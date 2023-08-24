package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.decoders.aoverc.Decryptor
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPListParser
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import net.rec0de.android.watchwitch.fromBytesLittle
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import net.rec0de.android.watchwitch.hexBytes
import net.rec0de.android.watchwitch.servicehandlers.UTunService
import net.rec0de.android.watchwitch.utun.DataMessage
import net.rec0de.android.watchwitch.utun.UTunHandler
import net.rec0de.android.watchwitch.utun.UTunMessage
import java.util.UUID

object HealthSync : UTunService {
    override val name = "com.apple.private.alloy.health.sync.classc"

    private val keys = LongTermStorage.getMPKeysForClass("A")
    private val decryptor = if(keys != null) Decryptor(keys) else null

    private val syncStatus = mutableMapOf<SyncStatusKey, Int>()
    private var utunSequence = 0

    override fun acceptsMessageType(msg: UTunMessage) = msg is DataMessage

    override fun receiveMessage(msg: UTunMessage, handler: UTunHandler) {
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
            val syncMsg = parseSyncMsg(plaintext, msg.responseIdentifier != null)

            if(syncMsg.changeSet != null) {
                handleChangeSet(syncMsg.changeSet)
            }

            // build a reply with out updated sync state
            val syncAnchors = syncStatus.map { it.key.toSyncAnchorWithValue(it.value.toLong()) }
            val nanoSyncStatus = NanoSyncStatus(syncAnchors, syncMsg.changeSet?.statusCode ?: 1) // status: echo incoming sync set
            val reply = NanoSyncMessage(12, syncMsg.persistentPairingUUID, syncMsg.healthPairingUUID, nanoSyncStatus, null, null)
            val protoBytes = "0200".hexBytes() + reply.renderProtobuf() // responses are only prefixed with msgid, not priority

            Logger.logUTUN("our sync status now: $syncAnchors", 1)
            sendEncrypted(protoBytes, msg.streamID, msg.messageUUID, handler)
        }
    }

    // based on HDIDSMessageCenter::service:account:incomingData:fromID:context: in HealthDaemon binary
    private fun parseSyncMsg(msg: ByteArray, isReply: Boolean): NanoSyncMessage {
        // first two bytes are message type (referred to message ID in apple sources)
        val type = UInt.fromBytesLittle(msg.sliceArray(0 until 2)).toInt()
        var offset = 2

        if(!isReply){
            // third byte is priority (0: default, 1: urgent, 2: sync)
            val priority = msg[3].toInt()
            offset = 3
            Logger.logUTUN("rcv HealthSync msg type $type priority $priority", 2)
        }
        else {
            Logger.logUTUN("rcv HealthSync msg type $type (reply)", 2)
        }


        val syncMsg = when(type) {
            2 -> parseNanoSyncMessage(msg.fromIndex(offset))
            else -> throw Exception("Unsupported HealthSync message type $type")
        }

        Logger.logUTUN("rcv HealthSync $syncMsg", 1)
        return syncMsg
    }

    private fun parseNanoSyncMessage(bytes: ByteArray): NanoSyncMessage {
        val pb = ProtobufParser().parse(bytes)
        try {
            return NanoSyncMessage.fromSafePB(pb)
        }
        catch(e: Exception) {
            // if we fail parsing something, print the failing protobuf for debugging and then still fail
            println(pb)
            throw e
        }
    }

    private fun handleChangeSet(changeSet: NanoSyncChangeSet) {
        Logger.logUTUN("Handling change set, status: ${changeSet.statusString}", 1)
        changeSet.changes.forEach { change ->
            // given that we usually start listening for health data in the middle of an existing pairing,
            // we just accept whatever we are sent and set out sync state to that anchor, ignoring previous
            // missing data
            val syncKey = SyncStatusKey(change.entityIdentifier.identifier, change.entityIdentifier.schema, change.objectType)
            syncStatus[syncKey] = change.endAnchor
        }
    }

    private fun sendEncrypted(bytes: ByteArray, streamId: Int, inResponseTo: UUID?, handler: UTunHandler) {
        val encrypted = decryptor!!.encrypt(bytes).renderAsTopLevelObject()
        val dataMsg = DataMessage(utunSequence, streamId, 0, inResponseTo, UUID.randomUUID(), null, null, encrypted)
        utunSequence += 1
        handler.send(dataMsg)
    }
}