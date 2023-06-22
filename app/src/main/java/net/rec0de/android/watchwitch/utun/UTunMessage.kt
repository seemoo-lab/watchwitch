package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.ParseCompanion
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex

abstract class UTunMessage(val sequence: Int) {
    companion object {
        // message types from jump table in IDSSocketPairMessage::messageWithCommand:data:
        private val typeMap = mapOf(
            0x00 to "DataMessage",
            0x01 to "AckMessage",
            0x02 to "KeepAliveMessage",
            0x03 to "ProtobufMessage",
            0x04 to "Handshake",
            0x05 to "EncryptedMessage",
            0x06 to "DictionaryMessage",
            0x07 to "AppAckMessage",
            0x08 to "SessionInvitationMessage",
            0x09 to "SessionAcceptMessage",
            0x0a to "SessionDeclineMessage",
            0x0b to "SessionCancelMessage",
            0x0c to "SessionMessage",
            0x0d to "SessionEndMessage",
            0x0e to "SMSTextMessage",
            0x0f to "SMSTextDownloadMessage",
            0x10 to "SMSOutgoing",
            0x11 to "SMSDownloadOutgoing",
            0x12 to "SMSDeliveryReceipt",
            0x13 to "SMSReadReceipt",
            0x14 to "SMSFailure",
            0x15 to "FragmentedMessage",
            0x16 to "ResourceTransferMessage",
            0x17 to "OTREncryptedMessage",
            0x18 to "OTRMessage",
            0x19 to "ProxyOutgoingNiceMessage",
            0x1a to "ProxyIncomingNiceMessage",
            0x1b to "TextMessage",
            0x1c to "DeliveryReceipt",
            0x1d to "ReadReceipt",
            0x1e to "AttachmentMessage",
            0x1f to "PlayedReceipt",
            0x20 to "SavedReceipt",
            0x21 to "ReflectedDeliveryReceipt",
            0x22 to "GenericCommandMessage",
            0x23 to "GenericGroupMessageCommand",
            0x24 to "LocationShareOfferCommand",
            0x25 to "ExpiredAckMessage",
            0x26 to "ErrorMessage",
            0x27 to "ServiceMapMessage",
            // 0x28 missing?
            0x29 to "SessionReinitiateMessage",
        )

        fun parse(bytes: ByteArray): UTunMessage {
            return when(val type = bytes[0].toInt()) {
                0x15 -> FragmentedMessage.parse(bytes)
                in 0x00..0x29 -> throw Exception("Unsupported UTunMsg ${typeMap[type]!!} in ${bytes.hex()}")
                else -> throw Exception("Unknown UTunMsg type 0x${type.toString(16)} in ${bytes.hex()}")
            }
        }
    }
}

class FragmentedMessage(sequence: Int, val fragmentIndex: Int, val fragmentCount: Int, val payload: ByteArray): UTunMessage(sequence) {
    companion object : ParseCompanion() {
        fun parse(bytes: ByteArray): FragmentedMessage {

            // common header: type, len, seq
            if(bytes[0].toInt() != 0x15)
                throw Exception("Expected UTunMsg type 0x15 for MessageFragment but got ${bytes[0]}")
            parseOffset = 1

            val length = readInt(bytes, 4).toUInt()
            val sequence = readInt(bytes, 4)

            if(length != (bytes.size - 5).toUInt())
                throw Exception("Unexpected UTunMsg size ${bytes.size-5}, expected $length")

            // fragment index and count, 4 bytes each
            val fragmentIndex = readInt(bytes, 4)
            val fragmentCount = readInt(bytes, 4)

            val payload = bytes.fromIndex(parseOffset)

            return FragmentedMessage(sequence, fragmentIndex, fragmentCount, payload)
        }
    }
}