package net.rec0de.android.watchwitch

import android.annotation.SuppressLint

object Logger {

    private const val GEN_SCREEN_LVL = 0
    private const val GEN_LOG_LVL = 0

    private const val IKE_SCREEN_LVL = 0
    private const val IKE_LOG_LVL = 0

    private const val IDS_SCREEN_LVL = 0
    private const val IDS_LOG_LVL = 2

    private const val UTUN_SCREEN_LVL = 1
    private const val UTUN_LOG_LVL = 2

    private const val SHOES_SCREEN_LVL = 1
    private const val SHOES_LOG_LVL = -1

    private const val CMD_SCREEN_LVL = -1
    private const val CMD_LOG_LVL = 1

    @SuppressLint("StaticFieldLeak")
    private var activity: MainActivity? = null

    fun setMainActivity(activity: MainActivity) {
        this.activity = activity
    }

    private fun logToScreen(msg: String) {
        if(activity == null)
            return
        activity!!.runOnUiThread { activity!!.logData(msg) }
    }

    fun log(msg: String, level: Int) {
        if(level <= GEN_SCREEN_LVL)
            logToScreen(msg)
        if(level <= GEN_LOG_LVL)
            println(msg)
    }

    fun logIDS(msg: String, level: Int) {
        if(level <= IDS_SCREEN_LVL)
            logToScreen(msg)
        if(level <= IDS_LOG_LVL)
            println(msg)
    }

    fun logUTUN(msg: String, level: Int) {
        if(level <= UTUN_SCREEN_LVL)
            logToScreen(msg)
        if(level <= UTUN_LOG_LVL)
            println(msg)
    }

    fun logShoes(msg: String, level: Int) {
        if(level <= SHOES_SCREEN_LVL)
            logToScreen(msg)
        if(level <= SHOES_LOG_LVL)
            println(msg)
    }

    fun logIKE(msg: String, level: Int) {
        if(level <= IKE_SCREEN_LVL)
            logToScreen(msg)
        if(level <= IKE_LOG_LVL)
            println(msg)
    }

    fun logCmd(msg: String, level: Int) {
        if(level <= CMD_SCREEN_LVL)
            logToScreen(msg)
        if(level <= CMD_LOG_LVL)
            println(msg)
    }
}