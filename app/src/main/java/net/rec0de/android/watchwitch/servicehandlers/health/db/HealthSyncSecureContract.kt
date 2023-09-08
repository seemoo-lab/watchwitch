package net.rec0de.android.watchwitch.servicehandlers.health.db

import android.provider.BaseColumns

object HealthSyncSecureContract {

    const val SAMPLES = "samples"
    const val BINARY_SAMPLES = "binary_samples"
    const val CATEGORY_SAMPLES = "category_samples"
    const val QUANTITY_SAMPLES = "quantity_samples"
    const val QUANTITY_SAMPLE_STATISTICS = "quantity_sample_statistics"
    const val QUANTITY_SAMPLE_SERIES = "quantity_sample_series"
    const val ACTIVITY_CACHES = "activity_caches"
    const val WORKOUTS = "workouts"
    const val WORKOUT_EVENTS = "workout_events"
    const val OBJECTS = "objects"
    const val METADATA_KEYS = "metadata_keys"
    const val METADATA_VALUES = "metadata_values"
    const val KEY_VALUE_SECURE = "key_value_secure"
    const val UNIT_STRINGS = "unit_strings"
    const val DATA_PROVENANCES = "data_provenances"

    const val SQL_CREATE_SAMPLES = "CREATE TABLE samples (data_id INTEGER PRIMARY KEY, start_date REAL, end_date REAL, data_type INTEGER);"
    const val SQL_CREATE_BINARY_SAMPLES = "CREATE TABLE binary_samples (data_id INTEGER PRIMARY KEY REFERENCES samples (data_id) ON DELETE CASCADE, payload BLOB);"
    const val SQL_CREATE_CATEGORY_SAMPLES = "CREATE TABLE category_samples (data_id INTEGER PRIMARY KEY REFERENCES samples (data_id) ON DELETE CASCADE, value INTEGER);"
    const val SQL_CREATE_QUANTITY_SAMPLES = "CREATE TABLE quantity_samples (data_id INTEGER PRIMARY KEY REFERENCES samples (data_id) ON DELETE CASCADE, quantity REAL, original_quantity REAL, original_unit INTEGER REFERENCES unit_strings (ROWID) ON DELETE NO ACTION);"
    const val SQL_CREATE_QUANTITY_SAMPLE_STATISTICS = "CREATE TABLE quantity_sample_statistics (owner_id INTEGER PRIMARY KEY REFERENCES quantity_samples (data_id) ON DELETE CASCADE, min REAL, max REAL, most_recent REAL, most_recent_date REAL, most_recent_duration REAL);"
    const val SQL_CREATE_QUANTITY_SAMPLE_SERIES = "CREATE TABLE quantity_sample_series (data_id INTEGER PRIMARY KEY REFERENCES samples (data_id) ON DELETE CASCADE, count INTEGER NOT NULL DEFAULT 0, insertion_era INTEGER, hfd_key INTEGER UNIQUE NOT NULL);"
    const val SQL_CREATE_ACTIVITY_CACHES = "CREATE TABLE activity_caches (data_id INTEGER PRIMARY KEY REFERENCES samples (data_id) ON DELETE CASCADE, cache_index INTEGER, sequence INTEGER NOT NULL, activity_mode INTEGER, wheelchair_use INTEGER, energy_burned REAL, energy_burned_goal REAL, energy_burned_goal_date REAL, move_minutes REAL, move_minutes_goal REAL, move_minutes_goal_date REAL, brisk_minutes REAL, brisk_minutes_goal REAL, brisk_minutes_goal_date REAL, active_hours REAL, active_hours_goal REAL, active_hours_goal_date REAL, steps REAL, pushes REAL, walk_distance REAL, deep_breathing_duration REAL, flights INTEGER, energy_burned_stats BLOB, move_minutes_stats BLOB, brisk_minutes_stats BLOB);"
    const val SQL_CREATE_WORKOUTS = "CREATE TABLE workouts (data_id INTEGER PRIMARY KEY, duration REAL, total_energy_burned REAL, total_basal_energy_burned REAL, total_distance REAL, activity_type INTEGER, goal_type INTEGER, goal REAL, total_w_steps REAL, total_flights_climbed REAL, condenser_version INTEGER, condenser_date REAL);"
    const val SQL_CREATE_WORKOUT_EVENTS = "CREATE TABLE workout_events (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL REFERENCES workouts (data_id) ON DELETE CASCADE, date REAL NOT NULL, type INTEGER NOT NULL, duration REAL NOT NULL, metadata BLOB, session_uuid BLOB, error BLOB);"
    const val SQL_CREATE_OBJECTS = "CREATE TABLE objects (data_id INTEGER PRIMARY KEY AUTOINCREMENT, uuid BLOB UNIQUE, provenance INTEGER NOT NULL, type INTEGER, creation_date REAL);"
    const val SQL_CREATE_METADATA_KEYS = "CREATE TABLE metadata_keys (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE);"
    const val SQL_CREATE_METADATA_VALUES = "CREATE TABLE metadata_values (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, key_id INTEGER, object_id INTEGER, value_type INTEGER NOT NULL DEFAULT 0, string_value TEXT, numerical_value REAL, date_value REAL, data_value BLOB);"
    const val SQL_CREATE_KEY_VALUE_SECURE = "CREATE TABLE key_value_secure (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, category INTEGER NOT NULL, domain TEXT NOT NULL, key TEXT NOT NULL, value, provenance INTEGER NOT NULL, mod_date REAL NOT NULL, UNIQUE(category, domain, key));"
    const val SQL_CREATE_UNIT_STRINGS = "CREATE TABLE unit_strings (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, unit_string TEXT UNIQUE);"
    const val SQL_CREATE_DATA_PROVENANCES = "CREATE TABLE data_provenances (ROWID INTEGER PRIMARY KEY AUTOINCREMENT, sync_provenance INTEGER NOT NULL, origin_product_type TEXT NOT NULL, origin_build TEXT NOT NULL, local_product_type TEXT NOT NULL, local_build TEXT NOT NULL, source_id INTEGER NOT NULL, device_id INTEGER NOT NULL, contributor_id INTEGER NOT NULL, source_version TEXT NOT NULL, tz_name TEXT NOT NULL, origin_major_version INTEGER NOT NULL, origin_minor_version INTEGER NOT NULL, origin_patch_version INTEGER NOT NULL, derived_flags INTEGER NOT NULL, UNIQUE(sync_provenance, origin_product_type, origin_build, local_product_type, local_build, source_id, device_id, contributor_id, source_version, tz_name, origin_major_version, origin_minor_version, origin_patch_version));"

