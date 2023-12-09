package net.rec0de.android.watchwitch.servicehandlers

import android.annotation.SuppressLint
import android.content.Context
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.ResourceTransferMessage
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPData
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPInt
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.bplist.NSData
import net.rec0de.android.watchwitch.decoders.bplist.NSDict
import net.rec0de.android.watchwitch.decoders.compression.GzipDecoder
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("StaticFieldLeak")
object GenericResourceTransferReceiver {
    private lateinit var context: Context
    private val reassemblyBuffer = mutableMapOf<Int,ByteArray>()
    private val filenames = mutableMapOf<Int,String>()
    private val filesizes = mutableMapOf<Int,Int>()

    fun initContext(ctx: Context) {
        context = ctx
    }

    fun handleMessage(msg: ResourceTransferMessage) {
        val stream = msg.streamID
        
        // first message of transfer contains metadata
        if(msg.payload[0].toInt() == 1) {
            val body = msg.payload.fromIndex(1)
            val content = if(GzipDecoder.bufferIsGzipCompressed(body)) GzipDecoder.inflate(body) else body
            val bp = BPListParser(nestedDecode = false).parse(content) as BPDict

            val totalBytes = (bp.values[BPAsciiString("ids-message-resource-transfer-total-bytes")]!! as BPInt).value.toInt()
            val file = (bp.values[BPAsciiString("ids-message-resource-transfer-url")]!! as BPAsciiString).value

            val firstChunk = if(totalBytes == 0) byteArrayOf() else (bp.values[BPAsciiString("ids-message-resource-transfer-data")]!! as BPData).value
            reassemblyBuffer[stream] = firstChunk
            filesizes[stream] = totalBytes
            filenames[stream] = file.split("/").last()
            logProgressForStream(stream)
        }
        // we have already received a first message for this transfer
        else if(filesizes[stream] != null) {
            val offset = ULong.fromBytesBig(msg.payload.sliceArray(0 until 8)).toInt()
            val body = msg.payload.fromIndex(8)
            val content = if(GzipDecoder.bufferIsGzipCompressed(body)) GzipDecoder.inflate(body) else body


            if(offset != reassemblyBuffer[stream]!!.size) {
                println("Resource transfer chunk length ${content.size} at offset $offset")
                println("Reassembly buffer mismatch")
            }
            else {
                reassemblyBuffer[stream] = reassemblyBuffer[stream]!! + content
                logProgressForStream(stream)
            }
        }
        else {
            Logger.logIDS("[transferreceviver] got resource transfer continuation message with no first message: $msg", 0)
        }

        if(reassemblyBuffer[stream] != null && reassemblyBuffer[stream]!!.size == filesizes[stream]!!) {
            saveTransferredFile(stream)
        }

    }

    private fun logProgressForStream(stream: Int) {
        val name = filenames[stream]!!
        val totalBytes = filesizes[stream]!!
        val receivedBytes = reassemblyBuffer[stream]!!.size
        val percent = if(totalBytes == 0)
                100.0
            else
                (((receivedBytes.toDouble() / totalBytes)*1000).roundToInt().toDouble())/10
        Logger.logIDS("[transferreceiver] receiving file $name on stream $stream, progress $percent%", 0)
    }

    private fun saveTransferredFile(stream: Int) {
        val filename = "transfer-${filenames[stream]!!}"
        val bytes = reassemblyBuffer[stream]!!

        context.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(bytes)
        }

        filenames.remove(stream)
        filesizes.remove(stream)
        reassemblyBuffer.remove(stream)
        Logger.logIDS("[transferreceiver] saved received file as $filename", 0)
    }
}