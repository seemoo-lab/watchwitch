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

    private val syncStatus = mutableMapOf<SyncStatusKey, Long>()
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
            val syncMsg = parseSyncMsg(plaintext)

            // build a reply with out updated sync state
            val reply = NanoSyncMessage(12, syncMsg.persistentPairingUUID, syncMsg.healthPairingUUID, null, null, null)
            val protoBytes = "0200".hexBytes() + reply.renderProtobuf() // responses are only prefixed with msgid, not priority


        }
    }

    // based on HDIDSMessageCenter::service:account:incomingData:fromID:context: in HealthDaemon binary
    private fun parseSyncMsg(msg: ByteArray): NanoSyncMessage {
        // first two bytes are message type (referred to message ID in apple sources)
        val type = UInt.fromBytesLittle(msg.sliceArray(0 until 2)).toInt()
        // third byte is priority (0: default, 1: urgent, 2: sync)
        val priority = msg[3].toInt()

        Logger.logUTUN("rcv HealthSync msg type $type priority $priority", 2)

        val syncMsg = when(type) {
            2 -> parseNanoSyncMessage(msg.fromIndex(3))
            else -> throw Exception("Unsupported HealthSync message type $type")
        }

        Logger.logUTUN("rcv HealthSync $syncMsg", 1)
        return syncMsg
    }

    private fun parseNanoSyncMessage(bytes: ByteArray): NanoSyncMessage {
        val pb = ProtobufParser().parse(bytes)
        return NanoSyncMessage.fromSafePB(pb)
    }

    private fun sendEncrypted(bytes: ByteArray, streamId: Int, inResponseTo: UUID?, handler: UTunHandler) {
        val encrypted = decryptor!!.encrypt(bytes).renderAsTopLevelObject()
        val dataMsg = DataMessage(utunSequence, streamId, 0, inResponseTo, UUID.randomUUID(), null, null, bytes)
        utunSequence += 1
        handler.send(dataMsg)
    }
}