    const val SQL_INDEX_OBJECTS_DELETED = "CREATE INDEX objects_deleted ON objects (type) WHERE (objects.type = 2);"
    const val SQL_INDEX_METADATA_VALUES_OBJECT = "CREATE INDEX metadata_values_object ON metadata_values (object_id);"
    const val SQL_INDEX_SAMPLES_TYPE_DATES = "CREATE INDEX samples_type_dates ON samples (data_type, start_date, end_date);"
    const val SQL_INDEX_SAMPLES_TYPE_END = "CREATE INDEX samples_type_end ON samples (data_type, end_date);"
    const val SQL_INDEX_SAMPLES_TYPE_ANCHOR = "CREATE INDEX samples_type_anchor ON samples (data_type, data_id);"

    object Samples : BaseColumns {
        const val DATA_ID = "data_id"
        const val START_DATE = "start_date"
        const val END_DATE = "end_date"
        const val DATA_TYPE = "data_type"
    }

    object BinarySamples : BaseColumns {
        const val DATA_ID = "data_id"
        const val PAYLOAD = "payload"
    }

    object CategorySamples : BaseColumns {
        const val DATA_ID = "data_id"
        const val VALUE = "value"
    }

    object QuantitySamples : BaseColumns {
        const val DATA_ID = "data_id"
        const val QUANTITY = "quantity"
        const val ORIGINAL_QUANTITY = "original_quantity"
        const val ORIGINAL_UNIT = "original_unit"
    }

    object QuantitySampleStatistics : BaseColumns {
        const val DATA_ID = "data_id"
        const val MIN = "min"
        const val MAX = "max"
        const val MOST_RECENT = "most_recent"
        const val MOST_RECENT_DATE = "most_recent_date"
        const val MOST_RECENT_DURATION = "most_recent_duration"
    }

    object QuantitySampleSeries : BaseColumns {
        const val DATA_ID = "data_id"
        const val COUNT = "count"
        const val INSERTION_ERA = "insertion_era"
        const val HFD_KEY = "hfd_key"
    }

    object Workouts : BaseColumns {
        const val DATA_ID = "data_id"
        const val DURATION = "duration"
        const val TOTAL_ENERGY_BURNED = "total_energy_burned"
        const val TOTAL_BASAL_ENERGY_BURNED = "total_basal_energy_burned"
        const val TOTAL_DISTANCE = "total_distance"
        const val ACTIVITY_TYPE = "activity_type"
        const val GOAL_TYPE = "goal_type"
        const val GOAL = "goal"
        const val TOTAL_W_STEPS = "total_w_steps"
        const val TOTAL_FLIGHTS_CLIMBED = "total_flights_climbed"
        const val CONDENSER_VERSION = "condenser_version"
        const val CONDENSER_DATE = "condenser_date"
    }

