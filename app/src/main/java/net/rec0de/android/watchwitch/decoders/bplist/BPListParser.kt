package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import java.math.BigInteger
import java.nio.ByteBuffer

// based on https://medium.com/@karaiskc/understanding-apples-binary-property-list-format-281e6da00dbd
class BPListParser {
    private val objectMap = mutableMapOf<Int, CodableBPListObject>()
    private var objectRefSize = 0
    private var offsetTableOffsetSize = 0
    private var offsetTable = byteArrayOf()

    companion object {
        fun bufferIsBPList(buf: ByteArray): Boolean {
            return buf.size > 8 && buf.sliceArray(0 until 8).decodeToString() == "bplist00"
        }
    }

    /**
     * Parses bytes representing a bplist and returns the first contained object (usually a container type containing all the other objects)
     */
    @Synchronized
    fun parse(bytes: ByteArray): BPListObject {
        val rootObject = parseCodable(bytes)
        return if(KeyedArchiveDecoder.isKeyedArchive(rootObject))
                KeyedArchiveDecoder.decode(rootObject as BPDict)
            else
                rootObject
    }

    @Synchronized
    fun parseCodable(bytes: ByteArray): CodableBPListObject {
        objectMap.clear()

        val header = bytes.sliceArray(0 until 8)
        if (header.decodeToString() != "bplist00")
            throw Exception("Expected bplist header 'bplist00' in bytes ${bytes.hex()}")

        val trailer = bytes.fromIndex(bytes.size - 32)
        offsetTableOffsetSize = trailer[6].toInt()
        objectRefSize = trailer[7].toInt()
        val numObjects = ULong.fromBytesBig(trailer.sliceArray(8 until 16)).toInt()
        val topObjectOffset = ULong.fromBytesBig(trailer.sliceArray(16 until 24)).toInt()
        val offsetTableStart = ULong.fromBytesBig(trailer.sliceArray(24 until 32)).toInt()

        offsetTable =
            bytes.sliceArray(offsetTableStart until (offsetTableStart + numObjects * offsetTableOffsetSize))

        return readObjectFromOffsetTableEntry(bytes, topObjectOffset)
    }

    private fun readObjectFromOffsetTableEntry(bytes: ByteArray, index: Int): CodableBPListObject {
        val offset = UInt.fromBytesBig(offsetTable.sliceArray(index*offsetTableOffsetSize until (index+1)*offsetTableOffsetSize)).toInt()
        return readObjectFromOffset(bytes, offset)
    }

