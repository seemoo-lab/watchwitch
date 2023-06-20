package net.rec0de.android.watchwitch.ike

import net.rec0de.android.watchwitch.Utils
import java.nio.ByteBuffer
import java.security.SecureRandom

class IKEMessage(exchangeType: Int, private val seq: Int, private val spii: ByteArray, private val spir: ByteArray, private val isReply: Boolean = true) {

    private val exchangeType = exchangeType.toByte()
    private val payloads = mutableListOf<IKEPayload>()

    fun addPayload(payload: IKEPayload) = payloads.add(payload)
    fun addPayloads(payload: List<IKEPayload>) = payloads.addAll(payload)

    fun assemble(): ByteArray {
        val headers = payloads.map { it.typeByte } + listOf(0)
        val firstHeader = headers.first().toByte()
        val renderedPayloads = payloads.mapIndexed { idx, payload -> payload.render(headers[idx+1]) }

        val length = 28 + renderedPayloads.sumOf { it.size }

        val message = ByteBuffer.allocate(length)
        message.put(header(firstHeader, length))
        renderedPayloads.forEach { message.put(it) }

        return message.array()
    }

    private fun header(firstPayload: Byte, length: Int): ByteArray {
        val flags = if(isReply) 0x20 else 0x00
        val flagWord = byteArrayOf(firstPayload, 0x20, exchangeType, flags.toByte())

        val header = ByteBuffer.allocate(28)
        header.put(spii) // 8
        header.put(spir) // 8
        header.put(flagWord) // 4
        header.putInt(seq) // 4
        header.putInt(length) // 4
        return header.array() // 28 byte header
    }

    fun encrypt(key: ByteArray, salt: ByteArray) {
        val encrypted = encryptPayloads(key, salt)
        payloads.clear()
        payloads.add(encrypted)
    }

    private fun encryptPayloads(key: ByteArray, salt: ByteArray): EncryptedPayload {
        val headers = payloads.map { it.typeByte } + listOf(0)
        val renderedPayloads = payloads.mapIndexed { idx, payload -> payload.render(headers[idx+1]) }

        val iv = ByteArray(8)
        SecureRandom().nextBytes(iv)

        val clearPayloadLen = renderedPayloads.sumOf { it.size }
        val container = EncryptedPayload(headers.first().toByte(), clearPayloadLen, iv)

        val totalLength = 28 + container.size()
        val aad = header(container.typeByte.toByte(), totalLength) + container.header()
        val nonce = salt + iv

        val plaintext = ByteBuffer.allocate(clearPayloadLen+1)
        renderedPayloads.forEach { plaintext.put(it) }
        plaintext.put(0) // append a zero byte to indicate no padding (padlen field)

        val ciphertext = Utils.chachaPolyEncrypt(key, nonce, aad, plaintext.array())
        container.setCiphertext(ciphertext)

        //println("plaintext packet: ${plaintext.array().hex()}")

        return container
    }
}