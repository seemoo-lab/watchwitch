package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.hex
import java.util.Date
import java.util.UUID

class Sample(
    val dataType: Int,
    val healthObject: HealthObject,
    val startDate: Date?,
    val endDate: Date?
) {
    companion object : PBParsable<Sample>() {
        override fun fromSafePB(pb: ProtoBuf): Sample {

            val obj = HealthObject.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val dataType = pb.readShortVarInt(2)
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
    val sample: Sample
) {
    companion object : PBParsable<DeletedSample>() {
        override fun fromSafePB(pb: ProtoBuf): DeletedSample {
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            return DeletedSample(sample)
        }
    }

    override fun toString(): String {
        return "DeletedSample(sample $sample)"
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

        fun quantityTypeToString(type: Int): String {
            return when(type) {
                0x00 -> "BodyMassIndex"
                0x01 -> "BodyFatPercentage"
                0x02 -> "Height"
                0x03 -> "BodyMass"
                0x04 -> "LeanBodyMass"
                0x05 -> "HeartRate"
                0x07 -> "StepCount"
                0x08 -> "DistanceWalkingRunning"
                0x09 -> "BasalEnergyBurned"
                0x0a -> "ActiveEnergyBurned"
                0x0c -> "FlightsClimbed"
                0x0d -> "NikeFuel"
                0x0e -> "OxygenSaturation"
                0x0f -> "BloodGlucose"
                0x10 -> "BloodPressureSystolic"
                0x11 -> "BloodPressureDiastolic"
                0x12 -> "BloodAlcoholContent"
                0x13 -> "PeripheralPerfusionIndex"
                0x14 -> "DietaryFatTotal"
                0x15 -> "DietaryFatPolyunsaturated"
                0x16 -> "DietaryFatMonounsaturated"
                0x17 -> "DietaryFatSaturated"
                0x18 -> "DietaryCholesterol"
                0x19 -> "DietarySodium"
                0x1a -> "DietaryCarbohydrates"
                0x1b -> "DietaryFiber"
                0x1c -> "DietarySugar"
                0x1d -> "DietaryEnergyConsumed"
                0x1e -> "DietaryProtein"
                0x1f -> "DietaryVitaminA"
                0x20 -> "DietaryVitaminB6"
                0x21 -> "DietaryVitaminB12"
                0x22 -> "DietaryVitaminC"
                0x23 -> "DietaryVitaminD"
                0x24 -> "DietaryVitaminE"
                0x25 -> "DietaryVitaminK"
                0x26 -> "DietaryCalcium"
                0x27 -> "DietaryIron"
                0x28 -> "DietaryThiamin"
                0x29 -> "DietaryRiboflavin"
                0x2a -> "DietaryNiacin"
                0x2b -> "DietaryFolate"
                0x2c -> "DietaryBiotin"
                0x2d -> "DietaryPantothenicAcid"
                0x2e -> "DietaryPhosphorus"
                0x2f -> "DietaryIodine"
                0x30 -> "DietaryMagnesium"
                0x31 -> "DietaryZinc"
                0x32 -> "DietarySelenium"
                0x33 -> "DietaryCopper"
                0x34 -> "DietaryManganese"
                0x35 -> "DietaryChromium"
                0x36 -> "DietaryMolybdenum"
                0x37 -> "DietaryChloride"
                0x38 -> "DietaryPotassium"
                0x39 -> "NumberOfTimesFallen"
                0x3a -> "ElectrodermalActivity"
                0x3c -> "InhalerUsage"
                0x3d -> "RespiratoryRate"
                0x3e -> "BodyTemperature"
                0x43 -> "CalorieGoal"
                0x47 -> "ForcedVitalCapacity"
                0x48 -> "ForcedExpiratoryVolume1"
                0x49 -> "PeakExpiratoryFlowRate"
                0x4b -> "AppleExerciseTime"
                0x4e -> "DietaryCaffeine"
                0x53 -> "DistanceCycling"
                0x57 -> "DietaryWater"
                0x59 -> "UVExposure"
                0x5a -> "BasalBodyTemperature"
                0x65 -> "PushCount"
                0x68 -> "AppleStandHourGoal"
                0x69 -> "BriskMinuteGoal"
                0x6e -> "DistanceSwimming"
                0x6f -> "SwimmingStrokeCount"
                0x71 -> "DistanceWheelchair"
                0x72 -> "WaistCircumference"
                0x76 -> "RestingHeartRate"
                0x7a -> "Zeppelin"
                0x7c -> "VO2Max"
                0x7d -> "InsulinDelivery"
                0x89 -> "WalkingHeartRateAverage"
                0x8a -> "DistanceDownhillSnowSports"
                0x8b -> "HeartRateVariabilitySDNN"
                0xac -> "EnvironmentalAudioExposure"
                0xad -> "HeadphoneAudioExposure"
                0xba -> "AppleStandTime"
                else -> "UnknownQuantityType($type)"
            }
        }
    }

    override fun toString(): String {
        return "QuantitySample(${quantityTypeToString(sample.dataType)} sample $sample, canon $valueInCanonicalUnit, orig $valueInOriginalUnit $originalUnitString, frozen? $frozen, final? $final, count $count, min $min max $max, mostRecent $mostRecent $mostRecentDate duration $mostRecentDuration, quantitySeries $quantitySeriesData)"
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
                // manually reversed
                0xe5 -> "MenstrualSymptomVaginalDryness"
                0xe6 -> "MenstrualSymptomNightSweats"
                0xe7 -> "MenstrualSymptomChills"
                0xe8 -> "MenstrualSymptomHairLoss"
                0xe9 -> "MenstrualSymptomSkinDryness"
                0xea -> "MenstrualSymptomBladderIncontinence"
                0xeb -> "MenstrualSymptomMemoryLapse"
                else -> "UnknownCategoryType($type)"
            }
        }
    }

    override fun toString(): String {
        return "CategorySample(${categoryTypeToString(sample.dataType)} $value, $sample)"
    }
}