    private fun readObjectFromOffset(bytes: ByteArray, offset: Int): CodableBPListObject {
        // check cache
        if(objectMap.containsKey(offset))
            return objectMap[offset]!!

        // objects start with a one byte type descriptor
        val objectByte = bytes[offset].toUByte().toInt()
        // for some objects, the lower four bits carry length info
        val lengthBits = objectByte and 0x0f

        val parsed = when(objectByte) {
            0x00 -> BPNull
            0x08 -> BPFalse
            0x09 -> BPTrue
            0x0f -> BPFill
            // Int
            in 0x10 until 0x20 -> {
                // length bits encode int byte size as 2^n
                val byteLen = 1 shl lengthBits
                BPInt(BigInteger(bytes.sliceArray(offset+1 until offset+1+byteLen)))
            }
            // Real
            in 0x20 until 0x30 -> {
                // length bits encode real byte size as 2^n
                val byteLen = 1 shl lengthBits
                val value = when(byteLen) {
                    4 -> {
                        val buf = ByteBuffer.allocate(4)
                        buf.put(bytes.sliceArray(offset+1 until offset+1+4))
                        buf.getFloat(0).toDouble()
                    }
                    8 -> {
                        val buf = ByteBuffer.allocate(8)
                        buf.put(bytes.sliceArray(offset+1 until offset+1+8))
                        buf.getDouble(0)
                    }
                    else -> throw Exception("Got unexpected byte length for real: $byteLen in ${bytes.hex()}")
                }
                BPReal(value)
            }
            // Date, always 8 bytes long
            0x33 -> BPDate(ULong.fromBytesBig(bytes.sliceArray(offset+1 until offset+9)).toLong())
            // Data
            in 0x40 until 0x50 -> {
                // length bits encode byte count, if all ones additional length integer follows
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val byteLen = tmp.first
                val effectiveOffset = tmp.second

                val data = bytes.sliceArray(effectiveOffset until effectiveOffset+byteLen)

                // decode nested bplists
                return if(bufferIsBPList(data))
                    BPListParser().parseCodable(data)
                else
                    BPData(data)
            }
            // ASCII string
            in 0x50 until 0x60 -> {
                // length bits encode character count, if all ones additional length integer follows
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val charLen = tmp.first
                val effectiveOffset = tmp.second
                // ascii encodes at one char per byte, we can use default UTF8 decoding as ascii is cross compatible with everything
                val string = bytes.decodeToString(effectiveOffset, effectiveOffset+charLen)
                BPAsciiString(string)
            }
            // Unicode string
            in 0x60 until 0x70 -> {
                // length bits encode character count, if all ones additional length integer follows
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val charLen = tmp.first
                val effectiveOffset = tmp.second
                // this is UTF16, encodes at two bytes per char
                val stringBytes = bytes.sliceArray(effectiveOffset until effectiveOffset+charLen*2)
                val string = Charsets.UTF_16BE.decode(ByteBuffer.wrap(stringBytes)).toString()
                BPUnicodeString(string)
            }
            // UID, byte length is lengthBits+1
            in 0x80 until 0x90 -> BPUid(bytes.sliceArray(offset+1 until offset+2+lengthBits))
            // Array
            in 0xa0 until 0xb0 -> {
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val entries = tmp.first
                val effectiveOffset = tmp.second

                val values = (0 until entries).map {i ->
                    val objectIndex = UInt.fromBytesBig(bytes.sliceArray(effectiveOffset+i*objectRefSize until effectiveOffset+(i+1)*objectRefSize)).toInt()
                    readObjectFromOffsetTableEntry(bytes, objectIndex)
                }

                BPArray(entries, values)
            }
            // Set
            in 0xc0 until 0xd0 -> {
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val entries = tmp.first
                val effectiveOffset = tmp.second

                val values = (0 until entries).map {i ->
                    val objectIndex = UInt.fromBytesBig(bytes.sliceArray(effectiveOffset+i*objectRefSize until effectiveOffset+(i+1)*objectRefSize)).toInt()
                    readObjectFromOffsetTableEntry(bytes, objectIndex)
                }

                BPSet(entries, values)
            }
            in 0xd0 until 0xf0 -> {
                val tmp = getFillAwareLengthAndOffset(bytes, offset)
                val entries = tmp.first
                var effectiveOffset = tmp.second

                val keys = (0 until entries).map {i ->
                    val keyIndex = UInt.fromBytesBig(bytes.sliceArray(effectiveOffset+i*objectRefSize until effectiveOffset+(i+1)*objectRefSize)).toInt()
                    readObjectFromOffsetTableEntry(bytes, keyIndex)
                }

                effectiveOffset += entries * objectRefSize

                val values = (0 until entries).map {i ->
                    val valueIndex = UInt.fromBytesBig(bytes.sliceArray(effectiveOffset+i*objectRefSize until effectiveOffset+(i+1)*objectRefSize)).toInt()
                    readObjectFromOffsetTableEntry(bytes, valueIndex)

                }

                BPDict(keys.zip(values).toMap())
            }
            else -> throw Exception("Unknown object type byte 0b${objectByte.toString(2)}")
        }

        objectMap[offset] = parsed
        return parsed
    }


    private fun getFillAwareLengthAndOffset(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val lengthBits = bytes[offset].toInt() and 0x0f
        if(lengthBits < 0x0f)
            return Pair(lengthBits, offset+1)

        val sizeFieldSize = 1 shl (bytes[offset+1].toInt() and 0x0f) // size field is 2^n bytes
        val size = ULong.fromBytesBig(bytes.sliceArray(offset+2 until offset+2+sizeFieldSize)).toInt() // let's just hope they never get into long territory

        return Pair(size, offset+2+sizeFieldSize)
    }
}