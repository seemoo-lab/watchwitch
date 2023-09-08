package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.hex
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt

class WorkoutEvent(
    val type: Int?,
    val date: Date?,
    val swimmingStrokeStyle: Int?,
    val metadataDictionary: MetadataDictionary?,
    val duration: Double?
) {
    companion object : PBParsable<WorkoutEvent>() {
        override fun fromSafePB(pb: ProtoBuf): WorkoutEvent {
            val type = pb.readShortVarInt(1)
            val date = pb.readOptDate(2)
            val swimmingStrokeStyle = pb.readOptShortVarInt(3)
            val metadataDictionary = MetadataDictionary.fromPB(pb.readOptionalSinglet(4) as ProtoBuf?)
            val duration = (pb.readOptionalSinglet(5) as ProtoI64?)?.asDouble()
            return WorkoutEvent(type, date, swimmingStrokeStyle, metadataDictionary, duration)
        }
    }

    override fun toString(): String {
        return "WorkoutEvent(${Workout.typeToString(type)}, date $date, strokeStyle $swimmingStrokeStyle, metadata $metadataDictionary, duration $duration)"
    }
}

class QuantitySeriesDatum(
    val startDate: Date,
    val endDate: Date,
    val value: Long
) {
    companion object : PBParsable<QuantitySeriesDatum>() {
        override fun fromSafePB(pb: ProtoBuf): QuantitySeriesDatum {
            val endDate = pb.readOptDate(1)!!
            val value = (pb.readAssertedSinglet(2) as ProtoI64).value
            val startDate = pb.readOptDate(3)!!
            return QuantitySeriesDatum(startDate, endDate, value)
        }
    }

    override fun toString(): String {
        return "QSDatum($startDate-$endDate: $value)"
    }
}

class TimestampedKeyValuePair(
    val key: String,
    val timestamp: Date,
    val numberIntValue: Int?,
    val numberDoubleValue: Double?,
    val stringValue: String?,
    val byteValue: ByteArray?
) {
    companion object : PBParsable<TimestampedKeyValuePair>() {
        override fun fromSafePB(pb: ProtoBuf): TimestampedKeyValuePair {
            val key = pb.readOptString(1)!!
            val timestamp = pb.readOptDate(2)!!
            val numberIntValue = pb.readOptShortVarInt(3)
            val numberDoubleValue = (pb.readOptionalSinglet(4) as ProtoI64?)?.asDouble()
            val stringValue = pb.readOptString(5)
            val bytesValue = (pb.readOptionalSinglet(6) as ProtoLen?)?.value

            return TimestampedKeyValuePair(key, timestamp, numberIntValue, numberDoubleValue, stringValue, bytesValue)
        }
    }

    override fun toString(): String {
        val valueString = when {
            stringValue != null -> stringValue
            numberIntValue != null -> numberIntValue.toString()
            numberDoubleValue != null -> numberDoubleValue.toString()
            byteValue != null -> byteValue.hex()
            else -> throw Exception("TimestampedKeyValuePair has no non-null value")
        }
        return "[$key @ $timestamp: $valueString]"
    }
}

class MetadataDictionary(
    val entries: List<MetadataKeyValuePair>
) {
    companion object : PBParsable<MetadataDictionary>() {
        override fun fromSafePB(pb: ProtoBuf): MetadataDictionary {
            val entries = pb.readMulti(1).map {
                if(it !is ProtoBuf)
                    throw Exception("Unexpected metadata dict entry: $it")
                MetadataKeyValuePair.fromSafePB(it as ProtoBuf)
            }
            return MetadataDictionary(entries)
        }
    }

    override fun toString() = entries.toString()
}