class BinarySample(
    val payload: ByteArray,
    val sample: Sample
) {
    companion object : PBParsable<BinarySample>() {
        override fun fromSafePB(pb: ProtoBuf): BinarySample {
            val payload = (pb.readAssertedSinglet(2) as ProtoLen).value
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            return BinarySample(payload, sample)
        }
    }

    override fun toString(): String {
        return "BinarySample($sample, payload ${payload.hex()})"
    }
}

class ActivityCache(
    val sample: Sample,
    val cacheIndex: Int?,
    val energyBurned: Double?,
    val briskMinutes: Double?,
    val activeHours: Double?,
    val stepCount: Int?,
    val energyBurnedGoal: Double?,
    val walkingAndRunningDistance: Double?,
    val energyBurnedGoalDate: Date?,
    val deepBreathingDuration: Double?,
    val pushCount: Int?,
    val flightsClimbed: Int?,
    val wheelchairUse: Int?,
    val sequence: Int?,
    val briskMinutesGoal: Double?,
    val activeHoursGoal: Double?,
    val dailyEnergyBurnedStatistics: List<StatisticsQuantityInfo>,
    val dailyBriskMinutesStatistics: List<StatisticsQuantityInfo>
) {
    companion object : PBParsable<ActivityCache>() {
        override fun fromSafePB(pb: ProtoBuf): ActivityCache {
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val cacheIndex = pb.readOptShortVarInt(2)
            val energyBurned = pb.readOptDouble(3)
            val briskMinutes = pb.readOptDouble(4)
            val activeHours = pb.readOptDouble(5)
            val stepCount = pb.readOptShortVarInt(6)
            val energyBurnedGoal = pb.readOptDouble(7)
            val walkingAndRunningDistance = pb.readOptDouble(8)
            val energyBurnedGoalDate = pb.readOptDate(9)

            val deepBreathingDuration = pb.readOptDouble(18)
            val pushCount = pb.readOptShortVarInt(20)
            val flightsClimbed = pb.readOptShortVarInt(22)
            val wheelchairUse = pb.readOptShortVarInt(24)

            val dailyEnergyBurnedStats = pb.readMulti(31).map { StatisticsQuantityInfo.fromSafePB(it as ProtoBuf) }
            val dailyBriskMinutesStats = pb.readMulti(32).map { StatisticsQuantityInfo.fromSafePB(it as ProtoBuf) }

            val sequence = pb.readOptShortVarInt(33)
            val briskMinutesGoal = pb.readOptDouble(34)
            val activeHoursGoal = pb.readOptDouble(35)
            val moveMinutesGoal = pb.readOptDouble(36) // highly speculative, not present in my version of healthd

            val someTimestamp1 = pb.readOptDate(40)
            val someTimestamp2 = pb.readOptDate(41)
            val someNumber = pb.readOptShortVarInt(42)

            return ActivityCache(sample, cacheIndex, energyBurned, briskMinutes, activeHours, stepCount, energyBurnedGoal, walkingAndRunningDistance, energyBurnedGoalDate, deepBreathingDuration, pushCount, flightsClimbed, wheelchairUse, sequence, briskMinutesGoal, activeHoursGoal, dailyEnergyBurnedStats, dailyBriskMinutesStats)
        }


    }

    override fun toString(): String {
        return "ActivityCache(sample $sample, energy $energyBurned, briskMinutes $briskMinutes, activeHours $activeHours, steps $stepCount, energyStats $dailyEnergyBurnedStatistics, brisk stats $dailyBriskMinutesStatistics)"
    }
}

