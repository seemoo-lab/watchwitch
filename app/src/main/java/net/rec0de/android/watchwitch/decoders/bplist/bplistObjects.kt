package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.toAppleTimestamp
import net.rec0de.android.watchwitch.toBytesBig
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Date
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.roundToInt

abstract class BPListObject

abstract class CodableBPListObject : BPListObject() {
    abstract fun collectObjects(): Set<CodableBPListObject>
    abstract fun renderWithObjectMapping(mapping: Map<CodableBPListObject, Int>, refSize: Int): ByteArray

    fun renderAsTopLevelObject(): ByteArray {
        val header = "bplist00".encodeToByteArray()

        val objects = collectObjects()
        val refSize = ceil(log2(objects.size.toDouble()) / 8).toInt()
        val objMap = objects.mapIndexed { index, obj -> Pair(obj, index) }.toMap()

        val renderedObjects = objects.map { it.renderWithObjectMapping(objMap, refSize) }
        val objectTable = renderedObjects.foldRight(byteArrayOf()){ gathered, obj -> gathered + obj}

        val offsets = mutableListOf<Int>()
        var cumulativeOffset = header.size // offsets are from beginning of file, not end of header
        renderedObjects.indices.forEach { i ->
            offsets.add(cumulativeOffset)
            cumulativeOffset += renderedObjects[i].size
        }

        // last offset will be the largest, determine offset table offset size to fit it
        val offsetSize = ceil(log2(offsets.last().toDouble()) / 8).toInt()

        // render offsets to bytes and cut to appropriate size
        val offsetTable = offsets.map { it.toBytesBig().fromIndex(4-offsetSize) }.foldRight(byteArrayOf()) {
            gathered, offset -> gathered + offset
        }

        val trailer = ByteBuffer.allocate(32)
        trailer.putInt(0) // reserved
        trailer.put(0) // reserved
        trailer.put(0) // sort version? idk
        trailer.put(offsetSize.toByte())
        trailer.put(refSize.toByte())
        trailer.putLong(objects.size.toLong()) // num objects
        trailer.putLong(objMap[this]!!.toLong()) // top object offset
        trailer.putLong((header.size + objectTable.size).toLong()) // offset table start

        return header + objectTable + offsetTable
    }
}

// BPList objects that we can immediately render to bytes without referring to the object table etc
abstract class BPListImmediateObject : CodableBPListObject() {
    abstract fun renderToBytes(): ByteArray
    override fun collectObjects() = setOf(this)
    override fun renderWithObjectMapping(mapping: Map<CodableBPListObject, Int>, refSize: Int) = renderToBytes()
}

object BPNull : BPListImmediateObject() {
    override fun toString() = "null"
    override fun renderToBytes() = byteArrayOf(0x00)
}
object BPTrue : BPListImmediateObject() {
    override fun toString() = "true"
    override fun renderToBytes() = byteArrayOf(0x09)
}
object BPFalse : BPListImmediateObject() {
    override fun toString() = "false"
    override fun renderToBytes() = byteArrayOf(0x08)
}
object BPFill : BPListImmediateObject() {
    override fun toString() = "BPFill"
    override fun renderToBytes() = byteArrayOf(0x0f)
}
data class BPInt(val value: BigInteger): BPListImmediateObject() {
    override fun toString() = value.toString()

    override fun renderToBytes(): ByteArray {
        val bytes = value.toByteArray()
        val lengthBits = (ceil(log2(bytes.size.toDouble())).toInt() and 0x0f)
        val markerByte = (0x10 or lengthBits).toByte()

        val encodedLen = 1 shl lengthBits
        val padding = ByteArray(encodedLen - bytes.size){ 0 }

        return byteArrayOf(markerByte) + padding + bytes
    }
}
data class BPReal(val value: Double): BPListImmediateObject() {
    override fun renderToBytes(): ByteArray {
        val markerByte = 0x23 // 0x20 for real, 0x03 for 2^3 = 8 bytes
        val buf = ByteBuffer.allocate(9)
        buf.put(markerByte.toByte())
        buf.putDouble(value)
        return buf.array()
    }
}
data class BPDate(val timestamp: Long) : BPListImmediateObject() {
    override fun toString() = "BPDate($timestamp)"

    override fun renderToBytes(): ByteArray {
        val markerByte = 0x33
        val buf = ByteBuffer.allocate(9)
        buf.put(markerByte.toByte())
        buf.putLong(timestamp)
        return buf.array()
    }
}
class BPData(val value: ByteArray) : BPListImmediateObject() {
    override fun toString() = "BPData(${value.hex()})"

    override fun renderToBytes(): ByteArray {
        val lengthBits = if(value.size > 14) 0x0F else value.size
        val markerByte = (0x40 or lengthBits).toByte()

        return if(value.size > 14) {
            byteArrayOf(markerByte) + BPInt(value.size.toBigInteger()).renderToBytes() + value
        } else {
            byteArrayOf(markerByte) + value
        }
    }
}
data class BPAsciiString(val value: String) : BPListImmediateObject() {
    override fun toString() = "\"$value\""

