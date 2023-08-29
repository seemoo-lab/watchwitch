package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HealthSyncSecureHelper (context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        // create all the tables
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_CATEGORY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_BINARY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_QUANTITY_SAMPLES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_ACTIVITY_CACHES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_OBJECTS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_DATA_PROVENANCES)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_UNIT_STRINGS)

        db.execSQL(HealthSyncSecureContract.SQL_CREATE_METADATA_KEYS)
        db.execSQL(HealthSyncSecureContract.SQL_CREATE_METADATA_VALUES)

        db.execSQL(HealthSyncSecureContract.SQL_CREATE_KEY_VALUE_SECURE)

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
        throw Exception("Database schema upgrade not supported")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "healthdb_secure.db"
    }
}
