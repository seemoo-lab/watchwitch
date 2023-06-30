package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.hex
import java.math.BigInteger

abstract class BPListObject

object BPNull : BPListObject() {
    override fun toString() = "null"
}
object BPTrue : BPListObject() {
    override fun toString() = "true"
}
object BPFalse : BPListObject() {
    override fun toString() = "false"
}
object BPFill : BPListObject() {
    override fun toString() = "BPFill"
}
data class BPInt(val byteLen: Int, val value: BigInteger): BPListObject() {
    override fun toString() = value.toString()
}
data class BPReal(val byteLen: Int, val value: BigInteger): BPListObject()
data class BPDate(val timestamp: Long) : BPListObject() {
    override fun toString() = "BPDate($timestamp)"
}
class BPData(val value: ByteArray) : BPListObject() {
    override fun toString() = "BPData(${value.hex()})"
}
data class BPAsciiString(val value: String) : BPListObject() {
    override fun toString() = "\"$value\""
}
data class BPUnicodeString(val value: String) : BPListObject() {
    override fun toString() = "\"$value\""
}
class BPUid(val value: ByteArray) : BPListObject() {
    override fun toString() = "BPUid(${value.hex()})"
}
data class BPArray(val entries: Int, val values: List<BPListObject>) : BPListObject() {
    override fun toString() = "[${values.joinToString(", ")}]"
}
data class BPSet(val entries: Int, val values: List<BPListObject>) : BPListObject() {
    override fun toString() = "<${values.joinToString(", ")}>"
}
data class BPDict(val entries: Int, val values: Map<BPListObject, BPListObject>) : BPListObject() {
    override fun toString() = values.toString()
}

data class NSDict(val values: Map<BPListObject, BPListObject>) : BPListObject() {
    override fun toString() = values.toString()
}