    override fun renderToBytes(): ByteArray {
        val charCount = value.length
        val bytes = Charsets.UTF_16BE.encode(value).array()

        val lengthBits = if(charCount > 14) 0x0F else charCount
        val markerByte = (0x50 or lengthBits).toByte()

        return if(charCount > 14) {
            byteArrayOf(markerByte) + BPInt(charCount.toBigInteger()).renderToBytes() + bytes
        } else {
            byteArrayOf(markerByte) + bytes
        }
    }
}
data class BPUnicodeString(val value: String) : BPListImmediateObject() {
    override fun toString() = "\"$value\""

    override fun renderToBytes(): ByteArray {
        val charCount = value.length
        val bytes = Charsets.UTF_16BE.encode(value).array()

        val lengthBits = if(charCount > 14) 0x0F else charCount
        val markerByte = (0x60 or lengthBits).toByte()

        return if(charCount > 14) {
            byteArrayOf(markerByte) + BPInt(charCount.toBigInteger()).renderToBytes() + bytes
        } else {
            byteArrayOf(markerByte) + bytes
        }
    }
}
class BPUid(val value: ByteArray) : BPListImmediateObject() {
    override fun toString() = "BPUid(${value.hex()})"

    override fun renderToBytes(): ByteArray {
        val lengthBits = (value.size - 1) and 0x0f
        val markerByte = (0x80 or lengthBits).toByte()
        return byteArrayOf(markerByte) + value
    }
}
data class BPArray(val entries: Int, val values: List<CodableBPListObject>) : CodableBPListObject() {
    override fun collectObjects() = values.flatMap { it.collectObjects() }.toSet() + this

    override fun renderWithObjectMapping(mapping: Map<CodableBPListObject, Int>, refSize: Int): ByteArray {
        val lengthBits = if(values.size > 14) 0x0F else values.size
        val markerByte = (0xa0 or lengthBits).toByte()
        var result = byteArrayOf(markerByte)

        if(values.size > 14) {
            result += BPInt(values.size.toBigInteger()).renderToBytes()
        }

        // tricky, let me explain: we map integer object references to 4-byte byte arrays
        // since we chose the reference size to accomodate all the references and the byte arrays
        // are in big endian order, the first 4-refSize bytes will always be zero
        // and stripping them gets us to the desired reference byte size
        val references = values.map { mapping[it]!!.toBytesBig().fromIndex(4-refSize) }

        // start with the marker and length, then append all the references
        return references.foldRight(result){ gathered, reference -> gathered + reference}
    }

    override fun toString() = "[${values.joinToString(", ")}]"
}
data class BPSet(val entries: Int, val values: List<CodableBPListObject>) : CodableBPListObject() {
    override fun collectObjects() = values.flatMap { it.collectObjects() }.toSet() + this

    // see BPArray
    override fun renderWithObjectMapping(mapping: Map<CodableBPListObject, Int>, refSize: Int): ByteArray {
        val lengthBits = if(values.size > 14) 0x0F else values.size
        val markerByte = (0xc0 or lengthBits).toByte()
        var result = byteArrayOf(markerByte)

        if(values.size > 14) {
            result += BPInt(values.size.toBigInteger()).renderToBytes()
        }

        val references = values.map { mapping[it]!!.toBytesBig().fromIndex(4-refSize) }

        // start with the marker and length, then append all the references
        return references.foldRight(result){ gathered, reference -> gathered + reference}
    }

    override fun toString() = "<${values.joinToString(", ")}>"
}
data class BPDict(val values: Map<CodableBPListObject, CodableBPListObject>) : CodableBPListObject() {
    override fun collectObjects(): Set<CodableBPListObject> {
        val keyObjs = values.keys.flatMap { it.collectObjects() }.toSet()
        val valueObjs = values.values.flatMap { it.collectObjects() }.toSet()
        return keyObjs + valueObjs + this
    }

    override fun renderWithObjectMapping(
        mapping: Map<CodableBPListObject, Int>,
        refSize: Int
    ): ByteArray {
        val lengthBits = if (values.size > 14) 0x0F else values.size
        val markerByte = (0xd0 or lengthBits).toByte()
        var result = byteArrayOf(markerByte)

        if (values.size > 14) {
            result += BPInt(values.size.toBigInteger()).renderToBytes()
        }

        val references = values.toList().map {
            Pair(
                mapping[it.first]!!.toBytesBig().fromIndex(4 - refSize),
                mapping[it.second]!!.toBytesBig().fromIndex(4 - refSize)
            )
        }

        val keyRefs = references.map { it.first }
            .foldRight(byteArrayOf()) { gathered, reference -> gathered + reference }
        val objRefs = references.map { it.second }
            .foldRight(byteArrayOf()) { gathered, reference -> gathered + reference }

        // encode marker, then all key refs, then all value refs
        return result + keyRefs + objRefs
    }

    override fun toString() = values.toString()
}

data class NSArray(val values: List<BPListObject>): BPListObject() {
    override fun toString() = values.toString()
}

data class NSDict(val values: Map<BPListObject, BPListObject>) : BPListObject() {
    override fun toString() = values.toString()
}

data class NSDate(val value: Date) : BPListObject() {
    override fun toString() = value.toString()
}