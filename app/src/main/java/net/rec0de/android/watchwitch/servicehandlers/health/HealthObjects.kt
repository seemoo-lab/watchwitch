package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI32
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.toAppleTimestamp
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
            val metadataDictionary = MetadataDictionary.fromPB(pb.readOptPB(4))
            val duration = pb.readOptDouble(5)
            return WorkoutEvent(type, date, swimmingStrokeStyle, metadataDictionary, duration)
        }

        // from __HKWorkoutEventTypeName in HealthKit
        private val eventTypes = listOf("Pause", "Resume", "Lap", "Marker", "MotionPaused", "MotionResumed", "Segment", "PauseOrResumeRequest")

        fun typeToString(type: Int?): String {
            return when (type) {
                null -> "null"
                in 1..8 -> eventTypes[type - 1]
                else -> "Unknown($type)"
            }
        }
    }

    override fun toString(): String {
        return "WorkoutEvent(${typeToString(type)}, date $date, strokeStyle $swimmingStrokeStyle, metadata $metadataDictionary, duration $duration)"
    }
}

class QuantitySeriesDatum(
    val startDate: Date,
    val endDate: Date,
    val value: Double
) {
    companion object : PBParsable<QuantitySeriesDatum>() {
        override fun fromSafePB(pb: ProtoBuf): QuantitySeriesDatum {
            val endDate = pb.readOptDate(1)!!
            val value = pb.readOptDouble(2)!!
            val startDate = pb.readOptDate(3)!!
            return QuantitySeriesDatum(startDate, endDate, value)
        }
    }

    override fun toString(): String {
        return "QSDatum($startDate-$endDate: $value)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        fields[1] = listOf(ProtoI64(startDate.toAppleTimestamp()))
        fields[3] = listOf(ProtoI64(endDate.toAppleTimestamp()))
        fields[2] = listOf(ProtoI64(value))
        return ProtoBuf(fields).renderStandalone()
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
            val numberDoubleValue = pb.readOptDouble(4)
            val stringValue = pb.readOptString(5)
            val bytesValue = (pb.readOptionalSinglet(6) as ProtoLen?)?.value

            return TimestampedKeyValuePair(key, timestamp, numberIntValue, numberDoubleValue, stringValue, bytesValue)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoString(key))
        fields[2] = listOf(ProtoI64(timestamp.toAppleTimestamp()))

        if(numberIntValue != null)
            fields[3] = listOf(ProtoVarInt(numberIntValue))
        if(numberDoubleValue != null)
            fields[4] = listOf(ProtoI64(numberDoubleValue))
        if(stringValue != null)
            fields[5] = listOf(ProtoString(stringValue))
        if(byteValue != null)
            fields[6] = listOf(ProtoLen(byteValue))

        return ProtoBuf(fields).renderStandalone()
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
                if(it !is ProtoLen)
                    throw Exception("Unexpected metadata dict entry: $it")
                MetadataKeyValuePair.fromSafePB(it.asProtoBuf())
            }
            return MetadataDictionary(entries)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        fields[1] = entries.map { ProtoLen(it.renderProtobuf()) }
        return ProtoBuf(fields).renderStandalone()
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
            val numberDoubleValue = pb.readOptDouble(5)
            val quantityValue = Quantity.fromPB(pb.readOptPB(6))
            val dataValue = pb.readOptionalSinglet(7)

            return MetadataKeyValuePair(key, stringValue, dateValue, numberIntValue, numberDoubleValue, quantityValue, dataValue)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoString(key))

        if(stringValue != null)
            fields[2] = listOf(ProtoString(stringValue))

        if(dateValue != null)
            fields[3] = listOf(ProtoI64(dateValue.toAppleTimestamp()))

        if(numberIntValue != null)
            fields[4] = listOf(ProtoVarInt(numberIntValue))

        if(numberDoubleValue != null)
            fields[5] = listOf(ProtoI64(numberDoubleValue))

        if(quantityValue != null)
            fields[6] = listOf(ProtoLen(quantityValue.renderProtobuf()))

        if(dataValue != null)
            fields[7] = listOf(dataValue)

        return ProtoBuf(fields).renderStandalone()
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
            val value = pb.readOptDouble(1)!!
            val unitString = pb.readOptString(2)!!
            return Quantity(value, unitString)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoI64(value))
        fields[2] = listOf(ProtoString(unitString))

        return ProtoBuf(fields).renderStandalone()
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

