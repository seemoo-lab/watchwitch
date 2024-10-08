package net.rec0de.android.watchwitch

import androidx.lifecycle.MutableLiveData
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

/*
 * A place to gather information about the current state of the connected watch, mostly sourced from NanoPreferencesSync
 */
object WatchState {

    @Volatile
    var networkPlumbingDone = AtomicBoolean(false)
    @Volatile
    var alloyConnected = AtomicBoolean(false)

    fun resetConnectionState() {
        alloyConnected.set(false)
        networkPlumbingDone.set(false)
    }

    val alarms: MutableLiveData<Map<String, Alarm>> by lazy {
        MutableLiveData<Map<String, Alarm>>(mapOf())
    }

    val openApps: MutableLiveData<List<String>> by lazy {
        MutableLiveData<List<String>>()
    }

    val ringerMuted: MutableLiveData<TriState> by lazy {
        MutableLiveData<TriState>(TriState.UNKNOWN)
    }

    fun setAlarm(id: String, alarm: Alarm) {
        val map = alarms.value!!.toMutableMap()
        map[id] = alarm
        alarms.postValue(map)
    }

    enum class TriState { TRUE, FALSE, UNKNOWN}

    data class Alarm(val hour: Int, val minute: Int, val enabled: Boolean, val title: String?)
}