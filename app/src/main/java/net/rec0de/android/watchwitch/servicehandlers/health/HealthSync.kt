package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.alloy.AlloyController
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.AlloyService
import net.rec0de.android.watchwitch.alloy.DataMessage
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.aoverc.Decryptor
import net.rec0de.android.watchwitch.decoders.aoverc.KeystoreBackedDecryptor
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.servicehandlers.PreferencesSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import net.rec0de.android.watchwitch.toAppleTimestamp
import java.util.Date
import java.util.UUID

object HealthSync : AlloyService {
    override val handlesTopics = listOf("com.apple.private.alloy.health.sync.classc")

    private val keys = LongTermStorage.getMPKeysForClass("A")
    private val decryptor = if(keys != null) KeystoreBackedDecryptor(keys) else null

    private val syncStatus = DatabaseWrangler.loadSyncAnchors().toMutableMap()

    private val sessionUUID = UUID.randomUUID()
    private val sessionStart = Date()

    private var initialized = false
    private lateinit var persistentUUID: UUID
    private lateinit var healthPairingUUID: UUID

    private var cachedHandler: AlloyHandler? = null
    private var cachedStream: Int? = null

    override fun acceptsMessageType(msg: AlloyMessage) = msg is DataMessage

    override fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler) {
        msg as DataMessage

        if(!BPListParser.bufferIsBPList(msg.payload))
            throw Exception("HealthSync expected bplist payload but got ${msg.payload.hex()}")

        val parsed = BPListParser().parse(msg.payload)

        if(!Decryptor.isEncryptedMessage(parsed))
            throw Exception("HealthSync expected encrypted AoverC message but got $parsed")

        if(decryptor == null)
            Logger.logUTUN("HealthSync: got encrypted message but no keys to decrypt", 1)
        else {
            val plaintext = decryptor.decrypt(parsed as BPDict) ?: throw Exception("HealthSync decryption failed for $parsed")
            val syncMsg = parseSyncMsg(plaintext, msg.responseIdentifier != null) ?: return

            if(!initialized) {
                persistentUUID = syncMsg.persistentPairingUUID
                healthPairingUUID = syncMsg.healthPairingUUID
                cachedHandler = handler
                cachedStream = msg.streamID
                initialized = true
            }

            if(syncMsg.changeSet != null) {
                handleChangeSet(syncMsg.changeSet)
            }

            // build a reply with out updated sync state
            val syncAnchors = syncStatus.map { it.key.toSyncAnchorWithValue(it.value.toLong()) }
            val nanoSyncStatus = NanoSyncStatus(syncAnchors, syncMsg.changeSet?.statusCode ?: 1) // status: echo incoming sync set
            val reply = NanoSyncMessage(12, syncMsg.persistentPairingUUID, syncMsg.healthPairingUUID, nanoSyncStatus, null, null)
            sendEncrypted(reply.renderReply(), msg.streamID, msg.messageUUID.toString(), handler)
        }
    }

    // based on HDIDSMessageCenter::service:account:incomingData:fromID:context: in HealthDaemon binary
    private fun parseSyncMsg(msg: ByteArray, isReply: Boolean): NanoSyncMessage? {
        // first two bytes are message type (referred to message ID in apple sources)
        val type = Int.fromBytes(msg.sliceArray(0 until 2), ByteOrder.LITTLE)
        var offset = 2

        if(!isReply){
            // third byte is priority (0: default, 1: urgent, 2: sync)
            val priority = when(msg[2].toInt()) {
                0 -> "default"
                1 -> "urgent"
                2 -> "sync"
                else -> "unk(${msg[2].toInt()})"
            }
            offset = 3
            Logger.logUTUN("rcv HealthSync msg type $type priority $priority", 2)
        }
        else {
            Logger.logUTUN("rcv HealthSync msg type $type (reply)", 2)
        }

        val syncMsg = when(type) {
            2 -> parseNanoSyncMessage(msg.fromIndex(offset))
            else -> {
                Logger.logIDS("Unsupported HealthSync message type $type", 0)
                Logger.logIDS("bytes: ${msg.hex()}", 0)
                null
                //throw Exception("Unsupported HealthSync message type $type")
            }
        }

        Logger.logUTUN("rcv HealthSync $syncMsg", 1)
        return syncMsg
    }

    private fun parseNanoSyncMessage(bytes: ByteArray): NanoSyncMessage? {
        val pb = ProtobufParser().parse(bytes)
        try {
            return NanoSyncMessage.fromSafePB(pb)
        }
        catch(e: Exception) {
            // if we fail parsing something, print the failing protobuf for debugging and then still fail
            Logger.log("Failed while parsing: $pb", 0)
            Logger.log("bytes: ${bytes.hex()}", 0)
            Logger.log(e.toString(), 0)
            return null
        }
    }

    private fun handleChangeSet(changeSet: NanoSyncChangeSet) {
        Logger.logUTUN("Handling change set, status: ${changeSet.statusString}", 1)
        changeSet.changes.forEach { change ->
            var handled = true
            when(change.objectTypeString) {
                "CategorySamples", "QuantitySamples", "Workouts", "DeletedSamples", "BinarySamples", "ActivityCaches", "LocationSeriesSamples", "ECGSamples" -> change.objectData.forEach { handleObjectCollectionGeneric(it as ObjectCollection) }
                "ProtectedNanoUserDefaults" -> change.objectData.forEach { handleUserDefaults(it as CategoryDomainDictionary, true) }
                "NanoUserDefaults" -> change.objectData.forEach { handleUserDefaults(it as CategoryDomainDictionary, false) }
                else -> {
                    handled = false
                    Logger.log("Unhandled health sync change type: ${change.objectTypeString}", 0)
                }
            }

            // given that we usually start listening for health data in the middle of an existing pairing,
            // we just accept whatever we are sent and set out sync state to that anchor, ignoring previous
            // missing data
            if(handled) { // do not advance sync anchors for unhandled data to make watch send them again for debugging
                val syncKey = SyncStatusKey(change.entityIdentifier.identifier, change.entityIdentifier.schema ?: "main", change.objectType ?: 0)
                syncStatus[syncKey] = change.endAnchor
                DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, change.endAnchor)
            }
        }
    }

    private fun handleObjectCollectionGeneric(samples: ObjectCollection) {
        val provenance = samples.provenance
        val provenanceId = DatabaseWrangler.getOrInsertProvenance(provenance, samples.source)

        when{
            samples.categorySamples.isNotEmpty() -> samples.categorySamples.forEach { DatabaseWrangler.insertCategorySample(it.value, provenanceId, it.sample)  }
            samples.quantitySamples.isNotEmpty() -> samples.quantitySamples.forEach { DatabaseWrangler.insertQuantitySample(it, provenanceId) }
            samples.activityCaches.isNotEmpty() -> samples.activityCaches.forEach { DatabaseWrangler.insertActivityCache(it, provenanceId) }
            samples.workouts.isNotEmpty() -> samples.workouts.forEach { DatabaseWrangler.insertWorkout(it, provenanceId) }
            samples.binarySamples.isNotEmpty() -> samples.binarySamples.forEach { DatabaseWrangler.insertBinarySample(it, provenanceId) }
            samples.locationSeries.isNotEmpty() -> samples.locationSeries.forEach { DatabaseWrangler.insertLocationSeries(it, provenanceId) }
            samples.deletedSamples.isNotEmpty() -> samples.deletedSamples.forEach { DatabaseWrangler.markSampleDeleted(it.sample.healthObject.uuid) }
            samples.ecgSamples.isNotEmpty() -> samples.ecgSamples.forEach { DatabaseWrangler.insertEcgSample(it, provenanceId) }
        }
    }

    private fun handleUserDefaults(settings: CategoryDomainDictionary, secure: Boolean) {
        settings.keyValuePairs.forEach {
            if(secure)
                DatabaseWrangler.setKeyValueSecure(settings.domain, settings.category!!, it)
            else
                DatabaseWrangler.setKeyValue(settings.domain, settings.category!!, it)
        }
    }

    private fun sendEncrypted(bytes: ByteArray, streamId: Int, inResponseTo: String?, handler: AlloyHandler) {
        val encrypted = decryptor!!.encrypt(bytes).renderAsTopLevelObject()
        val sequence = AlloyController.nextSenderSequence.incrementAndGet()
        val dataMsg = DataMessage(sequence, streamId, 0, inResponseTo, UUID.randomUUID(), null, null, encrypted)
        Logger.logIDS("Sending encrypted: $dataMsg", 2)
        handler.send(dataMsg)
    }

    fun enableECG() {
        if(!initialized) {
            Logger.log("Can't send ECG unlock: Health sync not initialized", 0)
            return
        }

        val startAnchor = syncStatus[SyncStatusKey(17, "main", 17)] ?: 0
        val endAnchor = startAnchor + 4 // why 4? no idea.
        syncStatus[SyncStatusKey(17, "main", 17)] = endAnchor
        val timestamp = Date()
        val cc = "DE"
        val countryCodeBpDict = BPDict(mapOf(BPAsciiString("4") to BPAsciiString(cc)))

        val protectedUserDefaultEntries = listOf(
            TimestampedKeyValuePair("HKElectrocardiogramOnboardingHistory", timestamp, null, null, null, countryCodeBpDict.renderAsTopLevelObject()),
            TimestampedKeyValuePair("HKElectrocardiogramOnboardingCompleted", timestamp, 4, null, null, null),
            TimestampedKeyValuePair("HKElectrocardiogramOnboardingCountryCode", timestamp, null, null, cc, null),
            TimestampedKeyValuePair("HKElectrocardiogramFirstOnboardingCompleted", timestamp, null, timestamp.toAppleTimestamp(), null, null)
        )
        val objectData = CategoryDomainDictionary("com.apple.private.health.heart-rhythm", 105, protectedUserDefaultEntries)
        val change = NanoSyncChange(17, startAnchor, endAnchor, listOf(objectData), emptyList(), null,0, true, EntityIdentifier(17, null))
        sendMessageWithChangeset(changeSetWithChange(change))
        sendMessageWithChangeset(finishedChangeSet)

        PreferencesSync.enableECG()
    }

    fun enableCycleTracking() {
        if(!initialized) {
            Logger.log("Can't send Cycle Tracking unlock: Health sync not initialized", 0)
            return
        }

        var startAnchor = syncStatus[SyncStatusKey(8, "main", 6)] ?: 0
        var endAnchor = startAnchor + 6
        syncStatus[SyncStatusKey(8, "main", 6)] = endAnchor

        val userCharacteristicsDict = CategoryDomainDictionary(null, 101, listOf(
            TimestampedKeyValuePair("user_entered_menstrual_cycle_length", Date(), null, 28.0, null, null),
            TimestampedKeyValuePair("user_entered_period_cycle_length", Date(), null, 5.0, null, null)
        ))
        sendMessageWithChangeset(changeSetWithChange(NanoSyncChange(6, startAnchor, endAnchor, listOf(userCharacteristicsDict), emptyList(), null, 0, true, EntityIdentifier(8, null))))

        startAnchor = syncStatus[SyncStatusKey(17, "main", 17)] ?: 0
        endAnchor = startAnchor + 20
        syncStatus[SyncStatusKey(17, "main", 17)] = endAnchor

        val userDefaultsDict = CategoryDomainDictionary("com.apple.private.health.menstrual-cycles", 105, listOf(
            TimestampedKeyValuePair("OnboardingCompleted", Date(), 2, null, null, null),
            TimestampedKeyValuePair("OnboardingFirstCompletedDate", Date(), null, Date().toAppleTimestamp(), null, null)
        ))
        sendMessageWithChangeset(changeSetWithChange(NanoSyncChange(17, startAnchor, endAnchor, listOf(userDefaultsDict), emptyList(), null, 0, true, EntityIdentifier(17, null))))

        sendMessageWithChangeset(finishedChangeSet)

        PreferencesSync.enableCycleTracking()
    }

    private val finishedChangeSet: NanoSyncChangeSet
        get() = NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.FINISHED.code, emptyList(), null)

    private fun changeSetWithChange(change: NanoSyncChange): NanoSyncChangeSet {
        return NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.CONTINUE.code, listOf(change), null)
    }

    private fun sendMessageWithChangeset(changeSet: NanoSyncChangeSet) {
        val msg = NanoSyncMessage(12, persistentUUID, healthPairingUUID, null, changeSet, null)
        Logger.logIDS("Prepared outgoing msg: $msg", 2)
        sendEncrypted(msg.renderRequest(), cachedStream!!, null, cachedHandler!!)

    }

    fun resetSyncStatus() {
        syncStatus.clear()
        DatabaseWrangler.resetSyncStatus()
    }
}