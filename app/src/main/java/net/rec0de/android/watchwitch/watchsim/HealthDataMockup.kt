package net.rec0de.android.watchwitch.watchsim

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.aoverc.Decryptor
import net.rec0de.android.watchwitch.decoders.aoverc.MPKeys
import net.rec0de.android.watchwitch.servicehandlers.health.EntityIdentifier
import net.rec0de.android.watchwitch.servicehandlers.health.HealthObject
import net.rec0de.android.watchwitch.servicehandlers.health.MetadataDictionary
import net.rec0de.android.watchwitch.servicehandlers.health.NanoSyncChange
import net.rec0de.android.watchwitch.servicehandlers.health.NanoSyncChangeSet
import net.rec0de.android.watchwitch.servicehandlers.health.NanoSyncMessage
import net.rec0de.android.watchwitch.servicehandlers.health.ObjectCollection
import net.rec0de.android.watchwitch.servicehandlers.health.Provenance
import net.rec0de.android.watchwitch.servicehandlers.health.QuantitySample
import net.rec0de.android.watchwitch.servicehandlers.health.Sample
import net.rec0de.android.watchwitch.servicehandlers.health.Source
import net.rec0de.android.watchwitch.servicehandlers.health.SyncStatusKey
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class HealthDataMockup(keys: MPKeys, private val controller: AlloyControlClient) {

    private val decryptor = Decryptor(keys)

    // these are random but should be fixed to avoid spamming the database with new sources / devices
    private val sourceUUID = UUID.fromString("c804af89-14d1-434b-817e-dea597aa6488")
    private val deviceUUID = UUID.fromString("61bd0419-e78f-435f-ba35-25662e5fc43d")
    private val persistentUUID = UUID.fromString("2c76c3d8-f6d5-4ad4-a1b2-059eb2404d44")
    private val healthPairingUUID = UUID.fromString("14adf58e-6246-4227-8907-d7d397bfd610")

    init {
        val runnable = Runnable {
            try {
                runBlocking { delay(1000*5) }
                while (true) {
                    sendHealthData()
                    runBlocking { delay(1000*45) }
                }
            } catch (e: Exception) { }
        }
        val thread = Thread(runnable)
        thread.start()
    }

    private fun sendHealthData() {
        val syncKey = SyncStatusKey(2, "main", 1, remote = false)
        val startAnchor = 22
        val endAnchor = startAnchor + 20

        val sessionUUID = UUID.randomUUID()
        val sessionStart = Date()

        val sourceCreationTime = Utils.dateFromAppleTimestamp(743003488.520043)

        val source = Source("Health", "com.watchwitch.Health", "", 3L, sourceUUID, sourceCreationTime, false, null)
        val provenance = Provenance("18H17", sourceUUID, deviceUUID, "7.3.3", "simWatch", "Europe/Berlin", 14, 8, 0)
        val metadata = MetadataDictionary(listOf())

        val heartrate = QuantitySample(
            Sample(0x05, HealthObject(UUID.randomUUID(), metadata, null, Date(), null), Date(), Date()),
            valueInCanonicalUnit = null,
            valueInOriginalUnit = Random.nextInt(600, 1200).toDouble()/10,
            originalUnitString = "count/min",
            frozen = false,
            count = null,
            final = null,
            min = null,
            max = null,
            mostRecent = null,
            mostRecentDate = null,
            quantitySeriesData = listOf(),
            mostRecentDuration = null
        )
        val steps = QuantitySample(
            Sample(0x07, HealthObject(UUID.randomUUID(), metadata, null, Date(), null), Date(), Date()),
            valueInCanonicalUnit = Random.nextInt(10, 200).toDouble(),
            valueInOriginalUnit = null,
            originalUnitString = null,
            frozen = false,
            count = null,
            final = null,
            min = null,
            max = null,
            mostRecent = null,
            mostRecentDate = null,
            quantitySeriesData = listOf(),
            mostRecentDuration = null
        )
        val objCollection = ObjectCollection(null, source, emptyList(), listOf(heartrate, steps), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), provenance)

        sendMessageWithChangeset(
            changeSetWithChange(
                NanoSyncChange(
                    2,
                    startAnchor,
                    endAnchor,
                    listOf(objCollection),
                    listOf(),
                    null,
                    0,
                    true,
                    EntityIdentifier(2, null)
                ), sessionUUID, sessionStart
            )
        )

        sendMessageWithChangeset(finishedChangeSet(sessionUUID, sessionStart))
    }

    private fun finishedChangeSet(sessionUUID: UUID, sessionStart: Date) = NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.FINISHED.code, emptyList(), null)

    private fun changeSetWithChange(change: NanoSyncChange, sessionUUID: UUID, sessionStart: Date): NanoSyncChangeSet {
        return NanoSyncChangeSet(sessionUUID, sessionStart, NanoSyncChangeSet.Status.CONTINUE.code, listOf(change), null)
    }

    private fun sendMessageWithChangeset(changeSet: NanoSyncChangeSet) {
        val msg = NanoSyncMessage(12, persistentUUID, healthPairingUUID, null, changeSet, null)
        Log.d("SimHealth", "Prepared outgoing msg: $msg")
        sendEncrypted(msg.renderRequest())
    }

    private fun sendEncrypted(bytes: ByteArray) {
        Log.d("SimHealth", "sending encrypted: ${bytes.hex()}")
        val encrypted = decryptor.encrypt(bytes).renderAsTopLevelObject()
        Log.d("SimHealth", "ciphertext: ${encrypted.hex()}")
        controller.sendData("com.apple.private.alloy.health.sync.classc", null, encrypted)
    }
}