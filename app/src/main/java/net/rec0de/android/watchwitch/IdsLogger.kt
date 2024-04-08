package net.rec0de.android.watchwitch

import android.content.Context
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IdsLogger {

    private var output: BufferedOutputStream? = null

    fun init(context: Context) {
        val time = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val name = "session-$time.idslog"
        val file = context.openFileOutput(name, Context.MODE_PRIVATE)
        output = BufferedOutputStream(file)
    }

    fun logAlloy(send: Boolean, bytes: ByteArray) {
        val dir = if(send) "snd" else "rcv"
        output?.write("$dir utun ${bytes[0].toInt()} ${bytes.fromIndex(5).hex()}\n".toByteArray(Charsets.US_ASCII))
    }

    fun logControl(send: Boolean, bytes: ByteArray) {
        val dir = if(send) "snd" else "rcv"
        output?.write("$dir utunctrl ${bytes.hex()}\n".toByteArray(Charsets.US_ASCII))
    }

    fun flush() {
        output?.flush()
    }
}