package net.rec0de.android.watchwitch.utun

import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import java.nio.ByteBuffer
import java.util.UUID

abstract class UTunControlMessage {
    companion object {
        fun parse(bytes: ByteArray): UTunControlMessage {
            return when(val type = bytes[0].toInt()) {
                0x01 -> Hello.parse(bytes)
                0x02 -> SetupChannel.parse(bytes)
                0x03 -> CloseChannel.parse(bytes)
                0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c -> throw Exception("Unsupported UTunControlMsg type $type / ${msgTypeToString(type)}")
                else -> throw Exception("Unknown UTunControlMsg type $type in message ${bytes.hex()}")
            }
        }

        private val typeStrings = listOf(
            "Hello",
            "SetupChannel",
            "CloseChannel",
            "CompressionRequest",
            "CompressionResponse",
            "SetupEncryptedChannel",
            "FairplayHostSessionInfo",
            "FairplayDeviceInfo",
            "FairplayDeviceSessionInfo",
            "OTRNegotiationMessage",
            "EncryptControlChannel",
            "SuspendOTRNegotiationMessage"
        )

        private fun msgTypeToString(type: Int) = typeStrings[type-1]
    }
}

class Hello(
    val ccVersion: String,
    val productName: String,
    val productVersion: String,
    val build: String,
    val model: String,
    val protocolVersion: Int
    ): UTunControlMessage() {

    var capabilityFlags: Long = 0L

    var compatMinProtocolVersion: Int = protocolVersion
    var compatMaxProtocolVersion: Int = protocolVersion
    var serviceMinimumCompatibilityVersion: Int = 0

    var instanceID: UUID? = null
    var deviceID: UUID? = null

    companion object {
        private var parseOffset = 0

        fun parse(bytes: ByteArray): Hello {
            if(bytes[0].toInt() != 0x01)
                throw Exception("Expected UTunControlMsg type 0x01 for Hello but got ${bytes[0]}")

            parseOffset = 1

            val controlChannelVersion = readString(bytes)
            val productName = readString(bytes)
            val productVersion = readString(bytes)
            val productBuild = readString(bytes)
            val model = readString(bytes)
            val protocolVersion = readInt(bytes, 4)

            val msg = Hello(controlChannelVersion, productName, productVersion, productBuild, model, protocolVersion)

            // read optional data field
            while(parseOffset < bytes.size) {
                val type = readInt(bytes, 1)
                val length = readInt(bytes, 2)

                when(type) {
                    0 -> msg.compatMinProtocolVersion = readInt(bytes, length)
                    1 -> msg.compatMaxProtocolVersion = readInt(bytes, length)
                    2 -> {
                        msg.instanceID = Utils.uuidFromBytes(bytes.sliceArray(parseOffset until parseOffset+length))
                        parseOffset += length
                    }
                    3 -> {
                        msg.capabilityFlags = ULong.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset+length)).toLong()
                        parseOffset += length
                    }
                    4 -> {
                        msg.serviceMinimumCompatibilityVersion = readInt(bytes, length)
                    }
                    5 -> {
                        msg.deviceID = Utils.uuidFromBytes(bytes.sliceArray(parseOffset until parseOffset+length))
                        parseOffset += length
                    }
                    else -> throw Exception("Unknown data field type $type in ${bytes.fromIndex(parseOffset-3).hex()}")
                }
            }

            return msg
        }

        private fun readString(bytes: ByteArray): String {
            val strLen = UInt.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset+2)).toInt()
            val str = bytes.sliceArray(parseOffset+2 until parseOffset+2+strLen).toString(Charsets.UTF_8)
            parseOffset += strLen + 2
            return str
        }

        private fun readInt(bytes: ByteArray, size: Int): Int {
            val int = UInt.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset+size)).toInt()
            parseOffset += size
            return int
        }
    }

    fun toBytes(): ByteArray {
        val ccvBytes = ccVersion.encodeToByteArray()
        val productBytes = productName.encodeToByteArray()
        val pVersionBytes = productVersion.encodeToByteArray()
        val buildBytes = build.encodeToByteArray()
        val modelBytes = model.encodeToByteArray()

        val baseLength = 1 + 2 + ccvBytes.size + 2 + productBytes.size + 2 + pVersionBytes.size + 2 + buildBytes.size + 2 + modelBytes.size + 4
        val base = ByteBuffer.allocate(baseLength)
        base.put(0x01) // hello type byte
        base.putShort(ccvBytes.size.toShort())
        base.put(ccvBytes)
        base.putShort(productBytes.size.toShort())
        base.put(productBytes)
        base.putShort(pVersionBytes.size.toShort())
        base.put(pVersionBytes)
        base.putShort(buildBytes.size.toShort())
        base.put(buildBytes)
        base.putShort(modelBytes.size.toShort())
        base.put(modelBytes)
        base.putInt(protocolVersion)

        var optFieldLen = (1 + 2 + 4)*2 + (1 + 2 + 2) + (1 + 2 + 8) // always included: min/max compat, serviceCompat, capabilityFlags
        if(instanceID != null)
            optFieldLen += 1 + 2 + 16
        if(deviceID != null)
            optFieldLen += 1 + 2 + 16

        val opt = ByteBuffer.allocate(optFieldLen)
        opt.put(0x00)
        opt.putShort(4)
        opt.putInt(compatMinProtocolVersion)
        opt.put(0x01)
        opt.putShort(4)
        opt.putInt(compatMaxProtocolVersion)
        opt.put(0x04)
        opt.putShort(2)
        opt.putShort(serviceMinimumCompatibilityVersion.toShort())
        opt.put(0x03)
        opt.putShort(8)
        opt.putLong(capabilityFlags)

        if(instanceID != null) {
            opt.put(0x02)
            opt.putShort(16)
            opt.putLong(instanceID!!.mostSignificantBits)
            opt.putLong(instanceID!!.leastSignificantBits)
        }

        if(deviceID != null) {
            opt.put(0x05)
            opt.putShort(16)
            opt.putLong(deviceID!!.mostSignificantBits)
            opt.putLong(deviceID!!.leastSignificantBits)
        }

        return base.array() + opt.array()
    }

    override fun toString(): String {
        return "Hello($productName $productVersion $build $model, ccv $ccVersion, iUUID $instanceID dUUID $deviceID)"
    }
}

