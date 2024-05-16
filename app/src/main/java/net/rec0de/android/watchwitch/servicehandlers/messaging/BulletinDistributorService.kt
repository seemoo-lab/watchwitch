package net.rec0de.android.watchwitch.servicehandlers.messaging

import android.annotation.SuppressLint
import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.alloy.AlloyController
import net.rec0de.android.watchwitch.alloy.AlloyHandler
import net.rec0de.android.watchwitch.alloy.AlloyMessage
import net.rec0de.android.watchwitch.alloy.AlloyService
import net.rec0de.android.watchwitch.alloy.ProtobufMessage
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.decoders.bplist.BPAsciiString
import net.rec0de.android.watchwitch.decoders.bplist.BPDict
import net.rec0de.android.watchwitch.decoders.bplist.BPString
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoVarInt
import net.rec0de.android.watchwitch.decoders.protobuf.ProtobufParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("StaticFieldLeak")
object BulletinDistributorService : AlloyService {
    override val handlesTopics = listOf("com.apple.private.alloy.bulletindistributor", "com.apple.private.alloy.bulletindistributor.settings")
    override fun acceptsMessageType(msg: AlloyMessage) = msg is ProtobufMessage

    // the watch refuses to properly reply to our bulletin messages, despite showing the notifications we send
    // (why?)
    // so we can't really know if we initialized a session successfully - we'll just reinitialize a new one on each app launch
    private val localSessionId = Utils.uuidFromBytes("6c760fe2ab084d8592de516d2aa6652c".fromHex())
    private var sessionInitialized = false
    private var sendingSequence = 1

    private var remoteState = 0
    private var remoteSequence = 0
    private var remoteUUID: UUID? = null

    private lateinit var context: Context

    val replyable = mutableListOf<NotificationWaitingForReply>()

    fun initContext(ctx: Context) {
        context = ctx
    }

    override fun receiveMessage(msg: AlloyMessage, handler: AlloyHandler) {
        msg as ProtobufMessage

        // trailer handling
        val trailerLen = Int.fromBytes(msg.payload.fromIndex(msg.payload.size-2), net.rec0de.android.watchwitch.bitmage.ByteOrder.LITTLE)
        if(trailerLen > msg.payload.size - 2 || trailerLen > 100) {
            Logger.logIDS("[bulletin] got message without expected trailer: ${msg.payload.hex()}", 0)
            return
        }

        val endIndex = msg.payload.size - 2
        val startIndex = endIndex - trailerLen
        val potentialTrailer = msg.payload.sliceArray(startIndex until endIndex)

        val trailer = try {
            val parsedTrailer = Trailer.fromSafePB(ProtobufParser().parse(potentialTrailer))
            Logger.logIDS("[bulletin] got trailer: $parsedTrailer", 2)
            parsedTrailer
        }
        catch(e: Exception) {
            Logger.logIDS("[bulletin] couldn't parse trailer in msg: ${msg.payload.hex()}", 0)
            return
        }

        handleTrailer(trailer)

        val payload = msg.payload.sliceArray(0 until startIndex)
        val pb = try {
            ProtobufParser().parse(payload)
        }
        catch(e: Exception) {
            Logger.logIDS("[bulletin] failed parsing payload: ${payload.hex()}", 0)
            Logger.logIDS("[bulletin] $e", 0)
            return
        }

        // type associations from BLTRemoteGizmoClient::registerProtobufHandlers in BulletinDistributorCompanion
        // (and BLTSettingSyncServer::registerProtobufHandlers, and some other places)
        when(msg.type) {
            1 -> Logger.logIDS("[bulletin] got ${BulletinRequest.fromSafePB(pb)}", 0)
            2 -> Logger.logIDS("[bulletin] got ${RemoveBulletinRequest.fromSafePB(pb)}", 0) // for some reason remove bulletin requests use type 2 and 10?
            3 -> Logger.logIDS("[bulletin] got ${AddBulletinSummaryRequest.fromSafePB(pb)}", 0)
            4 -> Logger.logIDS("[bulletin] got ${CancelBulletinRequest.fromSafePB(pb)}", 0)
            5 -> handleActionRequest(AcknowledgeActionRequest.fromSafePB(pb))
            6 -> handleActionRequest(SnoozeActionRequest.fromSafePB(pb))
            7 -> handleActionRequest(SupplementaryActionRequest.fromSafePB(pb))
            8 -> handleActionRequest(DismissActionRequest.fromSafePB(pb))
            9 -> handleDidPlayLightsAndSirens(DidPlayLightsAndSirens.fromSafePB(pb))
            10 -> Logger.logIDS("[bulletin] got ${RemoveBulletinRequest.fromSafePB(pb)}", 0)
            12 -> handleAckInitialSequence(AckInitialSequenceNumberRequest.fromSafePB(pb))
            13 -> if(msg.isResponse == 1)
                Logger.logIDS("[bulletin] got SetSectionInfoResponse $pb", 0)
            else
                Logger.logIDS("[bulletin] got ${SetSectionInfoRequest.fromSafePB(pb)}", 0)
            14 -> if(msg.isResponse == 1)
                Logger.logIDS("[bulletin] got SetSectionSubtypeParametersIconResponse $pb", 0)
            else
                Logger.logIDS("[bulletin] got ${SetSectionSubtypeParametersIconRequest.fromSafePB(pb)}", 0)
            15 -> Logger.logIDS("[bulletin] got ${UpdateBulletinListRequest.fromSafePB(pb)}", 0)
            16 -> Logger.logIDS("[bulletin] got ShouldSuppressLightsAndSirensRequest $pb", 0)
            17 -> Logger.logIDS("[bulletin] got PairedDeviceReady $pb", 0)
            18 -> Logger.logIDS("[bulletin] got ${WillSendLightsAndSirens.fromSafePB(pb)}", 0)
            19 -> Logger.logIDS("[bulletin] got ${RemoveSectionRequest.fromSafePB(pb)}", 0)
            20 -> Logger.logIDS("[bulletin] got ${SetNotificationsAlertLevelRequest.fromSafePB(pb)}", 0)
            21 -> Logger.logIDS("[bulletin] got ${SetNotificationsGroupingRequest.fromSafePB(pb)}", 0)
            22 -> Logger.logIDS("[bulletin] got ${SetNotificationsSoundRequest.fromSafePB(pb)}", 0)
            23 -> Logger.logIDS("[bulletin] got ${SetNotificationsCriticalAlertRequest.fromSafePB(pb)}", 0)
            24 -> Logger.logIDS("[bulletin] got ${SetRemoteGlobalSpokenSettingEnabledRequest.fromSafePB(pb)}", 0)
            else -> Logger.logIDS("[bulletin] got unknown type ${msg.type}: $pb", 0)
        }
    }

