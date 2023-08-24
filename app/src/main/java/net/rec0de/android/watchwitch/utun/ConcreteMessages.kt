package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.decoders.compression.GzipDecoder
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import java.nio.ByteBuffer
import java.util.UUID

open class DataMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, val payload: ByteArray): UTunCommonMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate) {
    open val name = "DataMessage"

    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): DataMessage {
            val commonData = parse(bytes, 0x00)
            val c = commonData.first
            // gzip compressed data can appear here with no correlation to the 'compressed' flag
            val rest = if(GzipDecoder.bufferIsGzipCompressed(commonData.second)) GzipDecoder.inflate(commonData.second) else commonData.second

            return DataMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }

    override fun toBytes(): ByteArray = wrapPayloadWithCommonFields(payload, 0x00)
    override fun toString() = "$name(stream $streamID, flags 0x${flags.toString(16)}, compressed? $compressed uuid $messageUUID, responseID $responseIdentifier, topic $topic, expires $normalizedExpiryDate, payload ${payload.hex()})"
}

open class AckMessage(sequence: Int) : UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): AckMessage {
            val sequence = checkHeader(bytes, 0x01)
            assertParseComplete(bytes)
            return AckMessage(sequence)
        }
    }

    override fun toBytes() = baseHeaderBytes(4, 0x01)
    override fun toString() = "AckMessage"
}

class KeepAliveMessage(sequence: Int) : UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): KeepAliveMessage {
            val sequence = checkHeader(bytes, 0x02)
            assertParseComplete(bytes)
            return KeepAliveMessage(sequence)
        }
    }

    override fun toBytes() = baseHeaderBytes(4, 0x02)
    override fun toString() = "KeepAliveMessage"
}

class ProtobufMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, val type: Int, val isResponse: Int, val payload: ByteArray): UTunCommonMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): ProtobufMessage {
            val commonData = parse(bytes, 0x03)
            val c = commonData.first
            val rest = commonData.second

            parseOffset = 0
            val type = readInt(rest, 2)
            val isResponse = readInt(rest, 2)
            val payloadLength = readInt(rest, 4)
            val payload = readBytes(rest, payloadLength)

            // gzip compressed data can appear here with no correlation to the 'compressed' flag
            val uncompressed = if(GzipDecoder.bufferIsGzipCompressed(payload)) GzipDecoder.inflate(payload) else payload

            return ProtobufMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, type, isResponse, uncompressed)
        }
    }

    override fun toBytes(): ByteArray {
        val protobufHeader = ByteBuffer.allocate(2 + 2 + 4)
        protobufHeader.putShort(type.toShort())
        protobufHeader.putShort(isResponse.toShort())
        protobufHeader.putInt(payload.size)
        return wrapPayloadWithCommonFields(protobufHeader.array() + payload, 0x03)
    }

    override fun toString() = "ProtobufMessage(stream $streamID, flags 0x${flags.toString(16)}, uuid $messageUUID, responseID $responseIdentifier, topic $topic, expires $normalizedExpiryDate, type $type, isResponse $isResponse payload ${payload.hex()})"
}

class Handshake(sequence: Int): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): Handshake {
            val sequence = checkHeader(bytes, 0x04)
            assertParseComplete(bytes)
            return Handshake(sequence)
        }
    }

    override fun toBytes() = baseHeaderBytes(4, 0x04)
    override fun toString() = "Handshake"
}


// not entirely sure about the structure here
class EncryptedMessage(sequence: Int, val payload: ByteArray): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): EncryptedMessage {
            val sequence = checkHeader(bytes, 0x05)
            val payload = bytes.fromIndex(parseOffset)
            return EncryptedMessage(sequence, payload)
        }
    }

    override fun toBytes() = baseHeaderBytes(4 + payload.size, 0x05) + payload
    override fun toString() = "EncryptedMessage(payload ${payload.hex()})"
}

class DictionaryMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): DataMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "DictionaryMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): DictionaryMessage {
            val commonData = parse(bytes, 0x06)
            val c = commonData.first
            val rest = commonData.second
            return DictionaryMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class AppAckMessage(sequence: Int, val streamID: Int, val responseIdentifier: String?, val topic: String?): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): AppAckMessage {
            val sequence = checkHeader(bytes, 0x07)
            val streamID = readInt(bytes, 2)
            val responseIdentifier = readLengthPrefixedString(bytes, 4)
            val topic = if(bytes.size - parseOffset > 3) readLengthPrefixedString(bytes, 4) else null
            assertParseComplete(bytes)
            return AppAckMessage(sequence, streamID, responseIdentifier, topic)
        }
    }

    override fun toBytes(): ByteArray {
        val responseIdLen = responseIdentifier?.length ?: 0
        val topicLen = if(topic == null) 0 else 4 + topic.length
        val length = 4 + 2 + 4 + responseIdLen + topicLen
        val header = baseHeaderBytes(length, 0x07)

        val packet = ByteBuffer.allocate(length + 5)
        packet.put(header)
        packet.putShort(streamID.toShort())
        packet.putInt(responseIdLen)
        if(responseIdentifier != null)
            packet.put(responseIdentifier.encodeToByteArray())
        if(topic != null) {
            packet.putInt(topic.length)
            packet.put(topic.encodeToByteArray())
        }

        return packet.array()
    }

    override fun toString() = "AppAckMessage(stream $streamID, responseID $responseIdentifier, topic $topic)"
}

class SessionInvitationMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionInvitationMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionInvitationMessage {
            val commonData = parse(bytes, 0x08)
            val c = commonData.first
            val rest = commonData.second
            return SessionInvitationMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SessionAcceptMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionAcceptMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionAcceptMessage {
            val commonData = parse(bytes, 0x09)
            val c = commonData.first
            val rest = commonData.second
            return SessionAcceptMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SessionDeclineMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionDeclineMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionDeclineMessage {
            val commonData = parse(bytes, 0x0a)
            val c = commonData.first
            val rest = commonData.second
            return SessionDeclineMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SessionCancelMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionCancelMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionCancelMessage {
            val commonData = parse(bytes, 0x0b)
            val c = commonData.first
            val rest = commonData.second
            return SessionCancelMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SessionMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionMessage {
            val commonData = parse(bytes, 0x0c)
            val c = commonData.first
            val rest = commonData.second
            return SessionMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SessionEndMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSessionMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SessionEndMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SessionEndMessage {
            val commonData = parse(bytes, 0x0d)
            val c = commonData.first
            val rest = commonData.second
            return SessionEndMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSTextMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSTextMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSTextMessage {
            val commonData = parse(bytes, 0x0e)
            val c = commonData.first
            val rest = commonData.second
            return SMSTextMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSTextDownloadMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSTextDownloadMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSTextDownloadMessage {
            val commonData = parse(bytes, 0x0f)
            val c = commonData.first
            val rest = commonData.second
            return SMSTextDownloadMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSOutgoing(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSOutgoing"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSOutgoing {
            val commonData = parse(bytes, 0x10)
            val c = commonData.first
            val rest = commonData.second
            return SMSOutgoing(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSDownloadOutgoing(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSDownloadOutgoing"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSDownloadOutgoing {
            val commonData = parse(bytes, 0x11)
            val c = commonData.first
            val rest = commonData.second
            return SMSDownloadOutgoing(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSDeliveryReceipt(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSDeliveryReceipt"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSDeliveryReceipt {
            val commonData = parse(bytes, 0x12)
            val c = commonData.first
            val rest = commonData.second
            return SMSDeliveryReceipt(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSReadReceipt(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSReadReceipt"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSReadReceipt {
            val commonData = parse(bytes, 0x13)
            val c = commonData.first
            val rest = commonData.second
            return SMSReadReceipt(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class SMSFailure(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): AbstractSMSMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "SMSFailure"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): SMSFailure {
            val commonData = parse(bytes, 0x14)
            val c = commonData.first
            val rest = commonData.second
            return SMSFailure(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

class FragmentedMessage(sequence: Int, val fragmentIndex: Int, val fragmentCount: Int, val payload: ByteArray): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): FragmentedMessage {

            val sequence = checkHeader(bytes, 0x15)

            // fragment index and count, 4 bytes each
            val fragmentIndex = readInt(bytes, 4)
            val fragmentCount = readInt(bytes, 4)

            val payload = bytes.fromIndex(parseOffset)

            return FragmentedMessage(sequence, fragmentIndex, fragmentCount, payload)
        }
    }

    override fun toBytes(): ByteArray {
        val header = ByteBuffer.allocate(9 + 4 + 4)
        header.put(baseHeaderBytes(4 + 4 + 4, 0x15))
        header.putInt(fragmentIndex)
        header.putInt(fragmentCount)
        return header.array() + payload
    }

    override fun toString() = "FragmentedMessage(${fragmentIndex+1}/$fragmentCount)"
}

class ResourceTransferMessage(sequence: Int, streamID: Int, flags: Int, responseIdentifier: UUID?, messageUUID: UUID, topic: String?, expiryDate: Long?, payload: ByteArray): DataMessage(sequence, streamID, flags, responseIdentifier, messageUUID, topic, expiryDate, payload) {
    override val name = "ResourceTransferMessage"
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): ResourceTransferMessage {
            val commonData = parse(bytes, 0x16)
            val c = commonData.first
            val rest = commonData.second
            return ResourceTransferMessage(c.sequence, c.streamID, c.flags, c.responseIdentifier, c.messageUUID, c.topic, c.expiryDate, rest)
        }
    }
}

// not sure about the format here, seems to get some special handling
class OTREncryptedMessage(sequence: Int, val version: Int, val encrypted: Boolean, val fileTransfer: Boolean, val streamID: Int, val priority: Int, val payload: ByteArray): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): OTREncryptedMessage {
            if(bytes[0].toInt() != 0x17)
                throw Exception("Expected UTunMsg type 0x17 for OTREncryptedMessage but got 0x${bytes[0].toString(16)}")
            parseOffset = 1

            val length = readInt(bytes, 4).toUInt()
            val versionByte = readInt(bytes, 1)
            val version = versionByte and 0x7f
            val encrypted = ((versionByte shr 7) and 0x01) == 1
            val fileTransfer = readInt(bytes, 1) != 0
            val streamID = readInt(bytes, 2)
            val priority = readInt(bytes, 2)
            val sequence = readInt(bytes, 4)

            val payload = bytes.fromIndex(parseOffset)

            return OTREncryptedMessage(sequence, version, encrypted, fileTransfer, streamID, priority, payload)
        }
    }

    override fun toBytes(): ByteArray {
        val header = ByteBuffer.allocate(15)
        header.put(0x17)
        header.putInt(payload.size + 10) // TODO: not sure how this size actually works
        val versionByte = (version and 0x7f) or (if(encrypted) 0x80 else 0x00)
        header.put(versionByte.toByte())
        header.put(if(fileTransfer) 0x01 else 0x00)
        header.putShort(streamID.toShort())
        header.putShort(priority.toShort())
        header.putInt(sequence)
        return header.array() + payload
    }

    override fun toString() = "OTREncryptedMessage(v$version, encrypted? $encrypted, transfer? $fileTransfer, streamID $streamID, priority $priority, payload ${payload.hex()})"
}

// not sure about the format here, seems to get some special handling
class OTRMessage(sequence: Int, val version: Int, val encrypted: Boolean, val shouldEncrypt: Boolean, val protectionClass: Int, val priority: Int, val streamID: Int, val payload: ByteArray): UTunMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): OTRMessage {
            if(bytes[0].toInt() != 0x18)
                throw Exception("Expected UTunMsg type 0x18 for OTRMessage but got 0x${bytes[0].toString(16)}")
            parseOffset = 1

            val length = readInt(bytes, 4).toUInt()
            val versionByte = readInt(bytes, 1)
            val version = versionByte and 0x0f
            val encrypted = ((versionByte shr 7) and 0x01) == 1
            val shouldEncrypt = ((versionByte shr 6) and 0x01) == 1
            val prioByte = readInt(bytes, 1)
            val protectionClass = prioByte and 0x03 // lower two bits
            val priority = (prioByte shr 6) * 100 // upper two bits, scaled
            val streamID = readInt(bytes, 2)
            val sequence = readInt(bytes, 4)

            val payload = bytes.fromIndex(parseOffset)

            return OTRMessage(sequence, version, encrypted, shouldEncrypt, protectionClass, priority, streamID, payload)
        }
    }

    override fun toBytes(): ByteArray {
        val versionByte = (version and 0x0f) or (if(shouldEncrypt) 0x40 else 0x00) or (if(encrypted) 0x80 else 0x00)
        val prioByte = (protectionClass and 0x03) or ((priority / 100) shl 6)

        val header = ByteBuffer.allocate(13)
        header.put(0x18)
        header.putInt(payload.size + 8)
        header.put(versionByte.toByte())
        header.put(prioByte.toByte())
        header.putShort(streamID.toShort())
        header.putInt(sequence)

        return header.array() + payload
    }

    override fun toString() = "OTRMessage(v$version, encrypted? $encrypted, shouldEncr? $shouldEncrypt, protection class $protectionClass, streamID $streamID, priority $priority, payload ${payload.hex()})"
}


class ServiceMapMessage(val streamID: Int, val serviceName: String, val reason: Int): UTunMessage(0) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): ServiceMapMessage {
            if(bytes[0].toInt() != 0x27)
                throw Exception("Expected UTunMsg type 0x27 for ServiceMapMessage but got 0x${bytes[0].toString(16)}")
            parseOffset = 1

            val len = readInt(bytes, 4)

            var reason: Int? = null
            var streamID: Int? = null
            var serviceName: String? = null

            while(parseOffset < bytes.size) {
                val type = readInt(bytes, 1)
                val length = readInt(bytes, 2)
                when(type) {
                    1 -> reason = readInt(bytes, length)
                    2 -> streamID = readInt(bytes, length)
                    3 -> serviceName = readString(bytes, length)
                }
            }

            if(streamID == null)
                throw Exception("ServiceMapMessage did not contain stream ID: ${bytes.hex()}")
            if(serviceName == null)
                throw Exception("ServiceMapMessage did not contain service name: ${bytes.hex()}")
            if(reason == null)
                throw Exception("ServiceMapMessage did not contain reason: ${bytes.hex()}")

            return ServiceMapMessage(streamID, serviceName, reason)
        }
    }

    override fun toBytes(): ByteArray {
        val serviceNameBytes = serviceName.encodeToByteArray()
        val length = 4 + 5 + 3 + serviceNameBytes.size // 4 bytes reason, 5 bytes stream id, 3+x bytes name
        val msg = ByteBuffer.allocate(5 + length) // 5 bytes utun type + len
        msg.put(0x27)
        msg.putInt(length)

        msg.put(0x01) // type: reason
        msg.putShort(1) // length: 1
        msg.put(reason.toByte())

        msg.put(0x02) // type: streamID
        msg.putShort(2) // length: 2
        msg.putShort(streamID.toShort())

        msg.put(0x03) // type: service name
        msg.putShort(serviceNameBytes.size.toShort()) // length
        msg.put(serviceNameBytes)

        return msg.array()
    }

    override fun toString() = "ServiceMapMessage(streamID $streamID maps to service $serviceName, reason $reason)"
}

open class ExpiredAckMessage(sequence: Int) : AckMessage(sequence) {
    companion object : UTunParseCompanion() {
        @Synchronized
        fun parse(bytes: ByteArray): ExpiredAckMessage {
            val sequence = checkHeader(bytes, 0x25)
            assertParseComplete(bytes)
            return ExpiredAckMessage(sequence)
        }
    }

    override fun toBytes() = baseHeaderBytes(4, 0x25)
    override fun toString() = "ExpiredAckMessage"
}