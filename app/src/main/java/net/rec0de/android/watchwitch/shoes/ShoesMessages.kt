package net.rec0de.android.watchwitch.shoes

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.ParseCompanion
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import java.net.InetAddress
import java.nio.ByteBuffer

abstract class ShoesRequest(val port: Int) {
    companion object {
        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesRequest {
            val type = bytes[0].toInt()
            return when(type) {
                0x04, 0x01 -> ShoesHostnameRequest.parse(bytes)
                0x05, 0x02 -> ShoesIPv6Request.parse(bytes)
                0x06, 0x03 -> ShoesIPv4Request.parse(bytes)
                0x07, 0x08 -> ShoesBonjourRequest.parse(bytes)
                else -> throw Exception("Unsupported Shoes request type: $type")
            }
        }
    }

    var bundleId: String? = null
    var trafficClass = 0
    var flags: Int = 0
    var multipath: ByteArray? = null // i suspect multipath contains an alternate port, but somehow it's three bytes, not two?

    abstract fun render(): ByteArray

    protected fun renderTLVs(): ByteArray {
        var bytes = byteArrayOf()

        if(bundleId != null) {
            val bundleBytes = bundleId!!.encodeToByteArray()
            val header = ByteBuffer.allocate(3)
            header.put(0x03) // type
            header.putShort(bundleBytes.size.toShort())
            bytes += header.array() + bundleBytes
        }

        if(trafficClass != 0) {
            val header = ByteBuffer.allocate(7)
            header.put(0x01) // type
            header.putShort(4) // length
            header.putInt(trafficClass)
            bytes += header.array()
        }

        if(flags != 0) {
            val header = ByteBuffer.allocate(4)
            header.put(0x02) // type
            header.putShort(1) // length
            header.put(flags.toByte())
            bytes += header.array()
        }

        if(multipath != null) {
            val header = ByteBuffer.allocate(3)
            header.put(0x05) // type
            header.putShort(multipath!!.size.toShort())
            bytes += header.array() + multipath!!
        }

        return bytes
    }

    protected fun readTLVs(bytes: ByteArray) {
        // TLVs:
        // 0x01 traffic class, 4 bytes
        // 0x02 flags, 1 byte
        // 0x03 bundle id, varlen
        // 0x05 multipath, 3 bytes
        var remaining = bytes
        while(remaining.isNotEmpty()) {
            val type = remaining[0].toInt()
            val length = Int.fromBytes(remaining.sliceArray(1 until 3), ByteOrder.BIG)
            val value = remaining.sliceArray(3 until 3+length)
            remaining = remaining.fromIndex(3+length)

            when(type) {
                1 -> trafficClass = Int.fromBytes(value, ByteOrder.BIG)
                2 -> flags = Int.fromBytes(value, ByteOrder.BIG)
                3 -> bundleId = value.decodeToString()
                5 -> multipath = value
                else -> Logger.logShoes("SHOES req unknown TLV: type $type, payload ${value.hex()}", 1)
            }
        }
    }

    protected fun tlvsToString(): String {
        val parts = mutableListOf<String>()

        if(bundleId != null)
           parts.add(bundleId!!)

        if(trafficClass != 0)
            parts.add("trafficClass: $trafficClass")

        if(flags != 0)
            parts.add("flags: 0x${flags.toString(16)}")

        if(multipath != null)
            parts.add("multipath: ${multipath!!.hex()}")


        return parts.joinToString(", ")
    }
}

class ShoesHostnameRequest(dstPort: Int, val hostname: String) : ShoesRequest(dstPort) {
    companion object : ParseCompanion() {
        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesHostnameRequest {
            parseOffset = 0
            val type = readInt(bytes, 1)
            if(type != 0x01 && type != 0x04)
                throw Exception("Unexpected Shoes request type for hostname type: got $type expected 1 or 4")
            val port = readInt(bytes, 2)
            val host = readLengthPrefixedString(bytes, 1)!!

            val req = ShoesHostnameRequest(port, host)
            req.readTLVs(bytes.fromIndex(parseOffset))
            return req
        }
    }

