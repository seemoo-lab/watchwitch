package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.PBParsable
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoValue
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.toAppleTimestamp
import java.util.Date
import java.util.UUID

class NanoSyncMessage(
    val version: Int?,
    val persistentPairingUUID: UUID,
    val healthPairingUUID: UUID,
    val status: NanoSyncStatus?,
    val changeSet: NanoSyncChangeSet?,
    val activationRestore: NanoSyncActivationRestore?
) {
    override fun toString(): String {
        return "NanoSyncMessage(v$version, $status, actrst $activationRestore, changeset $changeSet)"
    }

    companion object : PBParsable<NanoSyncMessage>() {
        // based on _HDCodableNanoSyncMessageReadFrom in HealthDaemon binary
        override fun fromSafePB(pb: ProtoBuf): NanoSyncMessage {
            val version = pb.readOptShortVarInt(2)
            val persistentUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(3) as ProtoLen).value)
            val healthUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(4) as ProtoLen).value)

            val changeSet = NanoSyncChangeSet.fromPB(pb.readOptionalSinglet(7) as ProtoBuf?)

            val status = NanoSyncStatus.fromPB(pb.readOptionalSinglet(8) as ProtoBuf?)
            val activationRestore =
                NanoSyncActivationRestore.fromPB(pb.readOptionalSinglet(9) as ProtoBuf?)

            return NanoSyncMessage(version, persistentUUID, healthUUID, status, changeSet, activationRestore)
        }
    }

    fun renderReply() = "0200".fromHex() + renderProtobuf()
    fun renderRequest() = "020000".fromHex() + renderProtobuf()

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(version != null)
            fields[2] = listOf(ProtoVarInt(version))
        fields[3] = listOf(ProtoLen(Utils.uuidToBytes(persistentPairingUUID)))
        fields[4] = listOf(ProtoLen(Utils.uuidToBytes(healthPairingUUID)))

        if(changeSet != null)
            fields[7] = listOf(ProtoLen(changeSet.renderProtobuf()))
        if(status != null)
            fields[8] = listOf(ProtoLen(status.renderProtobuf()))
        if(activationRestore != null)
            throw Exception("rendering activation restore not yet supported")
            //fields[9] = listOf(ProtoString(localizedDescription))
        return ProtoBuf(fields).renderStandalone()
    }
}

class NanoSyncChangeSet(
    val sessionUUID: UUID,
    val sessionStart: Date?,
    val statusCode: Int?,
    val changes: List<NanoSyncChange>,
    val error: NanoSyncError?
) {
    companion object : PBParsable<NanoSyncChangeSet>() {
        // based on _HDCodableNanoSyncChangeSetReadFrom in HealthDaemon binary
        override fun fromSafePB(pb: ProtoBuf): NanoSyncChangeSet {
            val changes = pb.readMulti(1).map { NanoSyncChange.fromSafePB(it as ProtoBuf) }
            val sessionUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(2) as ProtoLen).value)
            val sessionStartDate = (pb.readOptionalSinglet(3) as ProtoI64?)?.asDate()
            val error = NanoSyncError.fromPB(pb.readOptionalSinglet(4) as ProtoBuf?)
            val statusCode = pb.readOptShortVarInt(5)

            return NanoSyncChangeSet(sessionUUID, sessionStartDate, statusCode, changes, error)
        }

        fun statusCodeAsString(statusCode: Int?): String {
            return when(statusCode) {
                null -> "null"
                1 -> "Continue"
                2 -> "Finished"
                3 -> "Error"
                else -> "Unknown"
            }
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        fields[1] = changes.map { ProtoLen(it.renderProtobuf()) }
        fields[2] = listOf(ProtoLen(Utils.uuidToBytes(sessionUUID)))

        if(sessionStart != null)
            fields[3] = listOf(ProtoI64(sessionStart.toAppleTimestamp()))

        if(error != null)
            fields[4] = listOf(ProtoLen(error.renderProtobuf()))

        if(statusCode != null)
            fields[5] = listOf(ProtoVarInt(statusCode))

        return ProtoBuf(fields).renderStandalone()
    }

    val statusString: String
        get() = statusCodeAsString(statusCode)

    override fun toString(): String {
        return "ChangeSet(uuid $sessionUUID, start $sessionStart, status $statusString, error $error, changes: $changes)"
    }

    enum class Status(val code: Int) {
        CONTINUE(1), FINISHED(2), ERROR(3)
    }
}