    object WorkoutEvents : BaseColumns {
        const val ROWID = "ROWID"
        const val OWNER_ID = "owner_id"
        const val DATE = "date"
        const val TYPE = "type"
        const val DURATION = "duration"
        const val METADATA = "metadata"
        const val SESSION_UUID = "session_uuid"
        const val ERROR = "error"
    }

    object ActivityCaches : BaseColumns {
        const val DATA_ID = "data_id"
        const val CACHE_INDEX = "cache_index"
        const val SEQUENCE = "sequence"
        const val ACTIVITY_MODE = "activity_mode"
        const val WHEELCHAIR_USE = "wheelchair_use"
        const val ENERGY_BURNED = "energy_burned"
        const val ENERGY_BURNED_GOAL = "energy_burned_goal"
        const val ENERGY_BURNED_GOAL_DATE = "energy_burned_goal_date"
        const val MOVE_MINUTES = "move_minutes"
        const val MOVE_MINUTES_GOAL = "move_minutes_goal"
        const val MOVE_MINUTES_GOAL_DATE = "move_minutes_goal_date"
        const val BRISK_MINUTES = "brisk_minutes"
        const val BRISK_MINUTES_GOAL = "brisk_minutes_goal"
        const val BRISK_MINUTES_GOAL_DATE = "brisk_minutes_goal_date"
        const val ACTIVE_HOURS = "active_hours"
        const val ACTIVE_HOURS_GOAL = "active_hours_goal"
        const val ACTIVE_HOURS_GOAL_DATE = "active_hours_goal_date"
        const val STEPS = "steps"
        const val PUSHES = "pushes"
        const val WALK_DISTANCE = "walk_distance"
        const val DEEP_BREATHING_DURATION = "deep_breathing_duration"
        const val FLIGHTS = "flights"
        const val ENERGY_BURNED_STATS = "energy_burned_stats"
        const val MOVE_MINUTES_STATS = "move_minutes_stats"
        const val BRISK_MINUTES_STATS = "brisk_minutes_stats"
    }

    object Objects : BaseColumns {
        const val DATA_ID = "data_id"
        const val UUID = "uuid"
        const val PROVENANCE = "provenance"
        const val TYPE = "type"
        const val CREATION_DATE = "creation_date"
    }

    object MetadataKeys : BaseColumns {
        const val ROW_ID = "ROWID"
        const val KEY = "key"
    }

    object MetadataValues : BaseColumns {
        const val ROW_ID = "ROWID"
        const val KEY_ID = "key_id"
        const val OBJECT_ID = "object_id"
        const val VALUE_TYPE = "value_type"
        const val STRING_VALUE = "string_value"
        const val NUMERICAL_VALUE = "numerical_value"
        const val DATE_VALUE = "date_value"
        const val DATA_VALUE = "data_value"
    }

    object KeyValueSecure : BaseColumns {
        const val ROWID = "ROWID"
        const val CATEGORY = "category"
        const val DOMAIN = "domain"
        const val KEY = "key"
        const val VALUE = "value"
        const val PROVENANCE = "provenance"
        const val MOD_DATE = "mod_date"
    }

    object UnitStrings : BaseColumns {
        const val ROW_ID = "ROWID"
        const val UNIT_STRING = "unit_string"
    }

    object DataProvenances : BaseColumns {
        const val ROW_ID = "ROWID"
        const val SYNC_PROVENANCE = "sync_provenance"
        const val ORIGIN_PRODUCT_TYPE = "origin_product_type"
        const val ORIGIN_BUILD = "origin_build"
        const val LOCAL_PRODUCT_TYPE = "local_product_type"
        const val LOCAL_BUILD = "local_build"
        const val SOURCE_ID = "source_id"
        const val DEVICE_ID = "device_id"
        const val CONTRIBUTOR_ID = "contributor_id"
        const val SOURCE_VERSION = "source_version"
        const val TZ_NAME = "tz_name"
        const val ORIGIN_MAJOR_VERSION = "origin_major_version"
        const val ORIGIN_MINOR_VERSION = "origin_minor_version"
        const val ORIGIN_PATCH_VERSION = "origin_patch_version"
        const val DERIVED_FLAGS = "derived_flags"
    }

}