class MetadataKeyValuePair(
    val key: String,
    val stringValue: String?,
    val dateValue: Date?,
    val numberIntValue: Int?,
    val numberDoubleValue: Double?,
    val quantityValue: Quantity?,
    val dataValue: ProtoValue?
) {
    companion object : PBParsable<MetadataKeyValuePair>() {
        override fun fromSafePB(pb: ProtoBuf): MetadataKeyValuePair {
            val key = pb.readOptString(1)!!
            val stringValue = pb.readOptString(2)
            val dateValue = pb.readOptDate(3)
            val numberIntValue = pb.readOptShortVarInt(4)
            val numberDoubleValue = (pb.readOptionalSinglet(5) as ProtoI64?)?.asDouble()
            val quantityValue = Quantity.fromPB(pb.readOptionalSinglet(6) as ProtoBuf?)
            val dataValue = pb.readOptionalSinglet(7)

            return MetadataKeyValuePair(key, stringValue, dateValue, numberIntValue, numberDoubleValue, quantityValue, dataValue)
        }
    }

    override fun toString(): String {
        val valueStr = when {
            stringValue != null -> stringValue
            dateValue != null -> dateValue.toString()
            numberIntValue != null -> numberIntValue.toString()
            numberDoubleValue != null -> numberDoubleValue.toString()
            quantityValue != null -> quantityValue.toString()
            dataValue != null -> dataValue.toString()
            else -> throw Exception("MetadataKeyValuePair has no non-null value")
        }
        return "MKVP($key -> $valueStr)"
    }
}

class Quantity(
    val value: Double,
    val unitString: String
) {
    companion object : PBParsable<Quantity>() {
        override fun fromSafePB(pb: ProtoBuf): Quantity {
            val value = (pb.readAssertedSinglet(1) as ProtoI64).asDouble()
            val unitString = pb.readOptString(2)!!
            return Quantity(value, unitString)
        }
    }

    override fun toString() = "${(value*1000).roundToInt().toDouble()/1000}$unitString"
}

class StatisticsQuantityInfo(
    val startDate: Date,
    val endDate: Date,
    val unit: String,
    val value: Double
) : NanoSyncEntity {
    companion object : PBParsable<StatisticsQuantityInfo>() {
        override fun fromSafePB(pb: ProtoBuf): StatisticsQuantityInfo {
            val startDate = pb.readOptDate(1)!!
            val endDate = pb.readOptDate(2)!!
            val unit = pb.readOptString(3)!!
            val value = pb.readOptDouble(4)!!
            return StatisticsQuantityInfo(startDate, endDate, unit, value)
        }
    }

    override fun toString() = "StatisticsQuantityInfo(${(value*1000).roundToInt().toDouble()/1000}$unit @ $startDate - $endDate)"
}

class HealthObject(
    val uuid: UUID,
    val metadataDictionary: MetadataDictionary?,
    val sourceBundleIdentifier: String?,
    val creationDate: Date?,
    val externalSyncObjectCode: Long?
) {
    companion object : PBParsable<HealthObject>() {
        override fun fromSafePB(pb: ProtoBuf): HealthObject {

            val uuid = Utils.uuidFromBytes((pb.readAssertedSinglet(1) as ProtoLen).value)
            val metadata = MetadataDictionary.fromPB(pb.readOptionalSinglet(2) as ProtoBuf?)
            val bundle = pb.readOptString(3)
            val created = (pb.readOptionalSinglet(4) as ProtoI64?)?.asDate()
            val syncObjCode = pb.readOptLongVarInt(5)

            return HealthObject(uuid, metadata, bundle, created, syncObjCode)
        }
    }

    override fun toString(): String {
        return "HealthObject($uuid, bundle $sourceBundleIdentifier, created $creationDate, syncObjCode $externalSyncObjectCode, metadata $metadataDictionary)"
    }
}

class Authorization(
    val objectType: Int?,
    val authorizationStatus: Long?,
    val authorizationRequest: Long?,
    val modificationDate: Date?,
    val modificationEpoch: Long?,
    val authorizationMode: Long?
) {
    companion object : PBParsable<Authorization>() {
        override fun fromSafePB(pb: ProtoBuf): Authorization {
            val objectType = pb.readOptShortVarInt(1)
            val authorizationStatus = pb.readOptLongVarInt(2)
            val authorizationRequest = pb.readOptLongVarInt(3)
            val modificationDate = (pb.readOptionalSinglet(4) as ProtoI64?)?.asDate()
            val modificationEpoch = pb.readOptLongVarInt(5)
            val authorizationMode = pb.readOptLongVarInt(6)

            return Authorization(objectType, authorizationStatus, authorizationRequest, modificationDate, modificationEpoch, authorizationMode)
        }
    }

    override fun toString() = "Authorization(objType $objectType, status: $authorizationStatus, req: $authorizationRequest, modified $modificationDate, modifyEpoch $modificationEpoch, mode $authorizationMode)"
}