    private fun handleActionRequest(msg: AbstractActionRequest) {
        Logger.logIDS("[bulletin] got $msg", 0)

        // we might have a reply here
        if(msg is SupplementaryActionRequest) {
            val reply = extractReplyText(msg)
            if(reply != null) {
                // reply to source message if possible
                val matchingNotification = replyable.firstOrNull { it.sentAsUUID == UUID.fromString(msg.publisherBulletinID!!) }
                val matchingReplyAction = matchingNotification?.quickReplyAction
                if(matchingReplyAction != null) {
                    val intent = Intent()
                    val bundle = Bundle()

                    val actualInputs = matchingReplyAction.remoteInputs.map { input ->
                        bundle.putCharSequence(input.resultKey, reply)
                        val builder: RemoteInput.Builder = RemoteInput.Builder(input.resultKey)
                        builder.setLabel(input.label)
                        builder.setChoices(input.choices)
                        builder.addExtras(input.extras)
                        builder.build()
                    }

                    val inputs: Array<RemoteInput> = actualInputs.toTypedArray()
                    RemoteInput.addResultsToIntent(inputs, intent, bundle)
                    matchingReplyAction.actionIntent.send(context, 0, intent)
                    replyable.remove(matchingNotification)
                }


                // send reply to chat view
                Intent().also { intent ->
                    intent.action = "net.rec0de.android.watchwitch.chitchat"
                    intent.putExtra("data", "reply:$reply")
                    context.sendBroadcast(intent)
                }
            }
        }
        else {
            Intent().also { intent ->
                intent.action = "net.rec0de.android.watchwitch.chitchat"
                intent.putExtra("data", "action:${msg.name}")
                context.sendBroadcast(intent)
            }
        }
    }

    private fun handleDidPlayLightsAndSirens(msg: DidPlayLightsAndSirens) {
        Logger.logIDS("[bulletin] got $msg", 0)
        Intent().also { intent ->
            intent.action = "net.rec0de.android.watchwitch.chitchat"
            intent.putExtra("data", "lightsandsirens:${msg.didPlayLightsAndSirens}")
            context.sendBroadcast(intent)
        }
    }

    private fun extractReplyText(msg: SupplementaryActionRequest): String? {
        val ctx = msg.actionInfo?.context
        if(ctx != null && ctx is BPDict && ctx.values.containsKey(BPAsciiString("userResponseInfo"))) {
            val responseInfo = ctx.values[BPAsciiString("userResponseInfo")] as BPDict
            if (responseInfo.values.containsKey(BPAsciiString("UIUserNotificationActionResponseTypedTextKey"))) {
                return (responseInfo.values[BPAsciiString("UIUserNotificationActionResponseTypedTextKey")] as BPString).value
            }
        }
        return null
    }