class LocationDatum(
    val timestamp: Date,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Double?,
    val course: Double?,
    val horizontalAccuracy: Double?,
    val verticalAccuracy: Double?
) {
    companion object : PBParsable<LocationDatum>() {
        override fun fromSafePB(pb: ProtoBuf): LocationDatum {
            val timestamp = pb.readOptDate(1)!!
            val latitude = pb.readOptDouble(2)!!
            val longitude = pb.readOptDouble(3)!!
            val altitude = pb.readOptDouble(4)
            val speed = pb.readOptDouble(5)
            val course = pb.readOptDouble(6)
            val horizontalAccuracy = pb.readOptDouble(7)
            val verticalAccuracy = pb.readOptDouble(8)

            return LocationDatum(timestamp, latitude, longitude, altitude, speed, course, horizontalAccuracy, verticalAccuracy)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoI64(timestamp.toAppleTimestamp()))
        fields[2] = listOf(ProtoI64(latitude))
        fields[3] = listOf(ProtoI64(longitude))

        if(altitude != null)
            fields[4] = listOf(ProtoI64(altitude))

        if(speed != null)
            fields[5] = listOf(ProtoI64(speed))

        if(course != null)
            fields[6] = listOf(ProtoI64(course))

        if(horizontalAccuracy != null)
            fields[7] = listOf(ProtoI64(horizontalAccuracy))

        if(verticalAccuracy != null)
            fields[8] = listOf(ProtoI64(verticalAccuracy))

        return ProtoBuf(fields).renderStandalone()
    }

    override fun toString(): String {
        return "LocationDatum($timestamp, $latitude, $longitude, ${altitude}m, $speed, $course, +-$horizontalAccuracy/$verticalAccuracy)"
    }
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
            val metadata = MetadataDictionary.fromPB(pb.readOptPB(2))
            val bundle = pb.readOptString(3)
            val created = pb.readOptDate(4)
            val syncObjCode = pb.readOptLongVarInt(5)

            return HealthObject(uuid, metadata, bundle, created, syncObjCode)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoLen(Utils.uuidToBytes(uuid)))

        if(metadataDictionary != null)
            fields[2] = listOf(ProtoLen(metadataDictionary.renderProtobuf()))

        if(sourceBundleIdentifier != null)
            fields[3] = listOf(ProtoString(sourceBundleIdentifier))

        if(creationDate != null)
            fields[4] = listOf(ProtoI64(creationDate.toAppleTimestamp()))

        if(externalSyncObjectCode != null)
            fields[5] = listOf(ProtoVarInt(externalSyncObjectCode))

        return ProtoBuf(fields).renderStandalone()
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
            val modificationDate = pb.readOptDate(4)
            val modificationEpoch = pb.readOptLongVarInt(5)
            val authorizationMode = pb.readOptLongVarInt(6)

            return Authorization(objectType, authorizationStatus, authorizationRequest, modificationDate, modificationEpoch, authorizationMode)
        }
    }

    override fun toString() = "Authorization(objType $objectType, status: $authorizationStatus, req: $authorizationRequest, modified $modificationDate, modifyEpoch $modificationEpoch, mode $authorizationMode)"
}

// from binarysample::ElectrocardiogramLead::readFrom(ElectrocardiogramLead *this,Reader *param_1) in HealthKit
class ElectrocardiogramLead(
    val unkInt: Int?,
    val samples: List<Float>
) {
    companion object : PBParsable<ElectrocardiogramLead>() {
        override fun fromSafePB(pb: ProtoBuf): ElectrocardiogramLead {
            val unkInt = pb.readOptShortVarInt(1)
            val samples = pb.readMulti(3).map { (it as ProtoI32).asFloat() }
            return ElectrocardiogramLead(unkInt, samples)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        if(unkInt != null)
            fields[1] = listOf(ProtoVarInt(unkInt))

        fields[2] = samples.map { ProtoI32(it) }

        return ProtoBuf(fields).renderStandalone()
    }

    override fun toString(): String {
        return "Lead($unkInt, ${samples.size} voltage samples)"
    }
}

class Electrocardiogram(
    val sampleRate: Double?,
    val lead: ElectrocardiogramLead
) {
    companion object : PBParsable<Electrocardiogram>() {
        override fun fromSafePB(pb: ProtoBuf): Electrocardiogram {
            val sampleRate = pb.readOptDouble(2)
            val lead = ElectrocardiogramLead.fromSafePB((pb.readAssertedSinglet(1) as ProtoLen).asProtoBuf())
            return Electrocardiogram(sampleRate, lead)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = listOf(ProtoLen(lead.renderProtobuf()))

        if(sampleRate != null)
            fields[2] = listOf(ProtoI64(sampleRate))

        return ProtoBuf(fields).renderStandalone()
    }

    override fun toString(): String {
        return "ECG(${sampleRate}hz, $lead)"
    }
}