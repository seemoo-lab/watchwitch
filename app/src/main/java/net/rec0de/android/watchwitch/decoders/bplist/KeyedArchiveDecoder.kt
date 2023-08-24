package net.rec0de.android.watchwitch.decoders.bplist

import net.rec0de.android.watchwitch.fromBytesBig
import java.util.Date

class KeyedArchiveDecoder {
    companion object {
        private val topKey = BPAsciiString("\$top")
        private val rootKey = BPAsciiString("root")
        private val objectsKey = BPAsciiString("\$objects")
        private val classKey = BPAsciiString("\$class")
        private val classNameKey = BPAsciiString("\$classname")

        fun isKeyedArchive(data: BPListObject): Boolean {
            val archiverKey = BPAsciiString("\$archiver")
            val expectedArchiver = BPAsciiString("NSKeyedArchiver")
            return data is BPDict && data.values.containsKey(archiverKey) && data.values[archiverKey] == expectedArchiver
        }

        fun decode(data: BPDict): BPListObject {
            // get offset of the root object in the $objects list
            val top =
                UInt.fromBytesBig(((data.values[topKey]!! as BPDict).values[rootKey]!! as BPUid).value)
                    .toInt()

            val objects = data.values[objectsKey]!! as BPArray

            val rootObj = objects.values[top]
            val resolved = optionallyResolveObjectReference(rootObj, objects)
            return transformSupportedClasses(resolved)
        }

        private fun optionallyResolveObjectReference(thing: CodableBPListObject, objects: BPArray): CodableBPListObject {
            return when(thing) {
                is BPUid -> optionallyResolveObjectReference(objects.values[UInt.fromBytesBig(thing.value).toInt()], objects)
                is BPArray -> BPArray(thing.entries, thing.values.map { optionallyResolveObjectReference(it, objects) })
                is BPSet -> BPSet(thing.entries, thing.values.map { optionallyResolveObjectReference(it, objects) })
                is BPDict -> BPDict(thing.values.map {
                    Pair(optionallyResolveObjectReference(it.key, objects), optionallyResolveObjectReference(it.value, objects))
                }.toMap())
                else -> thing
            }
        }

        private fun transformSupportedClasses(thing: CodableBPListObject): BPListObject {
            return when(thing) {
                is BPArray -> BPArray(thing.entries, thing.values.map { transformSupportedClasses(it) as CodableBPListObject }) // not sure if this can blow up, but i gotta get it to compile for now
                is BPSet -> BPSet(thing.entries, thing.values.map { transformSupportedClasses(it) as CodableBPListObject })
                is BPDict -> {
                    if(thing.values.containsKey(classKey)) {
                        val className = ((thing.values[classKey] as BPDict).values[classNameKey] as BPAsciiString).value
                        when(className) {
                            "NSDictionary", "NSMutableDictionary" -> {
                                val keyList = (thing.values[BPAsciiString("NS.keys")]!! as BPArray).values.map { transformSupportedClasses(it) }
                                val valueList = (thing.values[BPAsciiString("NS.objects")]!! as BPArray).values.map { transformSupportedClasses(it) }
                                val map = keyList.zip(valueList).toMap()
                                NSDict(map)
                            }
                            "NSMutableString", "NSString" -> {
                                val string = (thing.values[BPAsciiString("NS.string")]!! as BPAsciiString)
                                string
                            }
                            "NSArray" -> {
                                val list = (thing.values[BPAsciiString("NS.objects")]!! as BPArray).values.map { transformSupportedClasses(it) }
                                NSArray(list)
                            }
                            "NSDate" -> {
                                val timestamp = (thing.values[BPAsciiString("NS.time")]!! as BPReal).value
                                // NSDates encode time as seconds from Jan 01 2001, we convert to standard unix time here
                                NSDate(Date((timestamp*1000).toLong() + 978307200000))
                            }
                            else -> BPDict(thing.values.map { Pair(transformSupportedClasses(it.key) as CodableBPListObject, transformSupportedClasses(it.value) as CodableBPListObject) }.toMap())
                        }
                    }
                    else {
                        BPDict(thing.values.map { Pair(transformSupportedClasses(it.key) as CodableBPListObject, transformSupportedClasses(it.value) as CodableBPListObject) }.toMap())
                    }
                }
                else -> thing
            }
        }
    }
}