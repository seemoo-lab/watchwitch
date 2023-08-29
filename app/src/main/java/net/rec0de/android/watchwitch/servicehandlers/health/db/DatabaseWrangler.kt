package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.content.ContentValues
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.servicehandlers.health.HealthObject
import net.rec0de.android.watchwitch.servicehandlers.health.Provenance
import net.rec0de.android.watchwitch.servicehandlers.health.Sample
import net.rec0de.android.watchwitch.toAppleTimestamp

object DatabaseWrangler {

    private lateinit var dbHelper: HealthSyncSecureHelper

    fun initDbHelper(dbh: HealthSyncSecureHelper) {
        dbHelper = dbh
    }

    fun insertCategorySample(value: Int, provenanceId: Int, sample: Sample) {
        val id = insertObject(sample.healthObject!!, provenanceId)
        insertSample(id, sample)
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.CategorySamples.DATA_ID, id)
            put(HealthSyncSecureContract.CategorySamples.VALUE, value)
        }
        dbHelper.writableDatabase.insert(HealthSyncSecureContract.CATEGORY_SAMPLES, null, values)
    }

    fun insertSample(id: Int, sample: Sample) {
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.Samples.DATA_ID, id)
            put(HealthSyncSecureContract.Samples.START_DATE, sample.startDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Samples.END_DATE, sample.endDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Samples.DATA_TYPE, sample.dataType)
        }
        dbHelper.writableDatabase.insert(HealthSyncSecureContract.SAMPLES, null, values)
    }

    private fun insertObject(obj: HealthObject, provenanceId: Int): Int {
        val values = ContentValues().apply {
            put(HealthSyncSecureContract.Objects.UUID, Utils.uuidToBytes(obj.uuid))
            put(HealthSyncSecureContract.Objects.CREATION_DATE, obj.creationDate?.toAppleTimestamp())
            put(HealthSyncSecureContract.Objects.TYPE, 1) // i think 1 just means not deleted? is not included in sent data
            put(HealthSyncSecureContract.Objects.PROVENANCE, provenanceId)
        }
        return dbHelper.writableDatabase.insert(HealthSyncSecureContract.OBJECTS, null, values).toInt()
    }

    fun getOrInsertProvenance(provenance: Provenance): Int {
        // assuming the desired provenance is already in the DB, select its ID
        val db = dbHelper.readableDatabase
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
            provenance.timeZoneName
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
                insertProvenance(provenance)
                getOrInsertProvenance(provenance)
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

    private fun insertProvenance(provenance: Provenance) {
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
            put(dp.SOURCE_ID, 0)
        }
        dbHelper.writableDatabase.insert(HealthSyncSecureContract.DATA_PROVENANCES, null, values)
    }
}