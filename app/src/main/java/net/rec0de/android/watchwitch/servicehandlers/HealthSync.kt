package net.rec0de.android.watchwitch.servicehandlers

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.decoders.aoverc.Decryptor
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.fromBytesLittle
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.utun.DataMessage
import net.rec0de.android.watchwitch.utun.UTunMessage
import java.util.Date
import java.util.UUID

object HealthSync : UTunService {
    override val name = "com.apple.private.alloy.health.sync.classc"

    private val keys = LongTermStorage.getMPKeysForService(name)
    private val decryptor = if(keys != null) Decryptor(keys) else null

    override fun acceptsMessageType(msg: UTunMessage) = msg is DataMessage

    override fun receiveMessage(msg: UTunMessage) {
        msg as DataMessage

        if(!BPListParser.bufferIsBPList(msg.payload))
            throw Exception("HealthSync expected bplist payload but got ${msg.payload.hex()}")

        val parsed = BPListParser().parse(msg.payload)

        if(!Decryptor.isEncryptedMessage(parsed))
            throw Exception("HealthSync expected encrypted AoverC message but got $parsed")

        if(decryptor == null)
            Logger.logUTUN("HealthSync: got encrypted message but no keys to decrypt", 1)
        else {
            val plaintext = decryptor.decrypt(parsed as BPDict) ?: throw Exception("HealthSync decryption failed for $parsed")
            parseSyncMsg(plaintext)
        }
    }

    // based on HDIDSMessageCenter::service:account:incomingData:fromID:context: in HealthDaemon binary
    private fun parseSyncMsg(msg: ByteArray) {
        // first two bytes are message type (referred to message ID in apple sources)
        val type = UInt.fromBytesLittle(msg.sliceArray(0 until 2)).toInt()
        // third byte is priority (0: default, 1: urgent, 2: sync)
        val priority = msg[3].toInt()

        when(type) {
            2 -> parseNanoSyncMessage(msg.fromIndex(3))
        }
    }

    // based on _HDCodableNanoSyncMessageReadFrom in HealthDaemon binary
    private fun parseNanoSyncMessage(bytes: ByteArray): NanoSyncMessage {
        val pb = ProtobufParser().parse(bytes)

        val version = pb.readShortVarInt(2)
        val persistentUUID = Utils.uuidFromBytes((pb.readSingletFieldAsserted(3) as ProtoLen).value)
        val healthUUID = Utils.uuidFromBytes((pb.readSingletFieldAsserted(4) as ProtoLen).value)

        val changeSet = if(pb.value.containsKey(7)) {
            parseNanoChangeSet(pb.value[7]!![0] as ProtoBuf)
        } else null

        val status = NanoSyncStatus.fromPB(pb.readSingletFieldAsserted(8) as ProtoBuf)
        val activationRestore = NanoSyncActivationRestore.fromPB(pb.readSingletFieldAsserted(9) as ProtoBuf)

        return NanoSyncMessage(version, persistentUUID, healthUUID, status, changeSet, activationRestore)
    }

    // based on _HDCodableNanoSyncChangeSetReadFrom in HealthDaemon binary
    private fun parseNanoChangeSet(pb: ProtoBuf): NanoSyncChangeSet {
        val changes = pb.readMulti(1).map { parseNanoChange(it as ProtoBuf) }
        val sessionUUID = Utils.uuidFromBytes((pb.readSingletFieldAsserted(2) as ProtoLen).value)
        val sessionStartDate = (pb.readSingletFieldAsserted(3) as ProtoI64).asDate()
        val error = NanoSyncError.fromPB(pb.readSingletFieldAsserted(4) as ProtoBuf)
        val statusCode = pb.readShortVarInt(5)

        return NanoSyncChangeSet(sessionUUID, sessionStartDate, statusCode, changes, error)
    }

