package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.toAppleTimestamp
import java.math.BigInteger

interface KeyedArchiveCodable

class KeyedArchiveEncoder {

    private val classTranslationCache = mutableMapOf<String, BPUid>()
    private val objects = mutableListOf<CodableBPListObject>()

    fun encode(obj: KeyedArchiveCodable): CodableBPListObject {
        objects.clear()
        classTranslationCache.clear()
        objects.add(BPAsciiString("\$null"))

        val map = mutableMapOf<CodableBPListObject,CodableBPListObject>()
        map[BPAsciiString("\$version")] = BPInt(BigInteger.valueOf(100000))
        map[BPAsciiString("\$archiver")] = BPAsciiString("NSKeyedArchiver")

        val uid = when(obj) {
            is NSArray -> encodeArray(obj)
            is NSSet -> encodeSet(obj)
            is NSUUID -> encodeUUID(obj)
            is NSDate -> encodeDate(obj)
            is NSDict -> encodeDict(obj)
            is NSData -> encodeData(obj)
            else -> throw Exception("unreachable")
        }

        map[BPAsciiString("\$top")] = BPDict(mapOf(BPAsciiString("root") to uid))
        map[BPAsciiString("\$objects")] = BPArray(objects)

        return BPDict(map)
    }

    private fun encodeArray(obj: NSArray): BPUid {
        val classUid = classesToUid("NSArray", listOf("NSObject"))
        val objUids = obj.values.map { encodeToUid(it) }

        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$class") to classUid,
            BPAsciiString("NS.objects") to BPArray(objUids),
        )
        objects.add(BPDict(map))
        return BPUid.fromInt(objects.size-1)
    }

    private fun encodeSet(obj: NSSet): BPUid {
        val classUid = classesToUid("NSSet", listOf("NSObject"))
        val objUids = obj.values.map { encodeToUid(it) }

        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$class") to classUid,
            BPAsciiString("NS.objects") to BPArray(objUids),
        )
        objects.add(BPDict(map))
        return BPUid.fromInt(objects.size-1)
    }

    private fun encodeDate(obj: NSDate): BPUid {
        val classUid = classesToUid("NSDate", listOf("NSObject"))
        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$class") to classUid,
            BPAsciiString("NS.time") to BPReal(obj.value.toAppleTimestamp()),
        )
        objects.add(BPDict(map))
        return BPUid.fromInt(objects.size-1)
    }

    private fun encodeData(obj: NSData): BPUid {
        val classUid = classesToUid("NSData", listOf("NSObject"))
        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$class") to classUid,
            BPAsciiString("NS.data") to BPData(obj.value),
        )
        objects.add(BPDict(map))
        return BPUid.fromInt(objects.size-1)
    }

    private fun encodeUUID(obj: NSUUID): BPUid {
        TODO()
    }

    private fun encodeDict(obj: NSDict): BPUid {
        val classUid = classesToUid("NSDictionary", listOf("NSObject"))

        val keyUids = obj.values.keys.map { encodeToUid(it) }
        val objUids = obj.values.values.map { encodeToUid(it) }

        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$class") to classUid,
            BPAsciiString("NS.keys") to BPArray(keyUids),
            BPAsciiString("NS.objects") to BPArray(objUids),
        )
        objects.add(BPDict(map))
        return BPUid.fromInt(objects.size-1)
    }

    private fun encodeToUid(obj: BPListObject): BPUid {
        val cachedIdx = objects.indexOf(obj)
        if(cachedIdx != -1)
            return BPUid.fromInt(cachedIdx)

        return when(obj) {
            is CodableBPListObject -> {
                objects.add(obj)
                BPUid.fromInt(objects.size-1)
            }
            is NSDict -> encodeDict(obj)
            is NSDate -> encodeDate(obj)
            is NSArray -> encodeArray(obj)
            else -> throw Exception("Unsupported object to encode: $obj")
        }
    }

    private fun classesToUid(className: String, superclasses: List<String>): BPUid {
        if(classTranslationCache.containsKey(className))
            return classTranslationCache[className]!!

        val map = mapOf<CodableBPListObject,CodableBPListObject>(
            BPAsciiString("\$classname") to BPAsciiString(className),
            BPAsciiString("\$classes") to BPArray((listOf(className) + superclasses).map { BPAsciiString(it) }),
        )

        objects.add(BPDict(map))
        val uid = BPUid.fromInt(objects.size-1)
        classTranslationCache[className] = uid
        return uid
    }
}