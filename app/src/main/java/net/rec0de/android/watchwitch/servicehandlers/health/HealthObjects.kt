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

abstract class HealthDataObject {
    companion object {
        fun fromSafePB(pb: ProtoBuf, type: Int): HealthDataObject {
            return when(type) {
                0x13 -> DeletedSample.fromSafePB(pb)
                else -> throw Exception("Unsupported HealthDataObject type $type")
            }
        }

    }
}

class Workout(
    val sample: Sample?,
    val type: Int?,
    val workoutEvent: WorkoutEvent?,
    val duration: Double?,
    val totalEnergyBurnedInCanonicalUnit: Double?,
    val totalDistanceInCanonicalUnit: Double?,
    val goalType: Int?,
    val goal: Double?,
    val totalBasalEnergyBurnedInCanonicalUnit: Double?,
    val totalSwimmingStrokeCountInCanonicalUnit: Double?,
    val totalFlightsClimbedInCanonicalUnit: Double?
) {
    companion object : PBParsable<Workout>() {
        override fun fromSafePB(pb: ProtoBuf): Workout {
            val sample = Sample.fromPB(pb.readOptionalSinglet(1) as ProtoBuf?)
            val type = pb.readOptShortVarInt(2)
            val workoutEvent = WorkoutEvent.fromPB(pb.readOptionalSinglet(3) as ProtoBuf?)
            val duration = pb.readOptDouble(4)
            val totalEnergyBurnedInCanonicalUnit = pb.readOptDouble(5)
            val totalDistanceInCanonicalUnit = pb.readOptDouble(6)
            val goalType = pb.readOptShortVarInt(7)
            val goal = pb.readOptDouble(8)
            val totalBasalEnergyBurnedInCanonicalUnit = pb.readOptDouble(9)
            val totalSwimmingStrokeCountInCanonicalUnit = pb.readOptDouble(10)
            val totalFlightsClimbedInCanonicalUnit = pb.readOptDouble(11)

            return Workout(sample, type, workoutEvent, duration, totalEnergyBurnedInCanonicalUnit, totalDistanceInCanonicalUnit, goalType, goal, totalBasalEnergyBurnedInCanonicalUnit, totalSwimmingStrokeCountInCanonicalUnit, totalFlightsClimbedInCanonicalUnit)
        }

        private val activityTypes = listOf("AmericanFootball", "Archery", "AustralianFootball", "Badminton", "Baseball", "Basketball", "Bowling", "Boxing", "Climbing", "Cricket", "CrossTraining", "Curling", "Cycling", "Dance", "DanceInspiredTraining", "Elliptical", "EquestrianSports", "Fencing", "Fishing", "FunctionalStrengthTraining", "Golf", "Gymnastics", "Handball", "Hiking", "Hockey", "Hunting", "Lacrosse", "MartialArts", "MindAndBody", "MixedMetabolicCardioTraining", "PaddleSports", "Play", "PreparationAndRecovery", "Racquetball", "Rowing", "Rugby", "Running", "Sailing", "SkatingSports", "SnowSports", "Soccer", "Softball", "Squash", "StairClimbing", "SurfingSports", "Swimming", "TableTennis", "Tennis", "TrackAndField", "TraditionalStrengthTraining", "Volleyball", "Walking", "WaterFitness", "WaterPolo", "WaterSports", "Wrestling", "Yoga", "Barre", "CoreTraining", "CrossCountrySkiing", "DownhillSkiing", "Flexibility", "HighIntensityIntervalTraining", "JumpRope", "Kickboxing", "Pilates", "Snowboarding", "Stairs", "StepTraining", "WheelchairWalk", "WheelchairRun", "HandCycling", "TaiChi")

        fun typeToString(type: Int?): String {
            return when (type) {
                null -> "null"
                in 1..73 -> activityTypes[type - 1]
                2000 -> "Wheelchair"
                3000 -> "Other"
                else -> "Unknown"
            }
        }
    }

    override fun toString(): String {
        return "Workout(${typeToString(type)}, sample $sample, event $workoutEvent, duration $duration, energy $totalEnergyBurnedInCanonicalUnit, distance $totalDistanceInCanonicalUnit, goalType $goalType, goal $goal, flights $totalFlightsClimbedInCanonicalUnit, strokes $totalSwimmingStrokeCountInCanonicalUnit, basal energy $totalBasalEnergyBurnedInCanonicalUnit)"
    }
}

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

class QuantitySample(
    val sample: Sample,
    val valueInCanonicalUnit: Double?,
    val valueInOriginalUnit: Double?,
    val originalUnitString: String?,
    val frozen: Boolean?,
    val count: Int?,
    val final: Boolean?,
    val min: Double?,
    val max: Double?,
    val mostRecent: Long?,
    val mostRecentDate: Date?,
    val quantitySeriesData: List<QuantitySeriesDatum>,
    val mostRecentDuration: Double?
) {
    companion object : PBParsable<QuantitySample>() {
        override fun fromSafePB(pb: ProtoBuf): QuantitySample {
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val valueInCanonicalUnit = (pb.readOptionalSinglet(2) as ProtoI64?)?.asDouble()
            val valueInOriginalUnit = (pb.readOptionalSinglet(3) as ProtoI64?)?.asDouble()
            val originalUnitString = pb.readOptString(4)
            val frozen = pb.readOptBool(5)
            val count = pb.readOptShortVarInt(6)
            val final = pb.readOptBool(7)
            val min = (pb.readOptionalSinglet(8) as ProtoI64?)?.asDouble()
            val max = (pb.readOptionalSinglet(9) as ProtoI64?)?.asDouble()
            val mostRecent = (pb.readOptionalSinglet(10) as ProtoI64?)?.value
            val mostRecentDate = pb.readOptDate(11)
            val quantitySeriesData = pb.readMulti(12).map { QuantitySeriesDatum.fromSafePB(it as ProtoBuf) }
            val mostRecentDuration = (pb.readOptionalSinglet(13) as ProtoI64?)?.asDouble()

            return QuantitySample(sample, valueInCanonicalUnit, valueInOriginalUnit, originalUnitString, frozen, count, final, min, max, mostRecent, mostRecentDate, quantitySeriesData, mostRecentDuration)
        }
    }

    override fun toString(): String {
        return "QuantitySample(sample $sample, canon $valueInCanonicalUnit, orig $valueInOriginalUnit $originalUnitString, frozen? $frozen, final? $final, count $count, min $min max $max, mostRecent $mostRecent $mostRecentDate duration $mostRecentDuration, quantitySeries $quantitySeriesData)"
    }
}

