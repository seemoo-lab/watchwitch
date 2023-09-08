package net.rec0de.android.watchwitch.decoders.protobuf

import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.doubleFromLongBytes
import net.rec0de.android.watchwitch.fromBytesLittle
import net.rec0de.android.watchwitch.hex
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
            val unusual = string.filter { !it.toString().matches(Regex("[a-zA-Z0-9\\-\\./,;\\(\\)_ ]")) }

            val utf8Errors = string.codePoints().anyMatch{ it == 0xFFFD }
            val weirdASCII = unusual.chars().anyMatch { it < 32 }

            // is 90% of characters are 'common', we assume this is a correctly decoded string
            if(unusual.length.toDouble() / string.length < 0.1 && !utf8Errors && !weirdASCII)
                return ProtoString(string)
        } catch(_: Exception) {}

        // try decoding as nested protobuf
        try {
            val nested = ProtobufParser().parse(data.value)
            // we sometimes get spurious UUIDs that are valid protobufs and get misclassified
            // checking that field ids are in sane ranges should help avoid that
            if(nested.value.keys.all { it in 1..99 })
                return nested
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

interface ProtoValue {
    val wireType: Int

    fun renderWithFieldId(fieldId: Int): ByteArray {
        val tag = (fieldId.toLong() shl 3) or (wireType and 0x03).toLong()
        return renderAsVarInt(tag) + render()
    }

    fun render(): ByteArray

    fun renderAsVarInt(v: Long): ByteArray {
        var bytes = byteArrayOf()
        var remaining = v

        while(remaining > 0x7F) {
            bytes += ((remaining and 0x7F) or 0x80).toByte() // Take lowest 7 bit and encode with continuation flag
            remaining = remaining shr 7
        }

        bytes += (remaining and 0x7F).toByte()
        return bytes
    }
}

data class ProtoBuf(val value: Map<Int, List<ProtoValue>>) : ProtoValue {
    override val wireType = 2 // LEN
    override fun toString() = "Protobuf($value)"

    fun readOptionalSinglet(field: Int) : ProtoValue? {
        val f = value[field]
        return if(f == null) null else f[0]
    }

    fun readAssertedSinglet(field: Int) : ProtoValue {
        return value[field]!![0]
    }

    fun readBool(field: Int) : Boolean {
        return (readAssertedSinglet(field) as ProtoVarInt).value.toInt() > 0
    }

    fun readOptBool(field: Int) : Boolean? {
        val intValue = readOptShortVarInt(field)
        return if(intValue == null) null else intValue > 0
    }

    fun readOptString(field: Int) : String? {
        val v = readOptionalSinglet(field)
        // i think empty strings can be parsed ambiguously as empty protobufs
        if(v != null && v is ProtoBuf && v.value.isEmpty())
            return ""
        return (v as ProtoString?)?.value
    }

    fun readOptDate(field: Int) : Date? {
        return (readOptionalSinglet(field) as ProtoI64?)?.asDate()
    }

    fun readOptDouble(field: Int) : Double? {
        return (readOptionalSinglet(field) as ProtoI64?)?.asDouble()
    }

    fun readShortVarInt(field: Int) = readLongVarInt(field).toInt()

    fun readOptShortVarInt(field: Int) = (readOptionalSinglet(field) as ProtoVarInt?)?.value?.toInt()

    fun readLongVarInt(field: Int) = (readAssertedSinglet(field) as ProtoVarInt).value

    fun readOptLongVarInt(field: Int) = (readOptionalSinglet(field) as ProtoVarInt?)?.value


    fun readMulti(field: Int): List<ProtoValue> {
        return value[field] ?: emptyList()
    }

    fun renderStandalone(): ByteArray {
        val fieldRecords = value.map { field ->
            val fieldId = field.key
            val renderedRecords = field.value.map { it.renderWithFieldId(fieldId) }
            renderedRecords.fold(byteArrayOf()){ acc, new -> acc + new }
        }

        return fieldRecords.fold(byteArrayOf()){ acc, new -> acc + new }
    }

    override fun render(): ByteArray {
        val bytes = renderStandalone()
        return renderAsVarInt(bytes.size.toLong()) + bytes // protobuf as a substructure is length delimited
    }
}

data class ProtoI32(val value: Int) : ProtoValue {
    override val wireType = 5
    override fun toString() = "I32($value)"

    override fun render(): ByteArray {
        val buf = ByteBuffer.allocate(4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(value)
        return buf.array()
    }
}

data class ProtoI64(val value: Long) : ProtoValue {
    override val wireType = 1
    override fun toString() = "I64($value)"

    override fun render(): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(value)
        return buf.array()
    }

    /**
     * Assume this I64 value represents a double containing an NSDate timestamp (seconds since Jan 01 2001)
     * and turn it into a Date object
     */
    fun asDate(): Date {
        val timestamp = asDouble()
        return Date((timestamp*1000).toLong() + 978307200000)
    }

    fun asDouble(): Double = value.doubleFromLongBytes()
}

data class ProtoVarInt(val value: Long) : ProtoValue {
    override val wireType = 0
    override fun toString() = "VarInt($value)"

    override fun render() = renderAsVarInt(value)
}

class ProtoLen(val value: ByteArray) : ProtoValue {
    override val wireType = 2 // LEN
    override fun toString() = "LEN(${value.hex()})"

    override fun render(): ByteArray {
        return renderAsVarInt(value.size.toLong()) + value
    }
}

data class ProtoString(val value: String) : ProtoValue {
    override val wireType = 2 // LEN
    override fun toString() = "String($value)"

    override fun render(): ByteArray {
        val bytes = value.encodeToByteArray()
        return renderAsVarInt(bytes.size.toLong()) + bytes
    }
}

class ProtoBPList(val value: ByteArray) : ProtoValue {
    override val wireType = 2 // LEN
    val parsed = BPListParser().parse(value)
    override fun toString() = "bplist($parsed)"

    override fun render(): ByteArray {
        return renderAsVarInt(value.size.toLong()) + value
    }
}