package net.rec0de.android.watchwitch.bitmage

enum class ByteOrder {
    BIG, LITTLE
}

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.hex() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
fun ByteArray.fromIndex(i: Int) = sliceArray(i until size)
@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.decodeAsUTF16BE(): String {
    // oh my
    val shorts = this.toList().chunked(2).map { UInt.fromBytes(it.toByteArray(), ByteOrder.BIG).toUShort() }.toTypedArray()
    return shorts.toUShortArray().utf16BEToUtf8().toByteArray().decodeToString()
}


// Integers

fun ULong.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder): ULong {
    check(bytes.size <= 8) { "trying to parse oversized bytearray ${bytes.hex()} as ULong" }
    val orderedBytes = if(byteOrder == ByteOrder.BIG) bytes.reversed() else bytes.toList()
    return orderedBytes.mapIndexed { index, byte ->  byte.toUByte().toULong() shl (index * 8)}.sum()
}
fun Long.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder) = ULong.fromBytes(bytes, byteOrder).toLong()

fun UInt.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder): UInt {
    check(bytes.size <= 4) { "trying to parse oversized bytearray ${bytes.hex()} as UInt" }
    return ULong.fromBytes(bytes, byteOrder).toUInt()
}
fun Int.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder) = UInt.fromBytes(bytes, byteOrder).toInt()

fun Long.toBytes(byteOrder: ByteOrder): ByteArray {
    val bytesBE = byteArrayOf(
        (this shr 56).toByte(),
        (this shr 48).toByte(),
        (this shr 40).toByte(),
        (this shr 32).toByte(),
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        (this shr 0).toByte()
    )
    return if(byteOrder == ByteOrder.BIG) bytesBE else bytesBE.reversed().toByteArray()
}
fun ULong.toBytes(byteOrder: ByteOrder) = this.toLong().toBytes(byteOrder)

fun Int.toBytes(byteOrder: ByteOrder): ByteArray {
    val bytesBE = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this shr 0).toByte())
    return if(byteOrder == ByteOrder.BIG) bytesBE else bytesBE.reversed().toByteArray()
}
fun UInt.toBytes(byteOrder: ByteOrder) = this.toInt().toBytes(byteOrder)

// Floats

fun Float.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder): Float {
    check(bytes.size == 4) { "trying to read Float from incorrectly sized bytearray ${bytes.hex()}" }
    return Float.fromBits(Int.fromBytes(bytes, byteOrder))
}

fun Float.toBytes(byteOrder: ByteOrder) = this.toBits().toBytes(byteOrder)

fun Double.Companion.fromBytes(bytes: ByteArray, byteOrder: ByteOrder): Double {
    check(bytes.size == 8) { "trying to read Double from incorrectly sized bytearray ${bytes.hex()}" }
    return Double.fromBits(Long.fromBytes(bytes, byteOrder))
}

fun Double.toBytes(byteOrder: ByteOrder) = this.toBits().toBytes(byteOrder)


// ByteArray reading convenience functions

fun ByteArray.readInt(byteOrder: ByteOrder): Int {
    check(size >= 4) { "trying to read Int from undersized bytearray ${this.hex()}" }
    return Int.fromBytes(this.sliceArray(0 until 4), byteOrder)
}

fun ByteArray.readLong(byteOrder: ByteOrder): Long {
    check(size >= 8) { "trying to read Long from undersized bytearray ${this.hex()}" }
    return Long.fromBytes(this.sliceArray(0 until 8), byteOrder)
}

fun ByteArray.readFloat(byteOrder: ByteOrder): Float {
    check(size >= 4) { "trying to read Float from undersized bytearray ${this.hex()}" }
    return Float.fromBytes(this.sliceArray(0 until 4), byteOrder)
}

fun ByteArray.readDouble(byteOrder: ByteOrder): Double {
    check(size >= 8) { "trying to read Double from undersized bytearray ${this.hex()}" }
    return Double.fromBytes(this.sliceArray(0 until 8), byteOrder)
}

fun String.fromHex(): ByteArray {
    check(length % 2 == 0) { "trying to parse hex string of uneven length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}



// based on code by ephemient from https://slack-chats.kotlinlang.org/t/527242/i-have-a-bytearray-of-utf-16-encoded-bytes-read-from-a-cinte
@OptIn(ExperimentalUnsignedTypes::class)
fun UShortArray.utf16BEToUtf8(): UByteArray {
    var i = if (this.firstOrNull() == 0xFFEF.toUShort()) 1 else 0 // skip BOM
    val bytes = UByteArray((this.size - i) * 3)
    var j = 0
    while (i < this.size) {
        val codepoint = when (val unit = this[i++].toInt()) {
            in Char.MIN_HIGH_SURROGATE.code..Char.MAX_HIGH_SURROGATE.code -> {
                if (i !in this.indices) throw CharacterCodingException() // unpaired high surrogate
                val lowSurrogate = this[i++].toInt()
                val highSurrogate = unit
                if (lowSurrogate !in Char.MIN_LOW_SURROGATE.code..Char.MAX_LOW_SURROGATE.code) {
                    throw CharacterCodingException() // unpaired high surrogate
                }

                val code = ((highSurrogate - 0xd800) shl 10) or (lowSurrogate - 0xdc00) + 0x10000

                if (code !in 0x010000..0x10FFFF) {
                    throw CharacterCodingException() // non-canonical encoding
                }
                code
            }

            in Char.MIN_LOW_SURROGATE.code..Char.MAX_LOW_SURROGATE.code -> {
                throw CharacterCodingException() // unpaired low surrogate
            }

            else -> unit
        }
        when (codepoint) {
            in 0x00..0x7F -> bytes[j++] = codepoint.toUByte()
            in 0x80..0x07FF -> {
                bytes[j++] = 0xC0.or(codepoint and 0x07C0 shr 6).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x003F).toUByte()
            }

            in 0x0800..0xFFFF -> {
                bytes[j++] = 0xE0.or(codepoint and 0xF000 shr 12).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x0FC0 shr 6).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x003F).toUByte()
            }

            in 0x10000..0x10FFFF -> {
                bytes[j++] = 0xF0.or(codepoint and 0x3C0000 shr 18).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x03F000 shr 12).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x000FC0 shr 6).toUByte()
                bytes[j++] = 0x80.or(codepoint and 0x00003F).toUByte()
            }

            else -> throw IllegalStateException()
        }
    }
    return bytes.sliceArray(0 until j)
}