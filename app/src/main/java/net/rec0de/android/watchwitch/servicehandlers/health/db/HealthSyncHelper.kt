package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

class HealthSyncHelper (context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, DatabaseSecretProvider.getOrCreateDatabaseSecret(context).asString(),null, DATABASE_VERSION, 0, null, null, true) {
    override fun onCreate(db: SQLiteDatabase) {
        // create all the tables
        db.execSQL(HealthSyncContract.SQL_CREATE_SOURCES)
        db.execSQL(HealthSyncContract.SQL_CREATE_SYNC_ANCHORS)
        db.execSQL(HealthSyncContract.SQL_CREATE_SIMPLE_SYNC_ANCHORS)
        db.execSQL(HealthSyncContract.SQL_CREATE_KEY_VALUE)

        // create indices
        db.execSQL(HealthSyncContract.SQL_INDEX_SOURCES_BUNDLE)
        db.execSQL(HealthSyncContract.SQL_INDEX_SYNC_ANCHORS)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(newVersion > 1) {
            // remove unique constraint on sync anchor
            db.execSQL("DROP TABLE ${HealthSyncContract.SOURCES};")
            db.execSQL(HealthSyncContract.SQL_CREATE_SOURCES)
            db.execSQL(HealthSyncContract.SQL_INDEX_SOURCES_BUNDLE)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "healthdb.db"
    }
}
