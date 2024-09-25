package net.rec0de.android.watchwitch.watchsim

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.decoders.bplist.BPArray
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPFalse
import net.rec0de.android.watchwitch.decoders.bplist.BPInt
import net.rec0de.android.watchwitch.decoders.bplist.BPTrue
import net.rec0de.android.watchwitch.servicehandlers.PreferencesSync
import java.math.BigInteger
import kotlin.random.Random

class WatchStateMockup(private val alloyController: AlloyControlClient) {

    init {
        val runnable = Runnable {
            try {
                while (true) {
                    runBlocking { delay(1000*10) }
                    sendWatchState()
                    sendTimers()
                }
            } catch (e: Exception) {

            }
        }
        val thread = Thread(runnable)
        thread.start()
    }

    private fun sendWatchState() {

        val simulatedApps = listOf("com.watchwitch.weather.watchapp", "com.watchwitch.NanoMaps", "com.watchwitch.NanoMenstrualCycles", "com.watchwitch.HeartRate",  "com.watchwitch.SessionTrackerApp", "com.watchwitch.NanoAlarm")

        val openApps = simulatedApps.shuffled().subList(0, Random.nextInt(1, 5))

        val muted = if(Random.nextBoolean()) BPTrue else BPFalse

        val changes = listOf(
            PreferencesSync.UserDefaultsBackupMsgKey("MRUFullScreenApplications", BPArray(openApps.map{ BPAsciiString(it) })),
            PreferencesSync.UserDefaultsBackupMsgKey("SBRingerMuted", muted),
        )
        val defaultsMsg = PreferencesSync.UserDefaultsBackupMessage(null, "com.apple.Carousel", changes)
        alloyController.sendProtobuf(defaultsMsg.renderProtobuf(), "com.apple.private.alloy.preferencessync", type = 2, isResponse = false)
    }

    private fun sendTimers() {

        val changes = listOf(
            PreferencesSync.UserDefaultsBackupMsgKey("MTAlarms", BPDict(mapOf(BPAsciiString("MTAlarms") to BPArray(listOf(
                BPDict(mapOf(BPAsciiString("\$MTAlarm") to BPDict(mapOf(
                    BPAsciiString("MTAlarmHour") to BPInt(BigInteger.valueOf(12)),
                    BPAsciiString("MTAlarmMinute") to BPInt(BigInteger.valueOf(30)),
                    BPAsciiString("MTAlarmEnabled") to if(Random.nextBoolean()) BPTrue else BPFalse,
                    BPAsciiString("MTAlarmID") to BPAsciiString("abc"),
                    BPAsciiString("MTAlarmTitle") to BPAsciiString("Get Up"),
                )))),
                BPDict(mapOf(BPAsciiString("\$MTAlarm") to BPDict(mapOf(
                    BPAsciiString("MTAlarmHour") to BPInt(BigInteger.valueOf(3)),
                    BPAsciiString("MTAlarmMinute") to BPInt(BigInteger.valueOf(33)),
                    BPAsciiString("MTAlarmEnabled") to if(Random.nextBoolean()) BPTrue else BPFalse,
                    BPAsciiString("MTAlarmTitle") to BPAsciiString("Dance"),
                    BPAsciiString("MTAlarmID") to BPAsciiString("def")
                ))))
            )))))
        )
        val defaultsMsg = PreferencesSync.UserDefaultsBackupMessage(null, "com.apple.mobiletimerd", changes)
        alloyController.sendProtobuf(defaultsMsg.renderProtobuf(), "com.apple.private.alloy.preferencessync", type = 2, isResponse = false)
    }
}