class Workout(
    val sample: Sample,
    val type: Int,
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
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val type = pb.readShortVarInt(2)
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

        // from __HKWorkoutActivityNameForActivityType(long param_1) in HealthKit
        private val activityTypes = listOf("AmericanFootball", "Archery", "AustralianFootball", "Badminton", "Baseball", "Basketball", "Bowling", "Boxing", "Climbing", "Cricket", "CrossTraining", "Curling", "Cycling", "Dance", "DanceInspiredTraining", "Elliptical", "EquestrianSports", "Fencing", "Fishing", "FunctionalStrengthTraining", "Golf", "Gymnastics", "Handball", "Hiking", "Hockey", "Hunting", "Lacrosse", "MartialArts", "MindAndBody", "MixedMetabolicCardioTraining", "PaddleSports", "Play", "PreparationAndRecovery", "Racquetball", "Rowing", "Rugby", "Running", "Sailing", "SkatingSports", "SnowSports", "Soccer", "Softball", "Squash", "StairClimbing", "SurfingSports", "Swimming", "TableTennis", "Tennis", "TrackAndField", "TraditionalStrengthTraining", "Volleyball", "Walking", "WaterFitness", "WaterPolo", "WaterSports", "Wrestling", "Yoga", "Barre", "CoreTraining", "CrossCountrySkiing", "DownhillSkiing", "Flexibility", "HighIntensityIntervalTraining", "JumpRope", "Kickboxing", "Pilates", "Snowboarding", "Stairs", "StepTraining", "WheelchairWalk", "WheelchairRun", "TaiChi", "MixedCardio", "HandCycling", "DiscSports", "FitnessGaming")

        fun typeToString(type: Int?): String {
            return when (type) {
                null -> "null"
                in 1..76 -> activityTypes[type - 1]
                2000 -> "Wheelchair"
                3000 -> "Other"
                else -> "Unknown($type)"
            }
        }
    }

    override fun toString(): String {
        return "Workout(${typeToString(type)}, sample $sample, event $workoutEvent, duration $duration, energy $totalEnergyBurnedInCanonicalUnit, distance $totalDistanceInCanonicalUnit, goalType $goalType, goal $goal, flights $totalFlightsClimbedInCanonicalUnit, strokes $totalSwimmingStrokeCountInCanonicalUnit, basal energy $totalBasalEnergyBurnedInCanonicalUnit)"
    }
}

class LocationSeries(
    val sample: Sample,
    val frozen: Boolean?,
    val final: Boolean?,
    val continuationUUID: UUID?,
    val locationData: List<LocationDatum>
) {
    companion object : PBParsable<LocationSeries>() {
        override fun fromSafePB(pb: ProtoBuf): LocationSeries {
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val frozen = pb.readOptBool(2)
            val uuidBytes = (pb.readOptionalSinglet(3) as ProtoLen?)?.value
            val continuationUUID = if(uuidBytes == null) null else Utils.uuidFromBytes(uuidBytes)
            val final = pb.readOptBool(4)
            val locationData = pb.readMulti(10).map { LocationDatum.fromSafePB(it as ProtoBuf) }
            return LocationSeries(sample, frozen, final, continuationUUID, locationData)
        }
    }

    override fun toString(): String {
        return "LocationSeries($sample, contUUID $continuationUUID, points $locationData)"
    }
}

class ECGSample(
    val sample: Sample,
    val version: Int?,
    val heartRate: Double?,
    val ecg: Electrocardiogram,
    val classification: Int?
) {
    companion object : PBParsable<ECGSample>() {
        override fun fromSafePB(pb: ProtoBuf): ECGSample {
            val sample = Sample.fromSafePB(pb.readAssertedSinglet(1) as ProtoBuf)
            val maybeVersion = pb.readOptShortVarInt(2)
            val heartrate = pb.readOptDouble(3)
            val ecg = Electrocardiogram.fromSafePB(pb.readAssertedSinglet(4) as ProtoBuf)
            val maybeClassification = pb.readOptShortVarInt(5)

            return ECGSample(sample, maybeVersion, heartrate, ecg, maybeClassification)
        }
    }

    override fun toString(): String {
        return "ECGSample($sample, $version, heartrate $heartRate, $classification, $ecg)"
    }
}