class CategorySample(
    val value: Int,
    val sample: Sample
) {
    companion object : PBParsable<CategorySample>() {
        override fun fromSafePB(pb: ProtoBuf): CategorySample {
            val value = pb.readShortVarInt(2)
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            return CategorySample(value, sample)
        }

        // from HealthKit ___HKCategoryTypeIdentifierLookupTable_block_invoke
        fun categoryTypeToString(type: Int): String {
            return when(type) {
                0x3f -> "SleepAnalysis"
                0x44 -> "WatchActivation"
                0x46 -> "AppleStandHour"
                0x5b -> "CervicalMucusQuality"
                0x5c -> "OvulationTestResult"
                0x5f -> "MenstrualFlow"
                0x60 -> "IntermenstrualBleeding"
                0x61 -> "SexualActivity"
                0x62 -> "CoachingEvent"
                0x63 -> "MindfulSession"
                0x70 -> "WheelchairUseChange"
                0x74 -> "WristEvent"
                0x8c -> "HighHeartRateEvent"
                0x8d -> "HeartStudyEvent"
                0x93 -> "LowHeartRateEvent"
                0x9c -> "IrregularHeartRhythmEvent"
                0x9d -> "MenstrualSymptomAbdominalCramps"
                0x9e -> "MenstrualSymptomBreastTenderness"
                0x9f -> "MenstrualSymptomBloating"
                0xa0 -> "MenstrualSymptomHeadache"
                0xa1 -> "MenstrualSymptomAcne"
                0xa2 -> "MenstrualSymptomLowerBackPain"
                0xa3 -> "MenstrualSymptomOvulationPain"
                0xa4 -> "MenstrualSymptomMoodChanges"
                0xa5 -> "MenstrualSymptomConstipation"
                0xa6 -> "MenstrualSymptomDiarrhea"
                0xa7 -> "MenstrualSymptomTiredness"
                0xa8 -> "MenstrualSymptomNausea"
                0xa9 -> "MenstrualSymptomSleepChanges"
                0xaa -> "MenstrualSymptomAppetiteChanges"
                0xab -> "MenstrualSymptomHotFlashes"
                0xb2 -> "AudioExposureEvent"
                0xbd -> "ToothbrushingEvent"
                else -> throw Exception("Unknown category sample type: $type")
            }
        }
    }

    override fun toString(): String {
        return "CategorySample(${categoryTypeToString(sample!!.dataType!!)} $value, $sample)"
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
            val entries = pb.readMulti(1).map { MetadataKeyValuePair.fromSafePB(it as ProtoBuf) }
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
    val quantityValue: Any?,
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

    override fun toString() = "${(value*1000).roundToInt()/1000}$unitString"
}

class HealthObject(
    val uuid: UUID,
    val metadataDictionary: MetadataDictionary?,
    val sourceBundleIdentifier: String?,
    val creationDate: Date?,
    val externalSyncObjectCode: Long?
) : HealthDataObject() {
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

class Sample(
    val dataType: Int?,
    val healthObject: HealthObject?,
    val startDate: Date?,
    val endDate: Date?
) : HealthDataObject() {
    companion object : PBParsable<Sample>() {
        override fun fromSafePB(pb: ProtoBuf): Sample {

            val obj = HealthObject.fromPB(pb.readOptionalSinglet(1) as ProtoBuf?)
            val dataType = pb.readOptShortVarInt(2)
            val startDate = (pb.readOptionalSinglet(3) as ProtoI64?)?.asDate()
            val endDate = (pb.readOptionalSinglet(3) as ProtoI64?)?.asDate()

            return Sample(dataType, obj, startDate, endDate)
        }
    }

    override fun toString(): String {
        return "Sample(type $dataType, start $startDate, end $endDate, object: $healthObject)"
    }
}

class DeletedSample(
    val sample: Sample?
) : HealthDataObject() {
    companion object : PBParsable<DeletedSample>() {
        override fun fromSafePB(pb: ProtoBuf): DeletedSample {
            val sample = Sample.fromPB(pb.readOptionalSinglet(1) as ProtoBuf?)
            return DeletedSample(sample)
        }
    }

    override fun toString(): String {
        return "DeletedSample(sample $sample)"
    }
}

class Authorization(
    val objectType: Int?,
    val authorizationStatus: Long?,
    val authorizationRequest: Long?,
    val modificationDate: Date?,
    val modificationEpoch: Long?,
    val authorizationMode: Long?
) : HealthDataObject() {
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