    /*
    Alright, hi, hello - I'll just put this here:
    The watch doesn't engage in a proper session handshake with us, but from what i can tell the handshake *should* look like this:
    1. send any message with a Trailer(phoneUUID, sequence = 1, flag = true, state = 1)
    2. receive AckInitialSequenceNumberRequest(phoneUUID, state=1) with Trailer(watchUUID, sequence = 1, flag = true, state = 1)
    3. send AckInitialSequenceNumberRequest(watchUUID, state=1) with Trailer(phoneUUID, sequence = 2, flag = true, state = 1)
    4. receive AckInitialSequenceNumberRequest(phoneUUID, state=1) with Trailer(watchUUID, sequence = 2, flag = true, state = 1)
    5. send AckInitialSequenceNumberRequest(watchUUID, state=1) with Trailer(phoneUUID, sequence = 3, state = 2)
    6. receive AckInitialSequenceNumberRequest(phoneUUID, state=2) with Trailer(watchUUID, sequence = 3, state = 2)
    7. send AckInitialSequenceNumberRequest(watchUUID, state=2) with Trailer(phoneUUID, sequence = 4, state = 2)
    8. receive AckInitialSequenceNumberRequest(phoneUUID, state=2) with Trailer(watchUUID, sequence = 4, state = 2)
    9. send AckInitialSequenceNumberRequest(watchUUID, state=2) with Trailer(phoneUUID, sequence = 4)
     */
    private fun handleAckInitialSequence(msg: AckInitialSequenceNumberRequest) {
        Logger.logIDS("[bulletin] got $msg", 0)
        if(msg.sessionState == 1) {
            val trailer = genTrailer(setFieldTwo = false, state = 2)
            val ack = AckInitialSequenceNumberRequest(null, Utils.uuidToBytes(localSessionId), 1)
            sendMsg(ack.renderProtobuf() + trailer, 12, false)
        }
    }

    private fun handleTrailer(trailer: Trailer) {
        if(trailer.state != null)
            remoteState = trailer.state
        remoteUUID = trailer.uuid
        remoteSequence = trailer.sequence
    }

    fun sendBulletin(from: String, text: String): Boolean {
        val msg = BulletinRequest.mimicIMessage(from, text, "$from@example.com")

        val trailer = if(sessionInitialized) genTrailer() else genTrailer(setFieldTwo = true, state = 1)
        val payload = msg.renderProtobuf() + trailer

        val success = sendMsg(payload, 1, false)
        if(success)
            sessionInitialized = true
        return success
    }

    fun sendSignalReplyable(from: String, text: String, uuid: UUID): Boolean {
        val msg = BulletinRequest.mimicSignal(from, text, uuid)

        val trailer = if(sessionInitialized) genTrailer() else genTrailer(setFieldTwo = true, state = 1)
        val payload = msg.renderProtobuf() + trailer

        val success = sendMsg(payload, 1, false)
        if(success)
            sessionInitialized = true
        return success
    }

    private fun sendMsg(msg: ByteArray, type: Int, isResponse: Boolean): Boolean {
        // prefer Urgent and Non-Cloud channels, but we don't really care that much
        val channelPrefs = listOf("UTunDelivery-Default-Urgent-C", "UTunDelivery-Default-Urgent-D", "UTunDelivery-Default-UrgentCloud-C", "UTunDelivery-Default-UrgentCloud-D", "UTunDelivery-Default-Default-C", "UTunDelivery-Default-Default-D")
        val handler = AlloyController.getHandlerForChannel(channelPrefs)
        return if(handler == null) {
            Logger.logIDS("[bulletin] can't send, no handler for channel", 0)
            false
        }
        else {
            //Logger.logIDS("[bulletin] sending payload: ${msg.hex()}", 0)
            handler.sendProtobuf(msg, "com.apple.private.alloy.bulletindistributor", type, isResponse)
            sessionInitialized = true
            true
        }
    }

    private fun genTrailer(setFieldTwo: Boolean = false, state: Int? = null): ByteArray {
        val fields = mutableMapOf(
            1 to listOf(ProtoVarInt(sendingSequence)),
            3 to listOf(ProtoLen(Utils.uuidToBytes(localSessionId)))
        )

        if(setFieldTwo)
            fields[2] = listOf(ProtoVarInt(1))
        if(state != null)
            fields[4] = listOf(ProtoVarInt(state))

        val trailer = ProtoBuf(fields)
        sendingSequence += 1

        val bytes = trailer.renderStandalone()
        val len = bytes.size
        val buf = ByteBuffer.allocate(len+2)
        buf.put(bytes)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(len.toShort())
        return buf.array()
    }
}

data class NotificationWaitingForReply(val notification: Notification, val sentAsUUID: UUID) {

    // based on Rob J's Notification Helper Library: https://github.com/iamrobj/NotificationHelperLibrary/blob/master/notifLib/src/main/java/com/robj/notificationhelperlibrary/utils/NotificationUtils.java
    val quickReplyAction: Notification.Action?
        get() = notification.actions.firstOrNull { action -> action.remoteInputs?.any { isKnownReplyKey(it.resultKey) } ?: false }

    private fun isKnownReplyKey(resultKey: String) = resultKey.lowercase().contains("reply") || resultKey.lowercase().contains("android.intent.extra.text")
}