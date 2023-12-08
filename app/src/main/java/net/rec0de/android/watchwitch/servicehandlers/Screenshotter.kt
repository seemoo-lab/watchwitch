package net.rec0de.android.watchwitch.servicehandlers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.DataMessage
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPData
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.bplist.NSData
import net.rec0de.android.watchwitch.decoders.bplist.NSDict
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@SuppressLint("StaticFieldLeak")
object Screenshotter : AlloyService {
    private var index = 1
    override val handlesTopics = listOf("com.apple.private.alloy.screenshotter")

    private lateinit var context: Context

    fun initContext(ctx: Context) {
        context = ctx
    }

    override fun acceptsMessageType(msg: AlloyMessage) = msg is DataMessage

    override fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler) {
        msg as DataMessage

        if(!BPListParser.bufferIsBPList(msg.payload)){
            Logger.logIDS("[screenshotter] unexpected non-bplist message: $msg", 0)
            return
        }

        val bp = BPListParser().parse(msg.payload) as NSDict
        val imgData = bp.values[BPAsciiString("d")]!! as NSData
        saveScreenshot(imgData.value)
    }

    private fun saveScreenshot(content: ByteArray) {
        val format = SimpleDateFormat("yyyMMdd-HHmm", Locale.US)
        val timestamp = format.format(Date())
        val filename = "screenshot-$timestamp-$index.png"

        context.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(content)
        }

        index += 1

        Logger.logIDS("[screenshotter] saved screenshot as $filename", 0)
    }
}