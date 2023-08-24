package net.rec0de.android.watchwitch.servicehandlers.health

data class SyncStatusKey(val identifier: Int, val schema: String?, val objType: Int?) {
    fun toSyncAnchorWithValue(value: Long): NanoSyncAnchor {
        return NanoSyncAnchor(objType, value, EntityIdentifier(identifier, schema))
    }
}