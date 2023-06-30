package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.ParseCompanion
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import java.nio.ByteBuffer
import java.util.Date
import java.util.UUID

abstract class UTunMessage(val sequence: Int) {
    companion object {
        // message types from jump table in IDSSocketPairMessage::messageWithCommand:data:
        val typeMap = mapOf(
            0x00 to "DataMessage",                // superclass: SocketPairMessage
            0x01 to "AckMessage",                 // superclass: SocketPairMessage
            0x02 to "KeepAliveMessage",           // superclass: SocketPairMessage
            0x03 to "ProtobufMessage",            // superclass: SocketPairMessage
            0x04 to "Handshake",                  // superclass: SocketPairMessage
            0x05 to "EncryptedMessage",           // superclass: SocketPairMessage
            0x06 to "DictionaryMessage",          // superclass: DataMessage
            0x07 to "AppAckMessage",              // superclass: SocketPairMessage
            0x08 to "SessionInvitationMessage",   // superclass: DataMessage
            0x09 to "SessionAcceptMessage",       // superclass: DataMessage
            0x0a to "SessionDeclineMessage",      // superclass: DataMessage
            0x0b to "SessionCancelMessage",       // superclass: DataMessage
            0x0c to "SessionMessage",             // superclass: DataMessage
            0x0d to "SessionEndMessage",          // superclass: DataMessage
            0x0e to "SMSTextMessage",             // superclass: DataMessage
            0x0f to "SMSTextDownloadMessage",     // superclass: DataMessage
            0x10 to "SMSOutgoing",                // superclass: DataMessage
            0x11 to "SMSDownloadOutgoing",        // superclass: DataMessage
            0x12 to "SMSDeliveryReceipt",         // superclass: DataMessage
            0x13 to "SMSReadReceipt",             // superclass: DataMessage
            0x14 to "SMSFailure",                 // superclass: DataMessage
            0x15 to "FragmentedMessage",          // superclass: SocketPairMessage
            0x16 to "ResourceTransferMessage",    // superclass: DataMessage
            0x17 to "OTREncryptedMessage",        // superclass: SocketPairMessage
            0x18 to "OTRMessage",                 // superclass: SocketPairMessage
            0x19 to "ProxyOutgoingNiceMessage",   // superclass: DataMessage
            0x1a to "ProxyIncomingNiceMessage",   // superclass: DataMessage
            0x1b to "TextMessage",                // superclass: DataMessage
            0x1c to "DeliveryReceipt",            // superclass: DataMessage
            0x1d to "ReadReceipt",                // superclass: DataMessage
            0x1e to "AttachmentMessage",          // superclass: DataMessage
            0x1f to "PlayedReceipt",              // superclass: DataMessage
            0x20 to "SavedReceipt",               // superclass: DataMessage
            0x21 to "ReflectedDeliveryReceipt",   // superclass: DataMessage
            0x22 to "GenericCommandMessage",      // superclass: DataMessage
            0x23 to "GenericGroupMessageCommand", // superclass: DataMessage
            0x24 to "LocationShareOfferCommand",  // superclass: DataMessage
            0x25 to "ExpiredAckMessage",          // superclass: AckMessage
            0x26 to "ErrorMessage",               // superclass: DataMessage
            0x27 to "ServiceMapMessage",          // superclass: SocketPairMessage
            // 0x28 missing?
            0x29 to "SessionReinitiateMessage",   // superclass: DataMessage
        )

        // converts UTun message type bytes to internally used 'commands' for actual application data messages
        // see  IDSUTunConnection::_socketToNiceCommand:
        val niceCommandMap = mapOf(
            0x00 to 0xf2,
            // missing: AckMessage
            // missing: KeepAliveMessage
            0x03 to 0xf3,
            // missing: Handshake
            // missing: EncryptedMessage
            0x06 to 0xe3,
            0x07 to 0xf4,
            0x08 to 0xe8,
            0x09 to 0xe9,
            0x0a to 0xea,
            0x0b to 0xeb,
            0x0c to 0xec,
            0x0d to 0xed,
            0x0e to 0x8c,
            0x0f to 0x8d,
            0x10 to 0x8f,
            0x11 to 0x90,
            0x12 to 0x92,
            0x13 to 0x93,
            0x14 to 0x95,
            // missing: FragmentedMessage
            0x16 to 0xe3,
            // missing: OTREncryptedMessage
            // missing: OTRMessage
            0x19 to 0xe4,
            0x1a to 0xe5,
            0x1b to 0x64,
            0x1c to 0x65,
            0x1d to 0x66,
            0x1e to 0x68,
            0x1f to 0x69,
            0x20 to 0x6a,
            0x21 to 0x6b,
            0x22 to 0xb4,
            0x23 to 0xbe,
            0x24 to 0xc3,
            // missing: ExpiredAckMessage
            // missing: ErrorMessage
            // missing: ServiceMapMessage
            0x28 to 0xc4, // ???
            0x29 to 0xee
        )

        fun parse(bytes: ByteArray): UTunMessage {
            return when(val type = bytes[0].toInt()) {
                0x00 -> DataMessage.parse(bytes)
                0x01 -> AckMessage.parse(bytes)
                0x02 -> KeepAliveMessage.parse(bytes)
                0x03 -> ProtobufMessage.parse(bytes)
                0x04 -> Handshake.parse(bytes)
                0x05 -> EncryptedMessage.parse(bytes)
                0x06 -> DictionaryMessage.parse(bytes)
                0x07 -> AppAckMessage.parse(bytes)
                0x08 -> SessionInvitationMessage.parse(bytes)
                0x09 -> SessionAcceptMessage.parse(bytes)
                0x0a -> SessionDeclineMessage.parse(bytes)
                0x0b -> SessionCancelMessage.parse(bytes)
                0x0c -> SessionMessage.parse(bytes)
                0x0d -> SessionEndMessage.parse(bytes)
                0x0e -> SMSTextMessage.parse(bytes)
                0x0f -> SMSTextDownloadMessage.parse(bytes)
                0x10 -> SMSOutgoing.parse(bytes)
                0x11 -> SMSDownloadOutgoing.parse(bytes)
                0x12 -> SMSDeliveryReceipt.parse(bytes)
                0x13 -> SMSReadReceipt.parse(bytes)
                0x14 -> SMSFailure.parse(bytes)
                0x15 -> FragmentedMessage.parse(bytes)
                0x16 -> ResourceTransferMessage.parse(bytes)
                0x17 -> OTREncryptedMessage.parse(bytes)
                0x18 -> OTRMessage.parse(bytes)
                0x27 -> ServiceMapMessage.parse(bytes)
                in 0x17..0x29 -> throw Exception("Unsupported UTunMsg ${typeMap[type]!!} in ${bytes.hex()}")
                else -> throw Exception("Unknown UTunMsg type 0x${type.toString(16)} in ${bytes.hex()}")
            }
        }
    }