    override fun render(): ByteArray {
        val tlvs = renderTLVs()
        val hostBytes = hostname.encodeToByteArray()
        val len = tlvs.size + hostBytes.size + 4 // 1 byte type, 2 bytes port, 1 byte host len

        if(hostBytes.size > 0xff)
            throw Exception("Hostname too long: ${hostBytes.size} > 0xff")

        val buf = ByteBuffer.allocate(6)
        buf.putShort(len.toShort())
        buf.put(0x01) // could also be 0x04, gotta figure out what that flag means
        buf.putShort(port.toShort())
        buf.put(hostBytes.size.toByte())

        return buf.array() + hostBytes + tlvs
    }

    override fun toString(): String {
        return "ShoesHostnameRequest($hostname:$port ${tlvsToString()})"
    }
}

class ShoesBonjourRequest(dstPort: Int, val service: String, val type: String, val domain: String) : ShoesRequest(dstPort) {
    companion object : ParseCompanion() {
        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesBonjourRequest {
            parseOffset = 0
            val type = readInt(bytes, 1)
            if(type != 0x07 && type != 0x08)
                throw Exception("Unexpected Shoes request type for bonjour type: got $type expected 7 or 8")
            val port = readInt(bytes, 2) // always zero?
            val bonjourDataLen = readInt(bytes, 1)
            var bonjourData = readBytes(bytes, bonjourDataLen)

            // we have three null-terminated strings in here, gotta piece those together
            val serviceNameEnd = bonjourData.indexOfFirst { it.toInt() == 0 }
            val service = bonjourData.sliceArray(0 until serviceNameEnd).decodeToString()
            bonjourData = bonjourData.fromIndex(serviceNameEnd+1)
            val typeEnd = bonjourData.indexOfFirst { it.toInt() == 0 }
            val typeString = bonjourData.sliceArray(0 until typeEnd).decodeToString()
            val domain = bonjourData.fromIndex(typeEnd+1).decodeToString()

            val req = ShoesBonjourRequest(port, service, typeString, domain)
            req.readTLVs(bytes.fromIndex(parseOffset))
            return req
        }
    }

    override fun render(): ByteArray {
        val tlvs = renderTLVs()
        val bonjourBytes = service.encodeToByteArray() + 0 + type.encodeToByteArray() + 0 + domain.encodeToByteArray() + 0
        val len = tlvs.size + bonjourBytes.size + 4 // 1 byte type, 2 bytes port, 1 byte bonjour len

        if(bonjourBytes.size > 0xff)
            throw Exception("Bonjour data too long: ${bonjourBytes.size} > 0xff")

        val buf = ByteBuffer.allocate(6)
        buf.putShort(len.toShort())
        buf.put(0x07) // could also be 0x08, gotta figure out what that flag means
        buf.putShort(port.toShort())
        buf.put(bonjourBytes.size.toByte())

        return buf.array() + bonjourBytes + tlvs
    }

    override fun toString(): String {
        return "ShoesBonjourRequest($service $type $domain ${tlvsToString()})"
    }
}

class ShoesIPv4Request(dstPort: Int, val ip: InetAddress) : ShoesRequest(dstPort) {
    companion object : ParseCompanion() {
        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesIPv4Request {
            parseOffset = 0
            val type = readInt(bytes, 1)
            if(type != 0x03 && type != 0x06)
                throw Exception("Unexpected Shoes request type for IPv4 type: got $type expected 3 or 6")

            val port = readInt(bytes, 2)

            val ipBytes = readBytes(bytes, 4)
            val ip = InetAddress.getByAddress(ipBytes)

            val req = ShoesIPv4Request(port, ip)
            req.readTLVs(bytes.fromIndex(parseOffset))
            return req
        }
    }

    override fun render(): ByteArray {
        val tlvs = renderTLVs()
        val ipBytes = ip.address
        val len = tlvs.size + ipBytes.size + 3 // 1 byte type, 2 bytes port

        val buf = ByteBuffer.allocate(5)
        buf.putShort(len.toShort())
        buf.put(0x03) // could also be 0x06, gotta figure out what that flag means
        buf.putShort(port.toShort())

        return buf.array() + ipBytes + tlvs
    }

    override fun toString(): String {
        return "ShoesIPv4Request($ip:$port ${tlvsToString()})"
    }
}