class NanoSyncChange(
    val objectType: Int?,
    val startAnchor: Int,
    val endAnchor: Int,
    val objectData: List<NanoSyncEntity>,
    val syncAnchors: List<NanoSyncAnchor>,
    val speculative: Boolean?,
    val sequence: Int?,
    val complete: Boolean?,
    val entityIdentifier: EntityIdentifier
) {
    companion object : PBParsable<NanoSyncChange>() {
        // based on _HDCodableNanoSyncChangeReadFrom in HealthDaemon binary
        override fun fromSafePB(pb: ProtoBuf): NanoSyncChange {
            val objectType = pb.readOptShortVarInt(1)
            val startAnchor = pb.readShortVarInt(2)
            val endAnchor = pb.readShortVarInt(3)

            val objectData = if(objectType != null) {
                pb.readMulti(4).map { NanoSyncEntity.fromSafePB(it as ProtoBuf, objectType) }
            }
            else
                emptyList()

            val syncAnchors = pb.readMulti(5).map { NanoSyncAnchor.fromSafePB(it as ProtoBuf) }
            val speculative = pb.readOptBool(6)
            val sequence = pb.readOptShortVarInt(7)
            val complete = pb.readOptBool(8)
            val entityIdentifier = EntityIdentifier.fromSafePB(pb.readOptionalSinglet(9) as ProtoBuf)

            return NanoSyncChange(objectType, startAnchor, endAnchor, objectData, syncAnchors, speculative, sequence, complete, entityIdentifier)
        }
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()

        if(objectType != null)
            fields[1] = listOf(ProtoVarInt(objectType))

        fields[2] = listOf(ProtoVarInt(startAnchor))
        fields[3] = listOf(ProtoVarInt(endAnchor))

        fields[4] = objectData.map { ProtoLen(it.renderProtobuf()) }
        fields[5] = syncAnchors.map { ProtoLen(it.renderProtobuf()) }

        if(speculative != null)
            fields[6] = listOf(ProtoVarInt(speculative))
        if(sequence != null)
            fields[7] = listOf(ProtoVarInt(sequence))
        if(complete != null)
            fields[8] = listOf(ProtoVarInt(complete))

        fields[9] = listOf(ProtoLen(entityIdentifier.renderProtobuf()))

        return ProtoBuf(fields).renderStandalone()
    }

    val objectTypeString: String
        get() = if(objectType == null) "unknown(null)" else NanoSyncEntity.objTypeToString(objectType)

    override fun toString(): String {
        return "Change(obj $objectTypeString, seq $sequence, startAnchor $startAnchor, endAnchor $endAnchor, syncAnchors $syncAnchors, spec? $speculative, comp? $complete, entID $entityIdentifier, data: $objectData)"
    }
}

class NanoSyncStatus(
    val syncAnchors: List<NanoSyncAnchor>,
    val statusCode: Int?
) {
    companion object : PBParsable<NanoSyncStatus>() {
        override fun fromSafePB(pb: ProtoBuf): NanoSyncStatus {
            val status = pb.readOptShortVarInt(1)
            val anchors = pb.readMulti(2).map { NanoSyncAnchor.fromSafePB(it as ProtoBuf) }

            return NanoSyncStatus(anchors, status)
        }

        fun statusCodeAsString(statusCode: Int?): String {
            return when(statusCode) {
                null -> "null"
                1 -> "Continue"
                2 -> "Resend"
                3 -> "Reactivate"
                4 -> "ChangesRequired"
                5 -> "LastChanceChangesRequested"
                6 -> "Obliterate"
                else -> "Unknown"
            }
        }
    }

    val statusString: String
        get() = statusCodeAsString(statusCode)

    override fun toString(): String {
        return "SyncStatus(status $statusString, anchors $syncAnchors)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(statusCode != null)
            fields[1] = listOf(ProtoVarInt(statusCode))
        fields[2] = syncAnchors.map { ProtoLen(it.renderProtobuf()) }
        return ProtoBuf(fields).renderStandalone()
    }
}

class NanoSyncError(
    val localizedDescription: String?,
    val code: Int?,
    val domain: String?
) {
    companion object : PBParsable<NanoSyncError>() {
        override fun fromSafePB(pb: ProtoBuf): NanoSyncError {
            val domain = pb.readOptString(1)
            val code = pb.readOptShortVarInt(2)
            val desc = pb.readOptString(3)
            return NanoSyncError(desc, code, domain)
        }
    }

    override fun toString(): String {
        return "SyncError(code $code, domain $domain, desc $localizedDescription)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(domain != null)
            fields[1] = listOf(ProtoString(domain))
        if(code != null)
            fields[2] = listOf(ProtoVarInt(code))
        if(localizedDescription != null)
            fields[3] = listOf(ProtoString(localizedDescription))
        return ProtoBuf(fields).renderStandalone()
    }
}

