package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.servicehandlers.health.ActivityCache
import net.rec0de.android.watchwitch.servicehandlers.health.BinarySample
import net.rec0de.android.watchwitch.servicehandlers.health.CategorySample
import net.rec0de.android.watchwitch.servicehandlers.health.ECGSample
import net.rec0de.android.watchwitch.servicehandlers.health.HealthObject
import net.rec0de.android.watchwitch.servicehandlers.health.LocationSeries
import net.rec0de.android.watchwitch.servicehandlers.health.Provenance
import net.rec0de.android.watchwitch.servicehandlers.health.QuantitySample
import net.rec0de.android.watchwitch.servicehandlers.health.Sample
import net.rec0de.android.watchwitch.servicehandlers.health.Source
import net.rec0de.android.watchwitch.servicehandlers.health.SyncStatusKey
import net.rec0de.android.watchwitch.servicehandlers.health.TimestampedKeyValuePair
import net.rec0de.android.watchwitch.servicehandlers.health.Workout
import net.rec0de.android.watchwitch.toAppleTimestamp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.UUID


object DatabaseWrangler {

    private lateinit var secure: HealthSyncSecureHelper
    private lateinit var regular: HealthSyncHelper

    fun initDbHelper(secure: HealthSyncSecureHelper, regular: HealthSyncHelper) {
        this.secure = secure
        this.regular = regular
    }

