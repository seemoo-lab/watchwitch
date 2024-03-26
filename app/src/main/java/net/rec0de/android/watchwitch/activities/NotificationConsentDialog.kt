package net.rec0de.android.watchwitch.activities

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.R

class NotificationConsentDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction.
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater

            builder.setView(inflater.inflate(R.layout.notification_consent, null))
                .setPositiveButton("Allow") { dialog, id ->
                    dismiss()
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
                .setNeutralButton("Not now") { dialog, id ->
                    dismiss()
                }
                .setNegativeButton("Deny") { dialog, id ->
                    LongTermStorage.notificationAccessDeniedForever = true
                    dismiss()
                }
            // Create the AlertDialog object and return it.
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