// the sender port is completely meaningless??
class SetupChannel(val proto: Int, val receiverPort: Int, val senderPort: Int, val senderUUID: UUID, val receiverUUID: UUID?, val account: String, val service: String, val name: String): UTunControlMessage() {
    companion object {
        private var parseOffset = 0

        fun parse(bytes: ByteArray): SetupChannel {
            if(bytes[0].toInt() != 0x02)
                throw Exception("Expected UTunControlMsg type 0x02 for SetupChannel but got ${bytes[0]}")
            parseOffset = 1

            val proto = bytes[parseOffset].toInt()
            parseOffset += 1

            val remotePort = readInt(bytes, 2)
            val localPort = readInt(bytes, 2)

            val remoteUUIDLen = readInt(bytes, 2)
            val localUUIDLen = readInt(bytes, 2)
            val accLen = readInt(bytes, 2)
            val serviceLen = readInt(bytes, 2)
            val nameLen = readInt(bytes, 2)

            val remoteUUID = UUID.fromString(readString(bytes, remoteUUIDLen))
            val localUUID = if(localUUIDLen > 0) UUID.fromString(readString(bytes, localUUIDLen)) else null

            val account = readString(bytes, accLen)
            val service = readString(bytes, serviceLen)
            val name = readString(bytes, nameLen)

            return SetupChannel(proto, localPort, remotePort, remoteUUID, localUUID, account, service, name)
        }

        private fun readString(bytes: ByteArray, size: Int): String {
            val str = bytes.sliceArray(parseOffset  until parseOffset +size).toString(Charsets.UTF_8)
            parseOffset += size
            return str
        }

        private fun readInt(bytes: ByteArray, size: Int): Int {
            val int = UInt.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset +size)).toInt()
            parseOffset += size
            return int
        }
    }

    fun toBytes(): ByteArray {
        val accBytes = account.encodeToByteArray()
        val serviceBytes = service.encodeToByteArray()
        val nameBytes = name.encodeToByteArray()

        val header = ByteBuffer.allocate(16)
        header.put(0x02) // SetupChannel type byte
        header.put(proto.toByte())
        header.putShort(receiverPort.toShort())
        header.putShort(senderPort.toShort())
        header.putShort(0x24) // sender UUID length
        header.putShort(if(receiverUUID != null) 0x24 else 0x00) // receiver UUID length
        header.putShort(accBytes.size.toShort())
        header.putShort(serviceBytes.size.toShort())
        header.putShort(nameBytes.size.toShort())

        val uuids = if(receiverUUID != null)
                senderUUID.toString().encodeToByteArray() + receiverUUID.toString().encodeToByteArray()
            else
                senderUUID.toString().encodeToByteArray()

        return header.array() + uuids + accBytes + serviceBytes + nameBytes
    }

    override fun toString(): String {
        return "SetupChannel(for $account $service $name ports $senderPort->$receiverPort proto $proto uuids $senderUUID $receiverUUID)"
    }
}

class CloseChannel(val senderUUID: UUID, val receiverUUID: UUID, val account: String, val service: String, val name: String): UTunControlMessage() {
    companion object {
        private var parseOffset = 0
        fun parse(bytes: ByteArray): CloseChannel {
            if(bytes[0].toInt() != 0x03)
                throw Exception("Expected UTunControlMsg type 0x03 for CloseChannel but got ${bytes[0]}")
            parseOffset = 1

            val remoteUUIDLen = readInt(bytes, 2)
            val localUUIDLen = readInt(bytes, 2)
            val accLen = readInt(bytes, 2)
            val serviceLen = readInt(bytes, 2)
            val nameLen = readInt(bytes, 2)


            val remoteUUID = UUID.fromString(readString(bytes, remoteUUIDLen))
            val localUUID = UUID.fromString(readString(bytes, localUUIDLen))
            val account = readString(bytes, accLen)
            val service = readString(bytes, serviceLen)
            val name = readString(bytes, nameLen)

            return CloseChannel(remoteUUID, localUUID, account, service, name)
        }

        private fun readString(bytes: ByteArray, size: Int): String {
            val str = bytes.sliceArray(parseOffset until parseOffset +size).toString(Charsets.UTF_8)
            parseOffset += size
            return str
        }

        private fun readInt(bytes: ByteArray, size: Int): Int {
            val int = UInt.fromBytesBig(bytes.sliceArray(parseOffset until parseOffset +size)).toInt()
            parseOffset += size
            return int
        }
    }

    override fun toString(): String {
        return "CloseChannel(for $account $service $name uuids $senderUUID $receiverUUID)"
    }
}