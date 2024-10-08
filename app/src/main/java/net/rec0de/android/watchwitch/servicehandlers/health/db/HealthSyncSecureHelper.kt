package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.content.ContentValues
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

class HealthSyncSecureHelper (context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, DatabaseSecretProvider.getOrCreateDatabaseSecret(context).asString(),null, DATABASE_VERSION, 0, null, null, true) {
    override fun onCreate(db: SQLiteDatabase) {
        // create all the tables
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_CATEGORY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_BINARY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_ECG_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SAMPLE_STATISTICS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_WORKOUTS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_WORKOUT_EVENTS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_ACTIVITY_CACHES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_OBJECTS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_DATA_PROVENANCES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_UNIT_STRINGS)

        db.execSQL(HealthSyncSecureContract.SQL_CREATE_METADATA_KEYS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_METADATA_VALUES)

        db.execSQL(HealthSyncSecureContract.SQL_CREATE_KEY_VALUE_SECURE)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SERIES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SERIES_DATA)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_LOCATION_SERIES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_LOCATION_SERIES_DATA)

        // create indices
        db.execSQL(HealthSyncSecureContract.SQL_INDEX_OBJECTS_DELETED)
        db.execSQL(HealthSyncSecureContract.SQL_INDEX_METADATA_VALUES_OBJECT)
        db.execSQL(HealthSyncSecureContract.SQL_INDEX_SAMPLES_TYPE_DATES)
        db.execSQL(HealthSyncSecureContract.SQL_INDEX_SAMPLES_TYPE_END)
        db.execSQL(HealthSyncSecureContract.SQL_INDEX_SAMPLES_TYPE_ANCHOR)

        // insert unit string associations
        db.insert(HealthSyncSecureContract.UNIT_STRINGS, null, ContentValues().apply {
            put(HealthSyncSecureContract.UnitStrings.UNIT_STRING, "count/min")
        })
        db.insert(HealthSyncSecureContract.UNIT_STRINGS, null, ContentValues().apply {
            put(HealthSyncSecureContract.UnitStrings.UNIT_STRING, "s")
        })
        db.insert(HealthSyncSecureContract.UNIT_STRINGS, null, ContentValues().apply {
            put(HealthSyncSecureContract.UnitStrings.UNIT_STRING, "cm")
        })
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) {
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SAMPLE_STATISTICS)
        }
        if(oldVersion < 3) {
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_WORKOUTS)
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_WORKOUT_EVENTS)
        }
        if(oldVersion < 4) {
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SERIES)
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SERIES_DATA)
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_LOCATION_SERIES)
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_LOCATION_SERIES_DATA)
        }
        if(oldVersion < 5) {
            db.execSQL(HealthSyncSecureContract.SQL_CREATE_ECG_SAMPLES)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 5
        const val DATABASE_NAME = "healthdb_secure.db"
    }
}
