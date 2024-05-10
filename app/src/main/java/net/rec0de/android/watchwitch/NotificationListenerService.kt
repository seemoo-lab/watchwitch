package net.rec0de.android.watchwitch

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService
import net.rec0de.android.watchwitch.servicehandlers.messaging.NotificationWaitingForReply
import java.util.UUID


class WatchNotificationForwarder : NotificationListenerService() {
    
    private val receivedTimestamps = mutableSetOf<Long>()
    private val receivedNotifications = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Logger.log("Notification received: $sbn", 2)

        when(sbn.packageName) {
            "org.thoughtcrime.securesms" -> handleSignal(sbn.notification)
            "com.whatsapp" -> handleWhatsapp(sbn.notification)
        }

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        //Logger.log("Notification removed: $sbn", 0)
    }

    private fun handleSignal(msg: Notification) {
        // filter out notifications we see when signal is checking for messages
        if(msg.category != "msg")
            return

        val sender = msg.extras.getString(Notification.EXTRA_TITLE)
        val message = msg.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        // when multiple messages are present, we get some annoying extra messages titled "Signal"
        if(sender == "Signal")
            return

        if (System.currentTimeMillis() - msg.`when` > 3000 || receivedTimestamps.contains(msg.`when`) || receivedNotifications.contains("$sender|$message")) {
            // discard re-posted old notifications (>3s) or notifications with timestamps we already received
            return;
        }
        else {
            receivedTimestamps.add(msg.`when`)
            receivedNotifications.add("$sender|$message")
            // keep only one minute worth of notification timestamps (or up to 20)
            if(receivedTimestamps.size > 20) {
                receivedTimestamps.removeIf { it < msg.`when` - 60000 }
            }
            if(receivedNotifications.size > 20) {
                receivedNotifications.clear() // imperfect but should be good enough
            }
        }

        Logger.log("Signal: $sender - $message", 0)

        val bulletinUUID = UUID.randomUUID()
        BulletinDistributorService.replyable.add(NotificationWaitingForReply(msg, bulletinUUID))

        forwardAsSignalMessage(sender?: "unknown", message, bulletinUUID)
    }

    private fun handleWhatsapp(msg: Notification) {
        val sender = msg.extras.getString(Notification.EXTRA_TITLE)
        val message = msg.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        Logger.log("WhatsApp: $sender - $message", 0)
        forwardAsSpoofedIMessage(sender?: "unknown", message)
    }

    private fun forwardAsSpoofedIMessage(title: String, body: String) {
        val thread = Thread {
            val success = BulletinDistributorService.sendBulletin(title, body)
            Logger.log("Forwarding message \"$title: $body\", success: $success", 0)
        }
        thread.start()
    }

    private fun forwardAsSignalMessage(title: String, body: String, uuid: UUID) {
        val thread = Thread {
            val success = BulletinDistributorService.sendSignalReplyable(title, body, uuid)
            Logger.log("Forwarding message \"$title: $body\" as Signal message with UUID $uuid, success: $success", 0)
        }
        thread.start()
    }
}
