package net.rec0de.android.watchwitch

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService


class WatchNotificationForwarder : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

    }
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

        Logger.log("Signal: $sender - $message", 0)
        forwardAsSpoofedIMessage(sender?: "unknown", message)
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
}