class ShoesIPv6Request(dstPort: Int, val ip: InetAddress) : ShoesRequest(dstPort) {
    companion object : ParseCompanion() {
        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesIPv6Request {
            parseOffset = 0
            val type = readInt(bytes, 1)
            if(type != 0x02 && type != 0x05)
                throw Exception("Unexpected Shoes request type for IPv6 type: got $type expected 2 or 5")

            val port = readInt(bytes, 2)

            val ipBytes = readBytes(bytes, 16)
            val ip = InetAddress.getByAddress(ipBytes)

            val req = ShoesIPv6Request(port, ip)
            req.readTLVs(bytes.fromIndex(parseOffset))
            return req
        }
    }

    override fun render(): ByteArray {
        val tlvs = renderTLVs()
        val ipBytes = ip.address
        val len = tlvs.size + ipBytes.size + 3 // 1 byte type, 2 bytes port

        val buf = ByteBuffer.allocate(5)
        buf.putShort(len.toShort())
        buf.put(0x02) // could also be 0x05, gotta figure out what that flag means
        buf.putShort(port.toShort())

        return buf.array() + ipBytes + tlvs
    }

    override fun toString(): String {
        return "ShoesIPv6Request($ip:$port ${tlvsToString()})"
    }
}

// based on _nw_shoes_read_reply in libnetwork.dylib
// and ___nw_shoes_read_reply_tlvs_block_invoke for TLV format and network flag meanings
// exact purpose of domain and code is unknown, non-zero code indicates error
class ShoesReply(val domain: Int, val code: Int, val expensive: Boolean, val cellular: Boolean, val wifi: Boolean, val constrained: Boolean, val denied: Boolean) {
    companion object : ParseCompanion() {

        // assuming 2-byte length prefix has already been consumed
        fun parse(bytes: ByteArray): ShoesReply {
            parseOffset = 0
            val domain = readInt(bytes, 1)
            val code = readInt(bytes, 1)
            val tlvType = readInt(bytes, 1)
            val tlvLen = readInt(bytes, 2)
            val tlvContent = readBytes(bytes, tlvLen)

            if(tlvType != 4)
                throw Exception("Unexpected TLV type $tlvType in SHOES reply, expected 0x04: ${bytes.hex()}")
            if(tlvLen != 1)
                throw Exception("Unexpected TLV len $tlvLen in SHOES reply, expected 1 byte")

            val netByte = tlvContent[0].toInt()
            val expensive = netByte and 0x80 != 0
            val cellular = netByte and 0x40 != 0
            val wifi = netByte and 0x20 != 0
            val constrained = netByte and 0x10 != 0
            val denied = netByte and 0x08 != 0

            return ShoesReply(domain, code, expensive, cellular, wifi, constrained, denied)
        }

        fun simple(expensive: Boolean, cellular: Boolean, wifi: Boolean): ShoesReply {
            return ShoesReply(0, 0, expensive, cellular, wifi, constrained = false, denied = false)
        }

        fun reject() = ShoesReply(0, 0, expensive = false, cellular = false, wifi = false, constrained = false, denied = true)
    }

    fun render(): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.putShort(6) // inner length, static because only exactly one TLV of known length
        buf.put(domain.toByte())
        buf.put(code.toByte())

        // connection info TLV
        val deniedFlag = (if(denied) 0x08 else 0x00).toUByte()
        val constrainedFlag = (if(constrained) 0x10 else 0x00).toUByte()
        val wifiFlag = (if(wifi) 0x20 else 0x00).toUByte()
        val cellFlag = (if(cellular) 0x40 else 0x00).toUByte()
        val expensiveFlag = (if(expensive) 0x80 else 0x00).toUByte()
        val networkByte = (deniedFlag or constrainedFlag or wifiFlag or cellFlag or expensiveFlag).toByte()

        buf.put(4) // type
        buf.putShort(1) // length
        buf.put(networkByte)

        return buf.array()
    }

    override fun toString(): String {
        val flags = mutableListOf<String>()
        if(expensive)
            flags.add("expensive")
        if(cellular)
            flags.add("cellular")
        if(wifi)
            flags.add("wifi")
        if(constrained)
            flags.add("constrained")
        if(denied)
            flags.add("denied")

        val words = flags.joinToString(" ")

        return "ShoesReply(domain $domain code $code $words)"
    }
}