class NanoSyncAnchor(
    val objectType: Int?,
    val anchor: Long?,
    val entityIdentifier: EntityIdentifier?
) {
    companion object : PBParsable<NanoSyncAnchor>() {
        override fun fromSafePB(pb: ProtoBuf): NanoSyncAnchor {
            val objectType = pb.readOptShortVarInt(1)
            val anchor = pb.readOptLongVarInt(2)
            val entityIdentifier = EntityIdentifier.fromPB(pb.readOptionalSinglet(3) as ProtoBuf?)
            return NanoSyncAnchor(objectType, anchor, entityIdentifier)
        }
    }

    override fun toString(): String {
        return "SyncAnchor(obj $objectType, anchor $anchor, entID $entityIdentifier)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(objectType != null)
            fields[1] = listOf(ProtoVarInt(objectType))
        if(anchor != null)
            fields[2] = listOf(ProtoVarInt(anchor))
        if(entityIdentifier != null)
            fields[3] = listOf(ProtoLen(entityIdentifier.renderProtobuf()))
        return ProtoBuf(fields).renderStandalone()
    }
}

class NanoSyncActivationRestore(
    val sequenceNumber: Int?,
    val statusCode: Int?,
    val defaultSouceBundleIdentifier: String?
) {
    companion object : PBParsable<NanoSyncActivationRestore>() {
        override fun fromSafePB(pb: ProtoBuf): NanoSyncActivationRestore {
            // 1: restore identifier
            val seq = pb.readOptShortVarInt(2)
            val status = pb.readOptShortVarInt(3)
            val bundle = pb.readOptString(4)
            // 6: obliterated health pairing uuids

            return NanoSyncActivationRestore(seq, status, bundle)
        }

        fun statusCodeAsString(statusCode: Int?): String {
            return when(statusCode) {
                null -> "null"
                1 -> "Continue"
                2 -> "Finished"
                3 -> "Abort"
                else -> "Unknown"
            }
        }
    }

    val statusString: String
        get() = statusCodeAsString(statusCode)

    override fun toString(): String {
        return "ActivationRestore(seq $sequenceNumber, status $statusString, bundle $defaultSouceBundleIdentifier)"
    }
}

class EntityIdentifier(
    val identifier: Int,
    val schema: String?
) {
    companion object : PBParsable<EntityIdentifier>() {
        override fun fromSafePB(pb: ProtoBuf): EntityIdentifier {
            val schema = pb.readOptString(1)
            val identifier = pb.readShortVarInt(2)
            return EntityIdentifier(identifier, schema)
        }
    }

    override fun toString(): String {
        return "EntityIdentifier(id $identifier, schema $schema)"
    }

    fun renderProtobuf(): ByteArray {
        val fields = mutableMapOf<Int,List<ProtoValue>>()
        if(schema != null)
            fields[1] = listOf(ProtoString(schema))
        fields[2] = listOf(ProtoVarInt(identifier))
        return ProtoBuf(fields).renderStandalone()
    }
}

class Provenance(
    val originBuild: String?,
    val sourceUUID: UUID,
    val deviceUUID: UUID,
    val sourceVersion: String?,
    val originProductType: String?,
    val timeZoneName: String?,
    val originMajorVersion: Int?,
    val originMinorVersion: Int?,
    val originPatchVersion: Int?
) {
    companion object : PBParsable<Provenance>() {
        override fun fromSafePB(pb: ProtoBuf): Provenance {
            val build = pb.readOptString(1)
            val sourceUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(3) as ProtoLen).value)
            val deviceUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(4) as ProtoLen).value)
            val version = pb.readOptString(5)
            val product = pb.readOptString(6)
            val timeZone = pb.readOptString(7)
            val major = pb.readOptShortVarInt(8)
            val minor = pb.readOptShortVarInt(9)
            val patch = pb.readOptShortVarInt(10)

            return Provenance(build, sourceUUID, deviceUUID, version, product, timeZone, major, minor, patch)
        }
    }

    override fun toString(): String {
        return "Provenance($originProductType version $sourceVersion, build $originBuild, timezone $timeZoneName source $sourceUUID, device $deviceUUID)"
    }
}