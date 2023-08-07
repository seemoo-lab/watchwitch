package net.rec0de.android.watchwitch.decoders.protobuf

import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.doubleFromLongBytes
import net.rec0de.android.watchwitch.fromBytesLittle
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.hexBytes
import java.util.Date

class ProtobufParser {

    private lateinit var bytes: ByteArray
    private var offset = 0

    @Synchronized
    fun parse(bytes: ByteArray): ProtoBuf {
        this.bytes = bytes
        offset = 0
        val result = mutableMapOf<Int, MutableList<ProtoValue>>()

        while(offset < bytes.size) {
            val info = readTag()
            val type = info.second
            val fieldNo = info.first

            val value = when(type) {
                ProtobufField.I32 -> readI32()
                ProtobufField.I64 -> readI64()
                ProtobufField.VARINT -> ProtoVarInt(readVarInt())
                ProtobufField.LEN -> guessVarLenValue(readLen())
            }

            if(!result.containsKey(fieldNo))
                result[fieldNo] = mutableListOf()
            result[fieldNo]!!.add(value)
        }

        return ProtoBuf(result)
    }

    private fun readTag(): Pair<Int, ProtobufField> {
        val tag = readVarInt().toInt()
        val field = tag shr 3
        val type = when(tag and 0x07) {
            0 -> ProtobufField.VARINT
            1 -> ProtobufField.I64
            2 -> ProtobufField.LEN
            //3 -> net.rec0de.android.watchwitch.decoders.protobuf.ProtobufField.SGROUP
            //4 -> net.rec0de.android.watchwitch.decoders.protobuf.ProtobufField.EGROUP
            5 -> ProtobufField.I32
            else -> throw Exception("Unknown protobuf field tag: $tag")
        }

        return Pair(field, type)
    }

    private fun readI32(): ProtoI32 {
        val b = bytes.sliceArray(offset until offset+4)
        offset += 4
        return ProtoI32(UInt.fromBytesLittle(b).toInt())
    }

    private fun readI64(): ProtoI64 {
        val b = bytes.sliceArray(offset until offset+8)
        offset += 8
        return ProtoI64(ULong.fromBytesLittle(b).toLong())
    }

    private fun readLen(): ProtoLen {
        val length = readVarInt()
        val data = bytes.sliceArray(offset until offset+length.toInt())
        offset += length.toInt()
        return ProtoLen(data)
    }

    private fun guessVarLenValue(data: ProtoLen): ProtoValue {
        // detect nested bplists
        if(BPListParser.bufferIsBPList(data.value))
            return ProtoBPList(data.value)

        // try decoding as string
        try {
            val string = data.value.decodeToString()
            val unusual = string.filter { !it.toString().matches(Regex("[a-zA-Z0-9\\-\\./,;\\(\\) ]")) }

            // is 90% of characters are 'common', we assume this is a correctly decoded string
            if(unusual.length.toDouble() / string.length < 0.1)
                return ProtoString(string)
        } catch(_: Exception) {}

        // try decoding as nested protobuf
        try {
            return ProtobufParser().parse(data.value)
        } catch (_: Exception) { }

        return data
    }

    private fun readVarInt(): Long {
        var continueFlag = true
        val numberBytes = mutableListOf<Int>()

        while(continueFlag) {
            val byte = bytes[offset].toInt()
            continueFlag = (byte and 0x80) != 0
            val value = byte and 0x7f
            numberBytes.add(value)
            offset += 1
        }

        // little endian
        numberBytes.reverse()

        var assembled = 0L
        numberBytes.forEach {
            assembled = assembled shl 7
            assembled = assembled or it.toLong()
        }

        return assembled
    }
}

enum class ProtobufField {
    VARINT, I64, LEN, I32
}

interface ProtoValue

data class ProtoBuf(val value: Map<Int, List<ProtoValue>>) : ProtoValue {
    override fun toString() = "Protobuf($value)"

    fun readSingletFieldAsserted(field: Int) : ProtoValue {
        return value[field]!![0]
    }

    fun readBool(field: Int) : Boolean {
        return (readSingletFieldAsserted(field) as ProtoVarInt).value.toInt() > 0
    }

    fun readShortVarInt(field: Int) = readLongVarInt(field).toInt()

    fun readLongVarInt(field: Int) : Long {
        return (readSingletFieldAsserted(field) as ProtoVarInt).value
    }

    fun readMulti(field: Int): List<ProtoValue> {
        return value[field] ?: emptyList()
    }
}

data class ProtoI32(val value: Int) : ProtoValue {
    override fun toString() = "I32($value)"
}

data class ProtoI64(val value: Long) : ProtoValue {
    override fun toString() = "I64($value)"

    /**
     * Assume this I64 value represents a double containing an NSDate timestamp (seconds since Jan 01 2001)
     * and turn it into a Date object
     */
    fun asDate(): Date {
        val timestamp = value.doubleFromLongBytes()
        return Date((timestamp*1000).toLong() + 978307200000)
    }
}

data class ProtoVarInt(val value: Long) : ProtoValue {
    override fun toString() = "VarInt($value)"
}

class ProtoLen(val value: ByteArray) : ProtoValue {
    override fun toString() = "LEN(${value.hex()})"
}

data class ProtoString(val value: String) : ProtoValue {
    override fun toString() = "String($value)"
}

class ProtoBPList(val value: ByteArray) : ProtoValue {
    val parsed = BPListParser().parse(value)
    override fun toString() = "bplist($parsed)"
}