package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.provider.BaseColumns

object HealthSyncContract {

    const val SIMPLE_SYNC_ANCHORS = "simple_sync_anchors"
    const val SYNC_ANCHORS = "sync_anchors"
    const val KEY_VALUE = "key_value"
    const val SOURCES = "sources"

    const val SQL_CREATE_SIMPLE_SYNC_ANCHORS = "CREATE TABLE simple_sync_anchors (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, next INTEGER NOT NULL, schema TEXT NOT NULL, type INTEGER NOT NULL, store INTEGER NOT NULL, epoch INTEGER NOT NULL, remote INTEGER NOT NULL, UNIQUE(store, epoch, type, schema, remote));"
    const val SQL_CREATE_SYNC_ANCHORS = "CREATE TABLE sync_anchors (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, next INTEGER NOT NULL, next_updated_date REAL NOT NULL, acked INTEGER NOT NULL, acked_updated_date REAL NOT NULL, frozen INTEGER NOT NULL, frozen_updated_date REAL NOT NULL, received INTEGER NOT NULL, received_updated_date REAL NOT NULL, expected INTEGER NOT NULL, expected_updated_date REAL NOT NULL, schema TEXT NOT NULL, type INTEGER NOT NULL, store INTEGER NOT NULL, epoch INTEGER NOT NULL, UNIQUE(store, epoch, type, schema));"
    const val SQL_CREATE_KEY_VALUE = "CREATE TABLE key_value (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, category INTEGER NOT NULL, domain TEXT NOT NULL, key TEXT NOT NULL, value, provenance INTEGER NOT NULL, mod_date REAL NOT NULL, UNIQUE(category, domain, key));"
    const val SQL_CREATE_SOURCES = "CREATE TABLE sources (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, uuid BLOB UNIQUE NOT NULL, bundle_id TEXT NOT NULL, name TEXT NOT NULL, source_options INTEGER NOT NULL, local_device INTEGER NOT NULL, product_type TEXT NOT NULL, deleted INTEGER NOT NULL, mod_date REAL NOT NULL, provenance INTEGER NOT NULL, sync_anchor INTEGER NOT NULL, owner_bundle_id TEXT);"

    const val SQL_INDEX_SOURCES_BUNDLE = "CREATE INDEX sources_bundle_id_uuid ON sources (bundle_id, uuid);"
    const val SQL_INDEX_SYNC_ANCHORS = "CREATE INDEX simple_sync_anchors_idx ON simple_sync_anchors (store, epoch, type, schema, remote);"

    object SimpleSyncAnchors : BaseColumns {
        const val ROWID = "ROWID"
        const val NEXT = "next"
        const val SCHEMA = "schema"
        const val TYPE = "type"
        const val STORE = "store"
        const val EPOCH = "epoch"
        const val REMOTE = "remote"
    }

    object SyncAnchors : BaseColumns {
        const val ROWID = "ROWID"
        const val NEXT = "next"
        const val NEXT_UPDATED_DATE = "next_updated_date"
        const val ACKED = "acked"
        const val ACKED_UPDATED_DATE = "acked_updated_date"
        const val FROZEN = "frozen"
        const val FROZEN_UPDATED_DATE = "frozen_updated_date"
        const val RECEIVED = "received"
        const val RECEIVED_UPDATED_DATE = "received_updated_date"
        const val EXPECTED = "expected"
        const val EXPECTED_UPDATED_DATE = "expected_updated_date"
        const val SCHEMA = "schema"
        const val TYPE = "type"
        const val STORE = "store"
        const val EPOCH = "epoch"
    }

    object KeyValue : BaseColumns {
        const val ROWID = "ROWID"
        const val CATEGORY = "category"
        const val DOMAIN = "domain"
        const val KEY = "key"
        const val VALUE = "value"
        const val PROVENANCE = "provenance"
        const val MOD_DATE = "mod_date"
    }

    object Sources : BaseColumns {
        const val ROWID = "ROWID"
        const val UUID = "UUID"
        const val BUNDLE_ID = "bundle_id"
        const val NAME = "name"
        const val SOURCE_OPTIONS = "source_options"
        const val LOCAL_DEVICE = "local_device"
        const val PRODUCT_TYPE = "product_type"
        const val DELETED = "deleted"
        const val MOD_DATE = "mod_date"
        const val PROVENANCE = "provenance"
        const val SYNC_ANCHOR = "sync_anchor"
        const val OWNER_BUNDLE_ID = "owner_bundle_id"
    }
}