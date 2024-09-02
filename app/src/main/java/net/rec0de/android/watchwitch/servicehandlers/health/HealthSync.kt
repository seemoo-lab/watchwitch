package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.Utils
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
import net.rec0de.android.watchwitch.decoders.bplist.BPData
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.servicehandlers.PreferencesSync
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import net.rec0de.android.watchwitch.toAppleTimestamp
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.random.Random

object HealthSync : AlloyService {
    override val handlesTopics = listOf("com.apple.private.alloy.health.sync.classc")

    private val keys = LongTermStorage.getMPKeysForClass("A")
    private val decryptor = if(keys != null) KeystoreBackedDecryptor(keys) else null

    private val syncStatus = DatabaseWrangler.loadSyncAnchors().toMutableMap()

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

            if(syncMsg.status != null && syncMsg.status.syncAnchors.isNotEmpty()) {
                Logger.logIDS("received health sync update, adjusting local anchors...", 1)
                syncMsg.status.syncAnchors.forEach {
                    val syncKey = SyncStatusKey(it.entityIdentifier!!.identifier, it.entityIdentifier.schema ?: "main", it.objectType ?: 0, false)
                    syncStatus[syncKey] = it.anchor!!.toInt()
                    DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, it.anchor.toInt(), remote = false)
                }
            }
            // we do not send sync states in reply to incoming sync states
            else {
                // build a reply with our updated sync state
                val syncAnchors = syncStatus.filter { it.key.remote }.map { it.key.toSyncAnchorWithValue(it.value.toLong()) }
                val nanoSyncStatus = NanoSyncStatus(syncAnchors, syncMsg.changeSet?.statusCode ?: 1) // status: echo incoming sync set
                val reply = NanoSyncMessage(12, syncMsg.persistentPairingUUID, syncMsg.healthPairingUUID, nanoSyncStatus, null, null)
                sendEncrypted(reply.renderReply(), msg.streamID, false, msg.messageUUID.toString(), handler)
            }
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
                val syncKey = SyncStatusKey(change.entityIdentifier.identifier, change.entityIdentifier.schema ?: "main", change.objectType ?: 0, remote = true)
                syncStatus[syncKey] = change.endAnchor
                DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, change.endAnchor, remote = true)
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

    private fun sendEncrypted(bytes: ByteArray, streamId: Int, isPush: Boolean, inResponseTo: String?, handler: AlloyHandler, corrupt: Boolean = false) {
        val dataMsg = encryptMessage(bytes, streamId, isPush, inResponseTo, corrupt)
        Logger.logIDS("Sending encrypted: $dataMsg", 2)
        handler.send(dataMsg)
    }

    private fun encryptMessage(bytes: ByteArray, streamId: Int, isPush: Boolean, inResponseTo: String?, corrupt: Boolean = false): DataMessage {
        val encrypted = if (corrupt) {
            val base = decryptor!!.encrypt(bytes)
            val ekd = (base.values[BPAsciiString("ekd")] as BPData).value
            val sed = (base.values[BPAsciiString("sed")] as BPData).value

            // this should fuck up the padding on sed
            val sedCorrputed = sed.sliceArray(0 until sed.size)
            sedCorrputed[sedCorrputed.lastIndex] = 0
            sedCorrputed[sedCorrputed.lastIndex - 1] = 0

            BPDict(mapOf(BPAsciiString("ekd") to BPData(ekd), BPAsciiString("sed") to BPData(sedCorrputed))).renderAsTopLevelObject()
        } else
            decryptor!!.encrypt(bytes).renderAsTopLevelObject()


        val sequence = AlloyController.nextSenderSequence.incrementAndGet()

        val flags = if (isPush) 0x21 else 0x00 // set wants app ack and mystery 0x20 flag when we actively push data

        return DataMessage(sequence, streamId, flags, inResponseTo, UUID.randomUUID(), null, null, encrypted)
    }

    fun enableECG() {
        if(!initialized) {
            Logger.log("Can't send ECG unlock: Health sync not initialized", 0)
            return
        }

        val sessionUUID = UUID.randomUUID()
        val sessionStart = Date()
        val syncKey = SyncStatusKey(17, "main", 17, remote = false)
        val startAnchor = syncStatus[syncKey] ?: 0
        val endAnchor = startAnchor + 4 // why 4? no idea.
        syncStatus[syncKey] = endAnchor
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
        sendMessageWithChangeset(changeSetWithChange(change, sessionUUID, sessionStart))
        sendMessageWithChangeset(finishedChangeSet(sessionUUID, sessionStart))

        PreferencesSync.enableECG()
        DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, endAnchor, remote = false)
    }

    fun enableCycleTracking() {
        if(!initialized) {
            Logger.log("Can't send Cycle Tracking unlock: Health sync not initialized", 0)
            return
        }

        val sessionUUID = UUID.randomUUID()
        val sessionStart = Date()

        var syncKey = SyncStatusKey(8, "main", 6, remote = false)
        var startAnchor = syncStatus[syncKey] ?: 4000
        var endAnchor = startAnchor + 6
        syncStatus[syncKey] = endAnchor

        val userCharacteristicsDict = CategoryDomainDictionary(null, 101, listOf(
            TimestampedKeyValuePair("user_entered_menstrual_cycle_length", Date(), null, 28.0, null, null),
            TimestampedKeyValuePair("user_entered_period_cycle_length", Date(), null, 5.0, null, null)
        ))
        sendMessageWithChangeset(changeSetWithChange(NanoSyncChange(6, startAnchor, endAnchor, listOf(userCharacteristicsDict), emptyList(), null, 0, true, EntityIdentifier(8, null)), sessionUUID, sessionStart))
        DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, endAnchor, remote = false)

        syncKey = SyncStatusKey(17, "main", 17, remote = false)
        startAnchor = syncStatus[syncKey] ?: 0
        endAnchor = startAnchor + 20
        syncStatus[syncKey] = endAnchor

        val userDefaultsDict = CategoryDomainDictionary("com.apple.private.health.menstrual-cycles", 105, listOf(
            TimestampedKeyValuePair("OnboardingCompleted", Date(), 2, null, null, null),
            TimestampedKeyValuePair("OnboardingFirstCompletedDate", Date(), null, Date().toAppleTimestamp(), null, null)
        ))
        sendMessageWithChangeset(changeSetWithChange(NanoSyncChange(17, startAnchor, endAnchor, listOf(userDefaultsDict), emptyList(), null, 0, true, EntityIdentifier(17, null)), sessionUUID, sessionStart))

        sendMessageWithChangeset(finishedChangeSet(sessionUUID, sessionStart))

        PreferencesSync.enableCycleTracking()
        DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, endAnchor, remote = false)
    }

    fun sendCTSymptom() {
        if(!initialized) {
            Logger.log("Can't send Cycle Tracking sample: Health sync not initialized", 0)
            return
        }

        val syncKey = SyncStatusKey(2, "main", 1, remote = false)
        val startAnchor = syncStatus[syncKey] ?: 8000 // ... start at a high number to take precedence over iPhone - annoying 🙄
        val endAnchor = startAnchor + 20
        syncStatus[syncKey] = endAnchor

        val sessionUUID = UUID.randomUUID()
        val sessionStart = Date()
        val sourceUUID = UUID.fromString("033e4a62-00c1-4149-b3bd-83fffdf71142")
        val deviceUUID = UUID.fromString("374e9a1e-f8f8-4b43-b8d4-a58197a598c5")

        val cal: Calendar = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val noonToday: Date = cal.time

        val sourceCreationTime = Utils.dateFromAppleTimestamp(743003488.520043)

        val source = Source("Health", "com.apple.Health", "", 3L, sourceUUID, sourceCreationTime, false, null)
        val provenance = Provenance("18H17", sourceUUID, deviceUUID, "14.8", "iPhone10,4", "Europe/Berlin", 14, 8, 0)
        val metadata = MetadataDictionary(listOf(
            MetadataKeyValuePair("_HKPrivateWasEnteredFromCycleTracking", null, null, null, 1.0, null, null),
            MetadataKeyValuePair("HKMenstrualCycleStart", null, null, null, 1.0, null, null),
            MetadataKeyValuePair("HKWasUserEntered", null, null, null, 1.0, null, null)
        ))

        val syncAnchors = listOf(
            NanoSyncAnchor(null, 0, EntityIdentifier(56, null)),
            NanoSyncAnchor(12, 3, EntityIdentifier(13, null)),
            NanoSyncAnchor(10, 7, EntityIdentifier(11, null))
        )

        val catSample = CategorySample(1, Sample(0x5f, HealthObject(UUID.randomUUID(), metadata, null, Date(), null), noonToday, noonToday))
        val objCollection = ObjectCollection(null, source, listOf(catSample), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), provenance)

        sendMessageWithChangeset(changeSetWithChange(NanoSyncChange(1, startAnchor, endAnchor, listOf(objCollection), syncAnchors, null, 0, true, EntityIdentifier(2, null)), sessionUUID, sessionStart))

        sendMessageWithChangeset(finishedChangeSet(sessionUUID, sessionStart))
        DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, endAnchor, remote = false)
    }

    fun randomizeSex() {
        if(!initialized) {
            Logger.log("Can't send user characteristics: Health sync not initialized", 0)
            return
        }

        val syncKey = SyncStatusKey(8, "main", 6, remote = false)
        val startAnchor = syncStatus[syncKey] ?: 4000 // if we don't have an anchor, trigger a resend to get state info from the watch
        val endAnchor = startAnchor + 1
        syncStatus[syncKey] = endAnchor

        val sessionUUID = UUID.randomUUID()
        val sessionStart = Date()

        val transedGender = Random.nextInt(1, 4)

        val dict = CategoryDomainDictionary(null, 101, listOf(
            TimestampedKeyValuePair("sex", Date(), transedGender, null, null, null)
        ))

        val change = NanoSyncChange(6, startAnchor, endAnchor, listOf(dict), emptyList(), null, 0, true, EntityIdentifier(8, null))
        sendMessageWithChangeset(changeSetWithChange(change, sessionUUID, sessionStart))

        sendMessageWithChangeset(finishedChangeSet(sessionUUID, sessionStart))
        DatabaseWrangler.setSyncAnchor(syncKey.schema, syncKey.objType, syncKey.identifier, endAnchor, remote = false)
    }

    private fun finishedChangeSet(sessionUUID: UUID, sessionStart: Date) = NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.FINISHED.code, emptyList(), null)

    private fun changeSetWithChange(change: NanoSyncChange, sessionUUID: UUID, sessionStart: Date): NanoSyncChangeSet {
        return NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.CONTINUE.code, listOf(change), null)
    }

    private fun sendMessageWithChangeset(changeSet: NanoSyncChangeSet) {
        val msg = NanoSyncMessage(12, persistentUUID, healthPairingUUID, null, changeSet, null)
        Logger.logIDS("Prepared outgoing msg: $msg", 2)
        sendEncrypted(msg.renderRequest(), cachedStream!!, true, null, cachedHandler!!)
    }

    fun resetSyncStatus() {
        syncStatus.clear()
        DatabaseWrangler.resetSyncStatus()
    }
}