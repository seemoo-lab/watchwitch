package net.rec0de.android.watchwitch.decoders.compression

import net.rec0de.android.watchwitch.bitmage.hex
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// adapted from https://gist.github.com/sgdan/eaada2f243a48196c5d4e49a277e3880
object GzipDecoder {

    // gzip is 10 byte header + 8 byte trailer, starting with 0x1f8b magic and 0x08 for deflate compression
    fun bufferIsGzipCompressed(bytes: ByteArray) = bytes.size > 18 && bytes.sliceArray(0 until 3).hex() == "1f8b08"

    fun compress(content: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).write(content)
        return bos.toByteArray()
    }

    fun inflate(content: ByteArray): ByteArray {
        val stream = GZIPInputStream(content.inputStream())
        val bytes = stream.readBytes()
        stream.close()
        return bytes
    }
}