    open val shouldAck = false
    abstract fun toBytes(): ByteArray

    protected fun baseHeaderBytes(length: Int, type: Int): ByteArray {
        val header = ByteBuffer.allocate(9)
        header.put(type.toByte())
        header.putInt(length)
        header.putInt(sequence)
        return header.array()
    }
}

open class UTunCommonMessage(sequence: Int, val streamID: Int, var flags: Int, val responseIdentifier: String?, val messageUUID: UUID, var topic: String?, val expiryDate: Long?) : UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray, expectedType: Int): Pair<UTunCommonMessage, ByteArray> {
            parseOffset = 0
            val sequence = checkHeader(bytes, expectedType)
            val streamID = readInt(bytes, 2)
            val flags = readInt(bytes, 1)
            val responseIdentifier = readLengthPrefixedString(bytes, 4)
            val messageUUID = UUID.fromString(readLengthPrefixedString(bytes, 4)!!)

            // compute some flags
            val hasExpiryDate = ((flags shr 3) and 0x01) == 1
            val hasTopic = ((flags shr 4) and 0x01) == 1

            val topic = if(hasTopic) readLengthPrefixedString(bytes, 4) else null

            val payloadLength = if(hasExpiryDate) bytes.size - parseOffset - 4 else bytes.size - parseOffset
            val payload = readBytes(bytes, payloadLength)

