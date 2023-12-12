package net.rec0de.android.watchwitch.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.adapter.ChatbubbleAdapter
import net.rec0de.android.watchwitch.servicehandlers.PreferencesSync
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService

class ChatActivity : AppCompatActivity() {

    private lateinit var msgList: RecyclerView
    private val adapter = ChatbubbleAdapter(mutableListOf())

    private val br: BroadcastReceiver = MyBroadcastReceiver(this)
    private val chatTopic = "net.rec0de.android.watchwitch.chitchat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        msgList = findViewById(R.id.msgListView)
        msgList.adapter = adapter

        val filter = IntentFilter(chatTopic)
        ContextCompat.registerReceiver(baseContext, br, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val textInput = findViewById<EditText>(R.id.editMessage)

        val sendBtn = findViewById<ImageButton>(R.id.btnSend)
        sendBtn.setOnClickListener {
            val msg = textInput.text.toString()
            sendMsg(msg)
            textInput.text.clear()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(br)
    }

    private fun sendMsg(text: String) {
        val thread = Thread {
            val success = BulletinDistributorService.sendBulletin("WatchWitch", text)
            if(success) {
                runOnUiThread{
                    adapter.msgs.add(Pair(false, text))
                    msgList.adapter!!.notifyItemInserted(adapter.msgs.size-1)
                }
            } else {
                runOnUiThread {
                    val duration = Toast.LENGTH_LONG
                    val toast = Toast.makeText(this, R.string.chat_send_failed, duration)
                    toast.show()
                }
            }
        }
        thread.start()
    }

    fun displayReceivedMessage(msg: String) {
        adapter.msgs.add(Pair(true, msg))
        msgList.adapter!!.notifyItemInserted(adapter.msgs.size-1)
    }

    class MyBroadcastReceiver(private val activity: ChatActivity) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val data = intent.extras!!.getString("data")!!
            println(data)
            if(data.startsWith("lightsandsirens:")) {
                val didPlayLightsAndSirens = data.removePrefix("lightsandsirens:")
                if(didPlayLightsAndSirens == "true") {
                    activity.displayReceivedMessage("played lights and sirens \uD83D\uDEA8✨")
                }
                else {
                    activity.displayReceivedMessage("no lights, no sirens \uD83D\uDD07")
                }
            }
            else if(data.startsWith("action:")) {
                when(val action = data.removePrefix("action:")) {
                    "DismissActionRequest" -> activity.displayReceivedMessage("dismissed")
                    "SnoozeActionRequest" -> activity.displayReceivedMessage("snoozed")
                    "AcknowledgeActionRequest" -> activity.displayReceivedMessage("acknowledged")
                    else -> activity.displayReceivedMessage("action: $action")
                }
            }
        }
    }
}