    // based on _HDCodableNanoSyncChangeReadFrom in HealthDaemon binary
    private fun parseNanoChange(pb: ProtoBuf): NanoSyncChange {
        val objectType = pb.readShortVarInt(1)
        val startAnchor = pb.readShortVarInt(2)
        val endAnchor = pb.readShortVarInt(3)

        val objectData = pb.readSingletFieldAsserted(4) as ProtoBuf
        val syncAnchor = NanoSyncAnchor.fromPB(pb.readSingletFieldAsserted(5) as ProtoBuf)
        val speculative = pb.readBool(6)
        val sequence = pb.readShortVarInt(7)
        val complete = pb.readBool(8)
        val entityIdentifier = EntityIdentifier.fromPB(pb.readSingletFieldAsserted(9) as ProtoBuf)

        return NanoSyncChange(objectType, startAnchor, endAnchor, objectData, syncAnchor, speculative, sequence, complete, entityIdentifier)
    }
}

class NanoSyncMessage(
    val version: Int,
    val persistentPairingUUID: UUID,
    val healthPairingUUID: UUID,
    val status: Any?,
    val changeSet: NanoSyncChangeSet?,
    val activationRestore: NanoSyncActivationRestore
    )

class NanoSyncChangeSet(
    val sessionUUID: UUID,
    val sessionStart: Date,
    val statusCode: Int,
    val changes: List<NanoSyncChange>,
    val error: NanoSyncError
)

class NanoSyncChange(
    val objectType: Int,
    val startAnchor: Int,
    val endAnchor: Int,
    val objectData: ProtoBuf,
    val syncAnchor: NanoSyncAnchor,
    val speculative: Boolean,
    val sequence: Int,
    val complete: Boolean,
    val entityIdentifier: EntityIdentifier
)

class NanoSyncStatus(
    val syncAnchor: NanoSyncAnchor,
    val statusCode: Int
) {
    companion object {
        fun fromPB(pb: ProtoBuf): NanoSyncStatus {
            val status = pb.readShortVarInt(1)
            val anchor = NanoSyncAnchor.fromPB(pb.readSingletFieldAsserted(2) as ProtoBuf)

            return NanoSyncStatus(anchor, status)
        }
    }
}

class NanoSyncError(
    val localizedDescription: String,
    val code: Int,
    val domain: String
) {
    companion object {
        fun fromPB(pb: ProtoBuf): NanoSyncError {
            val domain = (pb.readSingletFieldAsserted(1) as ProtoString).value
            val code = pb.readShortVarInt(2)
            val desc = (pb.readSingletFieldAsserted(3) as ProtoString).value
            return NanoSyncError(desc, code, domain)
        }
    }
}

class NanoSyncAnchor(
    val objectType: Int,
    val anchor: Long,
    val entityIdentifier: EntityIdentifier
) {
    companion object {
        fun fromPB(pb: ProtoBuf): NanoSyncAnchor {
            val objectType = pb.readShortVarInt(1)
            val anchor = pb.readLongVarInt(2)
            val entityIdentifier = EntityIdentifier.fromPB(pb.readSingletFieldAsserted(3) as ProtoBuf)
            return NanoSyncAnchor(objectType, anchor, entityIdentifier)
        }
    }
}

class NanoSyncActivationRestore(
    val sequenceNumber: Int,
    val statusCode: Int,
    val defaultSouceBundleIdentifier: String
) {
    companion object {
        fun fromPB(pb: ProtoBuf): NanoSyncActivationRestore {
            // 1: restore identifier
            val seq = pb.readShortVarInt(2)
            val status = pb.readShortVarInt(3)
            val bundle = (pb.readSingletFieldAsserted(4) as ProtoString).value
            // 6: obliterated health pairing uuids

            return NanoSyncActivationRestore(seq, status, bundle)
        }
    }
}

class EntityIdentifier(
    val identifier: Int,
    val schema: String
) {
    companion object {
        fun fromPB(pb: ProtoBuf): EntityIdentifier {
            val schema = (pb.readSingletFieldAsserted(1) as ProtoString).value
            val identifier = pb.readShortVarInt(2)
            return EntityIdentifier(identifier, schema)
        }
    }
}