    fun insertCategorySample(value: Int, provenanceId: Int, sample: Sample) {
        val id = try {
            insertObject(sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for category sample $sample", 0)
            return
        }

        if(id == -1)
            return

        insertSample(id, sample)
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.CategorySamples.DATA_ID, id)
            put(HealthSyncSecureContract.CategorySamples.VALUE, value)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.CATEGORY_SAMPLES, null, values)
        Logger.logSQL("Inserting category sample: $sample value: $value", 0)
    }

    fun insertBinarySample(sample: BinarySample, provenanceId: Int) {
        val id = try {
            insertObject(sample.sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for binary sample $sample", 0)
            return
        }

        if(id == -1)
            return

        insertSample(id, sample.sample)
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.BinarySamples.DATA_ID, id)
            put(HealthSyncSecureContract.BinarySamples.PAYLOAD, sample.payload)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.BINARY_SAMPLES, null, values)
        Logger.logSQL("Inserting binary sample: $sample payload: ${sample.payload.hex()}", 0)
    }

    fun insertEcgSample(sample: ECGSample, provenanceId: Int) {
        val id = try {
            insertObject(sample.sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for ECG sample $sample", 0)
            return
        }

        if(id == -1)
            return

        insertSample(id, sample.sample)

        val buffer = ByteBuffer.allocate(4 * sample.ecg.lead.samples.size).order(ByteOrder.BIG_ENDIAN)
        sample.ecg.lead.samples.forEach { buffer.putFloat(it) }
        val payload = buffer.array()

        val values = ContentValues().apply {
            put(HealthSyncSecureContract.EcgSamples.DATA_ID, id)
            put(HealthSyncSecureContract.EcgSamples.AVERAGE_HEART_RATE, sample.heartRate)
            put(HealthSyncSecureContract.EcgSamples.SYMPTOPMS_STATUS, sample.version) // this is likely not correct but we'll just put the "version" here to save it
            put(HealthSyncSecureContract.EcgSamples.PRIVATE_CLASSIFICATION, sample.classification)
            put(HealthSyncSecureContract.EcgSamples.VOLTAGE_PAYLOAD, payload)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.ECG_SAMPLES, null, values)
        Logger.logSQL("Inserting ECG sample: $sample", 0)
    }

    fun insertWorkout(sample: Workout, provenanceId: Int) {
        val id = try {
            insertObject(sample.sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for workout $sample", 0)
            return
        }

        if(id == -1)
            return

        insertSample(id, sample.sample)
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.Workouts.DATA_ID, id)
            put(HealthSyncSecureContract.Workouts.ACTIVITY_TYPE, sample.type)
            put(HealthSyncSecureContract.Workouts.GOAL_TYPE, sample.goalType)
            put(HealthSyncSecureContract.Workouts.GOAL, sample.goal)
            put(HealthSyncSecureContract.Workouts.DURATION, sample.duration)
            put(HealthSyncSecureContract.Workouts.TOTAL_BASAL_ENERGY_BURNED, sample.totalBasalEnergyBurnedInCanonicalUnit)
            put(HealthSyncSecureContract.Workouts.TOTAL_ENERGY_BURNED, sample.totalEnergyBurnedInCanonicalUnit)
            put(HealthSyncSecureContract.Workouts.TOTAL_DISTANCE, sample.totalDistanceInCanonicalUnit)
            put(HealthSyncSecureContract.Workouts.TOTAL_FLIGHTS_CLIMBED, sample.totalFlightsClimbedInCanonicalUnit)

        }

        sample.workoutEvents.forEach { evt ->
            val evtValues = ContentValues().apply {
                put(HealthSyncSecureContract.WorkoutEvents.OWNER_ID, id)
                put(HealthSyncSecureContract.WorkoutEvents.DATE, evt.date?.toAppleTimestamp())
                put(HealthSyncSecureContract.WorkoutEvents.TYPE, evt.type)
                put(HealthSyncSecureContract.WorkoutEvents.DURATION, evt.duration)
                put(HealthSyncSecureContract.WorkoutEvents.METADATA, evt.metadataDictionary.toString()) // idk how this is supposed to be handled?
            }
            secure.writableDatabase.insert(HealthSyncSecureContract.WORKOUT_EVENTS, null, evtValues)
            Logger.logSQL("Inserting workout event: $evt", 1)
        }

        secure.writableDatabase.insert(HealthSyncSecureContract.WORKOUTS, null, values)
        Logger.logSQL("Inserting workout: $sample", 0)
    }

    fun insertActivityCache(ac: ActivityCache, provenanceId: Int) {
        // looks like activity caches can be updated once inserted -> check if the given health object already exists
        // android sqlite API does not allow selecting by blob, so we'll do it manually
        val uuidHex = Utils.uuidToBytes(ac.sample.healthObject.uuid).hex()
        val query = "SELECT ${HealthSyncSecureContract.Objects.DATA_ID} FROM ${HealthSyncSecureContract.OBJECTS} WHERE ${HealthSyncSecureContract.Objects.UUID} = X'$uuidHex';"
        val cursor = secure.readableDatabase.rawQuery(query, null);

        var isUpdate = false

        val id = if(cursor.count > 0) {
            isUpdate = true
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        else {
            insertObject(ac.sample.healthObject, provenanceId)

        }
        cursor.close()

        if(id == -1)
            return

        if(!isUpdate)
            insertSample(id, ac.sample)

        val values = ContentValues().apply {
            put(HealthSyncSecureContract.ActivityCaches.DATA_ID, id)
            put(HealthSyncSecureContract.ActivityCaches.CACHE_INDEX, ac.cacheIndex)
            put(HealthSyncSecureContract.ActivityCaches.SEQUENCE, ac.sequence)
            put(HealthSyncSecureContract.ActivityCaches.ACTIVITY_MODE, ac.sample.dataType) // ??
            put(HealthSyncSecureContract.ActivityCaches.WHEELCHAIR_USE, ac.wheelchairUse)
            put(HealthSyncSecureContract.ActivityCaches.ENERGY_BURNED, ac.energyBurned)
            put(HealthSyncSecureContract.ActivityCaches.ENERGY_BURNED_GOAL, ac.energyBurnedGoal)
            put(HealthSyncSecureContract.ActivityCaches.ENERGY_BURNED_GOAL_DATE, ac.energyBurnedGoalDate?.toAppleTimestamp())
            // missing: move minutes, move minutes goal & goal date
            put(HealthSyncSecureContract.ActivityCaches.BRISK_MINUTES, ac.briskMinutes)
            put(HealthSyncSecureContract.ActivityCaches.BRISK_MINUTES_GOAL, ac.briskMinutesGoal)
            // missing: brisk minutes goal date
            put(HealthSyncSecureContract.ActivityCaches.ACTIVE_HOURS, ac.activeHours)
            put(HealthSyncSecureContract.ActivityCaches.ACTIVE_HOURS_GOAL, ac.activeHoursGoal)
            // missing: active hours goal date
            put(HealthSyncSecureContract.ActivityCaches.STEPS, ac.stepCount)
            put(HealthSyncSecureContract.ActivityCaches.PUSHES, ac.pushCount)
            put(HealthSyncSecureContract.ActivityCaches.WALK_DISTANCE, ac.walkingAndRunningDistance)
            put(HealthSyncSecureContract.ActivityCaches.DEEP_BREATHING_DURATION, ac.deepBreathingDuration)
            put(HealthSyncSecureContract.ActivityCaches.FLIGHTS, ac.flightsClimbed)
            put(HealthSyncSecureContract.ActivityCaches.ENERGY_BURNED_STATS, ac.dailyEnergyBurnedStatistics.toString()) // usually stored as NSArchiver bplist
            // missing: dailyMoveMinutesStats
            put(HealthSyncSecureContract.ActivityCaches.BRISK_MINUTES_STATS, ac.dailyBriskMinutesStatistics.toString()) // usually stored as NSArchiver bplist
        }

        secure.writableDatabase.insertWithOnConflict(HealthSyncSecureContract.ACTIVITY_CACHES, null, values, CONFLICT_REPLACE)
        val verb = if(isUpdate) "Updating" else "Inserting"
        Logger.logSQL("$verb activity cache: $ac", 0)
    }

    fun insertLocationSeries(sample: LocationSeries, provenanceId: Int) {
        Logger.logSQL("got LocationSeries: $sample", 0)
        val id = try {
            insertObject(sample.sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for location series $sample", 0)
            return
        }

        if(id == -1)
            return

        insertSample(id, sample.sample)

        val seriesMetadata = ContentValues().apply() {
            put(HealthSyncSecureContract.LocationSeries.DATA_ID, id)
            put(HealthSyncSecureContract.LocationSeries.CONTINUATION_UUID, if(sample.continuationUUID == null) null else Utils.uuidToBytes(sample.continuationUUID))
            put(HealthSyncSecureContract.LocationSeries.FINAL, sample.final)
            put(HealthSyncSecureContract.LocationSeries.FROZEN, sample.frozen)
        }
        val seriesId = secure.writableDatabase.insert(HealthSyncSecureContract.LOCATION_SERIES, null, seriesMetadata)

        sample.locationData.forEachIndexed { i, datum ->
            val point = ContentValues().apply {
                put(HealthSyncSecureContract.LocationSeriesData.SERIES_ID, seriesId)
                put(HealthSyncSecureContract.LocationSeriesData.DATUM_ID, i)
                put(HealthSyncSecureContract.LocationSeriesData.TIMESTAMP, datum.timestamp.toAppleTimestamp())
                put(HealthSyncSecureContract.LocationSeriesData.LATITUDE, datum.latitude)
                put(HealthSyncSecureContract.LocationSeriesData.LONGITUDE, datum.longitude)
                put(HealthSyncSecureContract.LocationSeriesData.ALTITUDE, datum.altitude)
                put(HealthSyncSecureContract.LocationSeriesData.COURSE, datum.course)
                put(HealthSyncSecureContract.LocationSeriesData.SPEED, datum.speed)
                put(HealthSyncSecureContract.LocationSeriesData.HORIZONTAL_ACCURACY, datum.horizontalAccuracy)
                put(HealthSyncSecureContract.LocationSeriesData.VERTICAL_ACCURACY, datum.verticalAccuracy)
            }
            secure.writableDatabase.insert(HealthSyncSecureContract.LOCATION_SERIES_DATA, null, point)
        }
    }

    fun insertQuantitySample(sample: QuantitySample, provenanceId: Int) {
        val id = try {
            insertObject(sample.sample.healthObject, provenanceId)
        } catch(e: SQLiteConstraintException) {
            Logger.logSQL("HealthObject UUID constraint failed for $sample", 0)
            return
        }

        insertSample(id, sample.sample)

        val unitStringId = if(sample.originalUnitString == null) null else getOrInsertUnitString(sample.originalUnitString)

        val values = ContentValues().apply {
            put(HealthSyncSecureContract.QuantitySamples.DATA_ID, id)
            put(HealthSyncSecureContract.QuantitySamples.QUANTITY, sample.valueInCanonicalUnit)
            put(HealthSyncSecureContract.QuantitySamples.ORIGINAL_QUANTITY, sample.valueInOriginalUnit)
            put(HealthSyncSecureContract.QuantitySamples.ORIGINAL_UNIT, unitStringId)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.QUANTITY_SAMPLES, null, values)

        Logger.logSQL("Inserting quantity sample: $sample", 0)

        if(sample.max != null || sample.min != null || sample.mostRecent != null) {
            val stats = ContentValues().apply {
                put(HealthSyncSecureContract.QuantitySampleStatistics.OWNER_ID, id)
                put(HealthSyncSecureContract.QuantitySampleStatistics.MIN, sample.min)
                put(HealthSyncSecureContract.QuantitySampleStatistics.MAX, sample.max)
                put(HealthSyncSecureContract.QuantitySampleStatistics.MOST_RECENT, sample.mostRecent)
                put(HealthSyncSecureContract.QuantitySampleStatistics.MOST_RECENT_DATE, sample.mostRecentDate?.toAppleTimestamp())
                put(HealthSyncSecureContract.QuantitySampleStatistics.MOST_RECENT_DURATION, sample.mostRecentDuration)
            }
            secure.writableDatabase.insert(HealthSyncSecureContract.QUANTITY_SAMPLE_STATISTICS, null, stats)
            Logger.logSQL("Inserting quantity sample statistics", 1)
        }

        if(sample.quantitySeriesData.isNotEmpty()) {
            Logger.logSQL("Quantity sample has data series: ${sample.quantitySeriesData}", 0)
            val seriesMetadata = ContentValues().apply {
                put(HealthSyncSecureContract.QuantitySeries.DATA_ID, id)
                put(HealthSyncSecureContract.QuantitySeries.COUNT, sample.quantitySeriesData.size)
            }
            val seriesId = secure.writableDatabase.insert(HealthSyncSecureContract.QUANTITY_SERIES, null, seriesMetadata)

            sample.quantitySeriesData.forEachIndexed { i, datum ->
                val values = ContentValues().apply {
                    put(HealthSyncSecureContract.QuantitySeriesData.SERIES_ID, seriesId)
                    put(HealthSyncSecureContract.QuantitySeriesData.DATUM_ID, i)
                    put(HealthSyncSecureContract.QuantitySeriesData.START_DATE, datum.startDate.toAppleTimestamp())
                    put(HealthSyncSecureContract.QuantitySeriesData.END_DATE, datum.endDate.toAppleTimestamp())
                    put(HealthSyncSecureContract.QuantitySeriesData.VALUE, datum.value)
                }
                secure.writableDatabase.insert(HealthSyncSecureContract.QUANTITY_SERIES_DATA, null, values)
            }
        }

        // unhandled:
        //sample.final
        //sample.frozen
    }

    private fun insertSample(id: Int, sample: Sample) {
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.Samples.DATA_ID, id)
            put(HealthSyncSecureContract.Samples.START_DATE, sample.startDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Samples.END_DATE, sample.endDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Samples.DATA_TYPE, sample.dataType)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.SAMPLES, null, values)
        Logger.logSQL("Inserting sample: $sample", 1)
    }

    private fun insertObject(obj: HealthObject, provenanceId: Int): Int {
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.Objects.UUID, Utils.uuidToBytes(obj.uuid))
            put(HealthSyncSecureContract.Objects.CREATION_DATE, obj.creationDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Objects.TYPE, 1) // i think 1 just means not deleted? is not included in sent data
            put(HealthSyncSecureContract.Objects.PROVENANCE, provenanceId)
        }

        val objId = secure.writableDatabase.insert(HealthSyncSecureContract.OBJECTS, null, values).toInt()

        Logger.logSQL("Inserting health object: $obj", 2)

        if(obj.metadataDictionary != null) {
            obj.metadataDictionary.entries.forEach {
                val keyId = getOrInsertMetadataKey(it.key)
                val numericalValue = when {
                    it.numberDoubleValue != null -> it.numberDoubleValue
                    it.numberIntValue != null -> it.numberIntValue.toDouble()
                    it.quantityValue != null -> it.quantityValue.value
                    else -> null
                }
                // from SQL statement in HealthDaemon: UPDATE protected.metadata_values SET value_type = (CASE WHEN protected.metadata_values.string_value IS NOT NULL THEN 0 WHEN protected.metadata_values.numerical_value IS NOT NULL THEN 1 WHEN protected.metadata_values.date_value IS NOT NULL THEN 2 END);
                val valueType = when {
                    it.stringValue != null -> 0
                    numericalValue != null -> 1
                    it.dateValue != null -> 2
                    else -> throw Exception("Unsupported metadata value $it")
                }
                val v = ContentValues().apply {
                    put(HealthSyncSecureContract.MetadataValues.KEY_ID, keyId)
                    put(HealthSyncSecureContract.MetadataValues.OBJECT_ID, objId)
                    put(HealthSyncSecureContract.MetadataValues.STRING_VALUE, it.stringValue)
                    put(HealthSyncSecureContract.MetadataValues.NUMERICAL_VALUE, numericalValue)
                    put(HealthSyncSecureContract.MetadataValues.DATE_VALUE, it.dateValue?.toAppleTimestamp())
                    put(HealthSyncSecureContract.MetadataValues.DATA_VALUE, it.dataValue?.render())
                    put(HealthSyncSecureContract.MetadataValues.VALUE_TYPE, valueType)
                }
                secure.writableDatabase.insert(HealthSyncSecureContract.METADATA_VALUES, null, v)
                Logger.logSQL("Inserting health object metadata: $it", 2)
            }
        }

        return objId
    }

    fun getOrInsertProvenance(provenance: Provenance, source: Source?): Int {

        val sourceId = if(source == null) null else insertSourceIfNotPresent(source)

        // assuming the desired provenance is already in the DB, select its ID
        val db = secure.readableDatabase
        val projection = arrayOf(HealthSyncSecureContract.DataProvenances.ROW_ID)

        // note: we SHOULD also filter for device UUID and source UUID but it's not immediately
        // obvious where those are stored in the DB (likely in the non-secure one?) so we'll make do
        // with what we have direct access to
        val dp = HealthSyncSecureContract.DataProvenances
        val selection = "${dp.ORIGIN_PRODUCT_TYPE} = ? AND ${dp.ORIGIN_BUILD} = ? AND ${dp.ORIGIN_MAJOR_VERSION} = ? AND ${dp.ORIGIN_MINOR_VERSION} = ? AND ${dp.ORIGIN_PATCH_VERSION} = ? AND ${dp.SOURCE_VERSION} = ? AND ${dp.TZ_NAME} = ?"
        val selectionArgs = arrayOf(
            provenance.originProductType,
            provenance.originBuild,
            provenance.originMajorVersion.toString(),
            provenance.originMinorVersion.toString(),
            provenance.originPatchVersion.toString(),
            provenance.sourceVersion,
            provenance.timeZoneName,
        )

        val cursor = db.query(
            HealthSyncSecureContract.DATA_PROVENANCES, // table to query
            projection, // select ROWID only
            selection,  // WHERE these columns match...
            selectionArgs, // ... with these values
            null,
            null,
            null
        )

        return when(cursor.count) {
            0 -> {
                cursor.close()
                insertProvenance(provenance, sourceId)
                getOrInsertProvenance(provenance, source)
            }
            1 -> {
                cursor.moveToFirst()
                val id = cursor.getInt(0)
                cursor.close()
                id
            }
            else -> {
                cursor.close()
                throw Exception("Got multiple provenance ROWIDs for provenance info that should be unique ($provenance)")
            }
        }
    }

    private fun insertProvenance(provenance: Provenance, sourceId: Int?) {
        val dp = HealthSyncSecureContract.DataProvenances
        val values = ContentValues().apply {
            put(dp.ORIGIN_PRODUCT_TYPE, provenance.originProductType)
            put(dp.ORIGIN_BUILD, provenance.originBuild)
            put(dp.ORIGIN_MAJOR_VERSION, provenance.originMajorVersion)
            put(dp.ORIGIN_MINOR_VERSION, provenance.originMinorVersion)
            put(dp.ORIGIN_PATCH_VERSION, provenance.originPatchVersion)
            put(dp.SOURCE_VERSION, provenance.sourceVersion)
            put(dp.TZ_NAME, provenance.timeZoneName)

            put(dp.LOCAL_PRODUCT_TYPE, "WatchWitch")
            put(dp.LOCAL_BUILD, "v0.0.1")

            put(dp.SYNC_PROVENANCE, 0)
            put(dp.CONTRIBUTOR_ID, 0)
            put(dp.DERIVED_FLAGS, 0)
            put(dp.DEVICE_ID, 0)
            put(dp.SOURCE_ID, sourceId)
        }
        secure.writableDatabase.insert(HealthSyncSecureContract.DATA_PROVENANCES, null, values)
        Logger.logSQL("Inserting provenance: $provenance", 2)
    }

    private fun insertSourceIfNotPresent(source: Source): Int {
        val db = regular.readableDatabase

        // android sqlite API does not allow selecting by blob, so we'll do it manually
        val uuidHex = Utils.uuidToBytes(source.uuid).hex()
        val query = "SELECT ${HealthSyncContract.Sources.ROWID} FROM ${HealthSyncContract.SOURCES} WHERE ${HealthSyncContract.Sources.UUID} = X'$uuidHex';"
        val cursor = db.rawQuery(query, null);

        return when(cursor.count) {
            0 -> {
                cursor.close()
                insertSource(source)
            }
            1 -> {
                cursor.moveToFirst()
                val id = cursor.getInt(0)
                cursor.close()
                id
            }
            else -> {
                cursor.close()
                throw Exception("Got multiple source ROWIDs for source info that should be unique ($source)")
            }
        }
    }

    private fun insertSource(source: Source): Int {
        val values = ContentValues().apply {
            put(HealthSyncContract.Sources.UUID, Utils.uuidToBytes(source.uuid))
            put(HealthSyncContract.Sources.BUNDLE_ID, source.bundleIdentifier)
            put(HealthSyncContract.Sources.SOURCE_OPTIONS, source.options)
            put(HealthSyncContract.Sources.DELETED, source.deleted)
            // not sure what sync anchors mean here, we'll just set it to zero (they are unique in the original DB but we drop that constraint)
            put(HealthSyncContract.Sources.SYNC_ANCHOR, 0)
            put(HealthSyncContract.Sources.NAME, source.name)
            put(HealthSyncContract.Sources.LOCAL_DEVICE, 0) // local_device is a boolean, we consider all sources remote
            put(HealthSyncContract.Sources.MOD_DATE, source.modificationDate?.toAppleTimestamp())
            put(HealthSyncContract.Sources.OWNER_BUNDLE_ID, source.owningAppBundleIdentifier)
            put(HealthSyncContract.Sources.PRODUCT_TYPE, source.productType)
            put(HealthSyncContract.Sources.PROVENANCE, 0) // i'm not sure how these provenance references work
        }

        Logger.logSQL("Inserting source: $source", 2)

        return regular.writableDatabase.insert(HealthSyncContract.SOURCES, null, values).toInt()
    }

    private fun getOrInsertUnitString(unit: String): Int {
        // assuming the desired unit is already in the DB, select its ID
        val db = secure.readableDatabase
        val projection = arrayOf(HealthSyncSecureContract.UnitStrings.ROW_ID)

        val selection = "${HealthSyncSecureContract.UnitStrings.UNIT_STRING} = ?"
        val selectionArgs = arrayOf(unit)

        val cursor = db.query(
            HealthSyncSecureContract.UNIT_STRINGS, // table to query
            projection, // select ROWID only
            selection,  // WHERE these columns match...
            selectionArgs, // ... with these values
            null,
            null,
            null
        )

        return when(cursor.count) {
            0 -> {
                cursor.close()
                val values = ContentValues().apply {
                    put(HealthSyncSecureContract.UnitStrings.UNIT_STRING, unit)
                }
                secure.writableDatabase.insert(HealthSyncSecureContract.UNIT_STRINGS, null, values).toInt()
            }
            1 -> {
                cursor.moveToFirst()
                val id = cursor.getInt(0)
                cursor.close()
                id
            }
            else -> {
                cursor.close()
                throw Exception("Got multiple unit string ROWIDs for unit info that should be unique ($unit)")
            }
        }
    }

    private fun getOrInsertMetadataKey(key: String): Int {
        // assuming the desired key is already in the DB, select its ID
        val db = secure.readableDatabase

        val cursor = db.query(
            HealthSyncSecureContract.METADATA_KEYS, // table to query
            arrayOf(HealthSyncSecureContract.MetadataKeys.ROW_ID), // select ROWID only
            "${HealthSyncSecureContract.MetadataKeys.KEY} = ?",  // WHERE these columns match...
            arrayOf(key), // ... with these values
            null,
            null,
            null
        )

        return when(cursor.count) {
            0 -> {
                cursor.close()
                val values = ContentValues().apply {
                    put(HealthSyncSecureContract.MetadataKeys.KEY, key)
                }
                secure.writableDatabase.insert(HealthSyncSecureContract.METADATA_KEYS, null, values).toInt()
            }
            1 -> {
                cursor.moveToFirst()
                val id = cursor.getInt(0)
                cursor.close()
                id
            }
            else -> {
                cursor.close()
                throw Exception("Got multiple unit string ROWIDs for metadata key that should be unique ($key)")
            }
        }
    }

    fun setKeyValueSecure(domain: String?, category: Int, kvp: TimestampedKeyValuePair) {
        val initialValues = ContentValues().apply {
            put(HealthSyncSecureContract.KeyValueSecure.DOMAIN, domain ?: "")
            put(HealthSyncSecureContract.KeyValueSecure.CATEGORY, category)
            put(HealthSyncSecureContract.KeyValueSecure.KEY, kvp.key)
            put(HealthSyncSecureContract.KeyValueSecure.MOD_DATE, kvp.timestamp.toAppleTimestamp())
            put(HealthSyncSecureContract.KeyValueSecure.PROVENANCE, 0)

            // value column is untyped in sqlite
            when {
                kvp.numberDoubleValue != null -> put(HealthSyncSecureContract.KeyValueSecure.VALUE, kvp.numberDoubleValue)
                kvp.numberIntValue != null -> put(HealthSyncSecureContract.KeyValueSecure.VALUE, kvp.numberIntValue)
                kvp.stringValue != null -> put(HealthSyncSecureContract.KeyValueSecure.VALUE, kvp.stringValue)
                kvp.byteValue != null -> put(HealthSyncSecureContract.KeyValueSecure.VALUE, kvp.byteValue)
            }
        }

        // insert or update on conflict
        if (secure.writableDatabase.insertWithOnConflict(HealthSyncSecureContract.KEY_VALUE_SECURE, null, initialValues, SQLiteDatabase.CONFLICT_IGNORE).toInt() == -1) {
            secure.writableDatabase.update(
                HealthSyncSecureContract.KEY_VALUE_SECURE,
                initialValues,
                "${HealthSyncSecureContract.KeyValueSecure.DOMAIN}=? AND ${HealthSyncSecureContract.KeyValueSecure.CATEGORY}=? AND ${HealthSyncSecureContract.KeyValueSecure.KEY}=?",
                arrayOf(domain ?: "", category.toString(), kvp.key)
            )
            Logger.logSQL("Updated KeyValueSecure: $domain-$category: $kvp", 0)
        }
        else
            Logger.logSQL("Inserted KeyValueSecure: $domain-$category: $kvp", 0)
    }

    fun setKeyValue(domain: String?, category: Int, kvp: TimestampedKeyValuePair) {
        val initialValues = ContentValues().apply {
            put(HealthSyncContract.KeyValue.DOMAIN, domain ?: "")
            put(HealthSyncContract.KeyValue.CATEGORY, category)
            put(HealthSyncContract.KeyValue.KEY, kvp.key)
            put(HealthSyncContract.KeyValue.MOD_DATE, kvp.timestamp.toAppleTimestamp())
            put(HealthSyncContract.KeyValue.PROVENANCE, 0)

            // value column is untyped in sqlite
            when {
                kvp.numberDoubleValue != null -> put(HealthSyncContract.KeyValue.VALUE, kvp.numberDoubleValue)
                kvp.numberIntValue != null -> put(HealthSyncContract.KeyValue.VALUE, kvp.numberIntValue)
                kvp.stringValue != null -> put(HealthSyncContract.KeyValue.VALUE, kvp.stringValue)
                kvp.byteValue != null -> put(HealthSyncContract.KeyValue.VALUE, kvp.byteValue)
            }
        }

        // insert or update on conflict
        if (regular.writableDatabase.insertWithOnConflict(HealthSyncContract.KEY_VALUE, null, initialValues, SQLiteDatabase.CONFLICT_IGNORE).toInt() == -1) {
            regular.writableDatabase.update(
                HealthSyncContract.KEY_VALUE,
                initialValues,
                "${HealthSyncContract.KeyValue.DOMAIN}=? AND ${HealthSyncContract.KeyValue.CATEGORY}=? AND ${HealthSyncContract.KeyValue.KEY}=?",
                arrayOf(domain ?: "", category.toString(), kvp.key)
            )
            Logger.logSQL("Updated KeyValue: $domain-$category: $kvp", 0)
        }
        else
            Logger.logSQL("Inserted KeyValue: $domain-$category: $kvp", 0)
    }

    fun markSampleDeleted(uuid: UUID) {
        Logger.logSQL("Marking sample with uuid $uuid as deleted", 0)
        val objs = HealthSyncSecureContract.OBJECTS
        val uuidField = HealthSyncSecureContract.Objects.UUID
        val type = HealthSyncSecureContract.Objects.TYPE
        secure.writableDatabase.execSQL("UPDATE $objs SET $type = 2 WHERE $uuidField = x'${Utils.uuidToBytes(uuid).hex()}';")
    }

    fun setSyncAnchor(schema: String, objType: Int, identifier: Int, value: Int, remote: Boolean) {
        val remoteValue = if(remote) 1 else 0

        val initialValues = ContentValues().apply {
            put(HealthSyncContract.SimpleSyncAnchors.EPOCH, 0)
            put(HealthSyncContract.SimpleSyncAnchors.SCHEMA, schema)
            put(HealthSyncContract.SimpleSyncAnchors.TYPE, objType)
            put(HealthSyncContract.SimpleSyncAnchors.STORE, identifier)
            put(HealthSyncContract.SimpleSyncAnchors.REMOTE, remote)
            put(HealthSyncContract.SimpleSyncAnchors.NEXT, value)
        }

        if (regular.writableDatabase.insertWithOnConflict(HealthSyncContract.SIMPLE_SYNC_ANCHORS, null, initialValues, SQLiteDatabase.CONFLICT_IGNORE).toInt() == -1) {
            regular.writableDatabase.update(
                HealthSyncContract.SIMPLE_SYNC_ANCHORS,
                initialValues,
                "${HealthSyncContract.SimpleSyncAnchors.EPOCH}=? AND ${HealthSyncContract.SimpleSyncAnchors.SCHEMA}=? AND ${HealthSyncContract.SimpleSyncAnchors.TYPE}=? AND ${HealthSyncContract.SimpleSyncAnchors.STORE}=? AND ${HealthSyncContract.SimpleSyncAnchors.REMOTE}=?",
                arrayOf("0", schema, objType.toString(), identifier.toString(), remoteValue.toString())
            )
        }

        Logger.logSQL("Set SyncAnchor: $schema-$objType-$identifier: $value", 2)
    }

    fun resetSyncStatus() {
        regular.writableDatabase.execSQL("DELETE FROM ${HealthSyncContract.SIMPLE_SYNC_ANCHORS};")
    }

    fun loadSyncAnchors(): Map<SyncStatusKey,Int> {
        val projection = arrayOf(
            HealthSyncContract.SimpleSyncAnchors.SCHEMA,
            HealthSyncContract.SimpleSyncAnchors.TYPE,
            HealthSyncContract.SimpleSyncAnchors.STORE,
            HealthSyncContract.SimpleSyncAnchors.NEXT,
            HealthSyncContract.SimpleSyncAnchors.REMOTE,
        )

        val map = mutableMapOf<SyncStatusKey,Int>()
        val cursor = regular.readableDatabase.query(HealthSyncContract.SIMPLE_SYNC_ANCHORS, projection, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val key = SyncStatusKey(cursor.getInt(2), cursor.getString(0), cursor.getInt(1), cursor.getInt(4) == 1)
            map[key] = cursor.getInt(3)
        }
        cursor.close()

        return map
    }

    fun getCategorySamples() : List<DisplaySample> {
        val cat = HealthSyncSecureContract.CATEGORY_SAMPLES
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val list = mutableListOf<DisplaySample>()
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date, data_type, value FROM $cat INNER JOIN $sam ON $cat.data_id=$sam.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2;", null)
        while (cursor.moveToNext()) {
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))
            list.add(DisplaySample(start, end, CategorySample.categoryTypeToString(cursor.getInt(2)), cursor.getInt(3).toDouble(), ""))
        }
        cursor.close()
        return list
    }

    fun getQuantitySamples() : List<DisplaySample> {
        val qua = HealthSyncSecureContract.QUANTITY_SAMPLES
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val units = HealthSyncSecureContract.UNIT_STRINGS
        val stats = HealthSyncSecureContract.QUANTITY_SAMPLE_STATISTICS
        val series = HealthSyncSecureContract.QUANTITY_SERIES
        val list = mutableListOf<DisplaySample>()
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date, data_type, quantity, original_quantity, unit_string, min, max, $objs.data_id, count, series_id FROM $qua INNER JOIN $sam ON $qua.data_id=$sam.data_id LEFT JOIN $units ON $units.ROWID=$qua.original_unit LEFT JOIN $stats ON $stats.owner_id=$qua.data_id LEFT JOIN $series ON $series.data_id=$qua.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2;", null)
        while (cursor.moveToNext()) {
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))

            val type = QuantitySample.quantityTypeToString(cursor.getInt(2))
            var quantity = cursor.getDouble(3)
            var unit = when(type) {
                "BasalEnergyBurned", "ActiveEnergyBurned" -> "kcal"
                else -> ""
            }

            if(!cursor.isNull(4)) {
                quantity = cursor.getDouble(4)
                unit = cursor.getString(5)
            }

            val min = if(cursor.isNull(6)) null else cursor.getDouble(6)
            val max = if(cursor.isNull(7)) null else cursor.getDouble(7)

            val metadata = getMetadataOfSample(cursor.getInt(8))

            val count = if(cursor.isNull(9)) null else cursor.getInt(9)
            val seriesId = if(cursor.isNull(10)) null else cursor.getInt(10)

            // load associated data series
            val seriesData = if(count != null && seriesId != null) {
                val projection = arrayOf(HealthSyncSecureContract.QuantitySeriesData.DATUM_ID, HealthSyncSecureContract.QuantitySeriesData.START_DATE, HealthSyncSecureContract.QuantitySeriesData.END_DATE, HealthSyncSecureContract.QuantitySeriesData.VALUE)
                val selection = "${HealthSyncSecureContract.QuantitySeriesData.SERIES_ID} = ?"
                val selectionArgs = arrayOf(seriesId.toString())
                val seriesCursor = secure.readableDatabase.query(HealthSyncSecureContract.QUANTITY_SERIES_DATA, projection, selection, selectionArgs, null, null, "${HealthSyncSecureContract.QuantitySeriesData.DATUM_ID} ASC")
                val data = mutableListOf<Triple<Date, Date, Double>>()
                while (seriesCursor.moveToNext()) {
                    val datumId = seriesCursor.getInt(0)
                    val datumStart = Utils.dateFromAppleTimestamp(seriesCursor.getDouble(1))
                    val datumEnd = Utils.dateFromAppleTimestamp(seriesCursor.getDouble(2))
                    val datumValue = seriesCursor.getDouble(3)
                    data.add(Triple(datumStart, datumEnd, datumValue))
                }
                seriesCursor.close()
                data
            }
            else
                listOf()

            list.add(DisplaySample(start, end, type, quantity, unit, min, max, metadata, seriesData))
        }
        cursor.close()
        return list
    }

    fun getWorkouts() : List<DisplayWorkout> {
        val wor = HealthSyncSecureContract.WORKOUTS
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val list = mutableListOf<DisplayWorkout>()
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date, activity_type, duration, total_energy_burned, total_distance, total_w_steps, $objs.data_id FROM $wor INNER JOIN $sam ON $wor.data_id=$sam.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2;", null)
        while (cursor.moveToNext()) {
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))

            val type = Workout.typeToString(cursor.getInt(2))
            val duration = cursor.getDouble(3)
            val energy = cursor.getDouble(4)
            val distance = cursor.getDouble(5)
            val steps = cursor.getDouble(6)
            val metadata = getMetadataOfSample(cursor.getInt(7))

            list.add(DisplayWorkout(start, end, type, duration, energy, distance, steps, metadata))
        }
        cursor.close()
        return list
    }

    fun getEcgSamples() : List<DisplayEcg> {
        val ecg = HealthSyncSecureContract.ECG_SAMPLES
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val list = mutableListOf<DisplayEcg>()
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date, average_heart_rate, private_classification, voltage_payload, $objs.data_id FROM $ecg INNER JOIN $sam ON $ecg.data_id=$sam.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2;", null)
        while (cursor.moveToNext()) {
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))
            val heartrate = cursor.getDouble(2)
            val classification = cursor.getInt(3)
            val voltageBlob = cursor.getBlob(4)
            val metadata = getMetadataOfSample(cursor.getInt(5))

            val buf = ByteBuffer.wrap(voltageBlob)
            val data = mutableListOf<Float>()
            while(buf.hasRemaining())
                data.add(buf.float)

            list.add(DisplayEcg(start, end, heartrate, classification, data, metadata))
        }
        cursor.close()
        return list
    }

    fun getLocationSeries() : List<DisplayLocationSeries> {
        val loc = HealthSyncSecureContract.LOCATION_SERIES
        val pts = HealthSyncSecureContract.LOCATION_SERIES_DATA
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val list = mutableListOf<DisplayLocationSeries>()
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date, series_id FROM $loc INNER JOIN $sam ON $loc.data_id=$sam.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2;", null)
        while (cursor.moveToNext()) {
            val seriesId = cursor.getInt(2)
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))

            val points = mutableListOf<Pair<Double,Double>>()
            val pointCursor = secure.readableDatabase.rawQuery("SELECT latitude, longitude FROM $pts WHERE series_id = ? ORDER BY datum_id ASC;", arrayOf(seriesId.toString()))
            while(pointCursor.moveToNext()) {
                points.add(Pair(pointCursor.getDouble(0), pointCursor.getDouble(1)))
            }
            pointCursor.close()

            list.add(DisplayLocationSeries(start, end, seriesId, points))
        }
        cursor.close()
        return list
    }

    fun getLocationSeries(id: Int) : DisplayLocationSeries? {
        val loc = HealthSyncSecureContract.LOCATION_SERIES
        val pts = HealthSyncSecureContract.LOCATION_SERIES_DATA
        val sam = HealthSyncSecureContract.SAMPLES
        val objs = HealthSyncSecureContract.OBJECTS
        val cursor = secure.readableDatabase.rawQuery("SELECT start_date, end_date FROM $loc INNER JOIN $sam ON $loc.data_id=$sam.data_id INNER JOIN $objs ON $sam.data_id=$objs.data_id WHERE $objs.type != 2 AND series_id = ?;", arrayOf(id.toString()))

        if(cursor.count == 0) {
            cursor.close()
            return null
        }
        else {
            cursor.moveToFirst()
            val start = Utils.dateFromAppleTimestamp(cursor.getDouble(0))
            val end = Utils.dateFromAppleTimestamp(cursor.getDouble(1))

            val points = mutableListOf<Pair<Double,Double>>()
            val pointCursor = secure.readableDatabase.rawQuery("SELECT latitude, longitude FROM $pts WHERE series_id = ? ORDER BY datum_id ASC;", arrayOf(id.toString()))
            while(pointCursor.moveToNext()) {
                points.add(Pair(pointCursor.getDouble(0), pointCursor.getDouble(1)))
            }
            pointCursor.close()
            cursor.close()
            return DisplayLocationSeries(start, end, id, points)
        }
    }

    private fun getMetadataOfSample(id: Int): Map<String, String> {
        val meta = HealthSyncSecureContract.METADATA_VALUES
        val keys = HealthSyncSecureContract.METADATA_KEYS
        val cursor = secure.readableDatabase.rawQuery("SELECT key, value_type, string_value, numerical_value, date_value, data_value FROM $meta INNER JOIN $keys ON $meta.key_id=$keys.ROWID WHERE object_id = ?", arrayOf(id.toString()))
        val results = mutableMapOf<String,String>()
        while (cursor.moveToNext()) {
            val key = cursor.getString(0)
            val type = cursor.getInt(1)

            val value = when(type) {
                0 -> cursor.getString(2)
                2 -> Utils.dateFromAppleTimestamp(cursor.getDouble(4)).toString()
                else -> cursor.getDouble(3).toString()
            }
            results[key] = value
        }
        cursor.close()
        return results
    }

    abstract class HealthLogDisplayItem {
        abstract val startDate: Date
        abstract val endDate: Date
    }

    data class DisplaySample(
        override val startDate: Date,
        override val endDate: Date,
        val dataType: String,
        val value: Double,
        val unit: String,
        val min: Double? = null,
        val max: Double? = null,
        val metadata: Map<String, String> = mapOf(),
        val series: List<Triple<Date,Date,Double>> = listOf()
    ) : HealthLogDisplayItem()

    data class DisplayLocationSeries(
        override val startDate: Date,
        override val endDate: Date,
        val seriesId: Int,
        val points: List<Pair<Double, Double>>
    ) : HealthLogDisplayItem()

    data class DisplayWorkout(
        override val startDate: Date,
        override val endDate: Date,
        val type: String,
        val duration: Double,
        val energy: Double,
        val distance: Double,
        val steps: Double,
        val metadata: Map<String, String> = mapOf()
    ) : HealthLogDisplayItem()

    data class DisplayEcg(
        override val startDate: Date,
        override val endDate: Date,
        val heartrate: Double,
        val classification: Int,
        val voltageSamples: List<Float>,
        val metadata: Map<String, String> = mapOf()
    ) : HealthLogDisplayItem()
}