            // expiry is seconds since 00:00:00 UTC on 1 January 2001
            val expiryDate = if(hasExpiryDate) readInt(bytes, 4).toLong() else null
            assertParseComplete(bytes)

            return Pair(UTunCommonMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate), payload)
        }
    }

    override fun toBytes(): ByteArray {
        throw Exception("Attempting to call toBytes on UTunCommonMessage")
    }

    protected fun wrapPayloadWithCommonFields(payload: ByteArray, type: Int): ByteArray {
        val respIdLen = responseIdentifier?.length ?: 0
        val topicLen = if(hasTopic) topic!!.length + 4 else 0
        val expiryLen = if(hasExpiryDate) 4 else 0
        val length = 4 + 3 + 4 + respIdLen + 4 + messageUUID.toString().length + topicLen + payload.size + expiryLen

        val header = ByteBuffer.allocate(length + 5)

        // basic type length sequence header
        header.put(type.toByte())
        header.putInt(length)
        header.putInt(sequence)

        header.putShort(streamID.toShort())
        header.put(flags.toByte())

        if(responseIdentifier != null) {
            header.putInt(responseIdentifier.length)
            header.put(responseIdentifier.encodeToByteArray())
        }
        else
            header.putInt(0)

        header.putInt(messageUUID.toString().length)
        header.put(messageUUID.toString().encodeToByteArray())

        if(hasTopic) {
            header.putInt(topic!!.length)
            header.put(topic!!.encodeToByteArray())
        }

        header.put(payload)

        if(hasExpiryDate)
            header.putInt(expiryDate!!.toInt())

        return header.array()
    }

    val expectsPeerResponse: Boolean
        get() = (flags and 0x01) == 1
    val compressed: Boolean
        get() = ((flags shr 1) and 0x01) == 1
    val wantsAppAck: Boolean
        get() = ((flags shr 2) and 0x01) == 1
    val hasExpiryDate: Boolean
        get() = ((flags shr 3) and 0x01) == 1
    val hasTopic: Boolean
        get() = ((flags shr 4) and 0x01) == 1
    val normalizedExpiryDate: Date?
        // convert apple's reference date (Jan 1st 2001, 00:00 UTC) to standard millisecond unix timestamp
        get() = if(hasExpiryDate) Date((expiryDate!! + 978307200) * 1000 ) else null
}

abstract class AbstractSessionMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: String?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray) : DataMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val shouldAck = true // Session messages are always acknowledged
}
abstract class AbstractSMSMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: String?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray) : DataMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val shouldAck = true // SMS messages are always acknowledged
}
abstract class AbstractIMessageMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: String?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray) : DataMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val shouldAck = true // iMessage messages are always acknowledged
}


abstract class UTunParseCompanion : ParseCompanion() {
    protected fun checkHeader(bytes: ByteArray, expectedType: Int): Int {
        // common header: type, len, seq
        if(bytes[0].toInt() != expectedType)
            throw Exception("Expected UTunMsg type 0x${expectedType.toString(16)} for ${UTunMessage.typeMap[expectedType]!!} but got 0x${bytes[0].toString(16)}")
        parseOffset = 1

        val length = readInt(bytes, 4).toUInt()
        val sequence = readInt(bytes, 4)

        if(length != (bytes.size - 5).toUInt())
            throw Exception("Unexpected UTunMsg size ${bytes.size-5}, expected $length")

        return sequence
    }

    protected fun assertParseComplete(bytes: ByteArray) {
        if(parseOffset < bytes.size)
            throw Exception("Expected message end but got ${bytes.fromIndex(parseOffset).hex()} in message ${bytes.hex()}")
    }
}