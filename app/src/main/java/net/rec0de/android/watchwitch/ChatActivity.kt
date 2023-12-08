package net.rec0de.android.watchwitch

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.rec0de.android.watchwitch.adapter.ChatbubbleAdapter
import net.rec0de.android.watchwitch.adapter.HealthLogAdapter
import net.rec0de.android.watchwitch.servicehandlers.health.db.DatabaseWrangler
import net.rec0de.android.watchwitch.servicehandlers.messaging.BulletinDistributorService

class ChatActivity : AppCompatActivity() {

    private lateinit var msgList: RecyclerView
    private val adapter = ChatbubbleAdapter(mutableListOf())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        msgList = findViewById(R.id.msgListView)
        msgList.adapter = adapter

        val textInput = findViewById<EditText>(R.id.editMessage)

        val sendBtn = findViewById<ImageButton>(R.id.btnSend)
        sendBtn.setOnClickListener {
            val msg = textInput.text.toString()
            sendMsg(msg)
            textInput.text.clear()
        }

    }

    private fun sendMsg(text: String) {
        val thread = Thread {
            val success = BulletinDistributorService.sendBulletin("WatchWitch", text)
            if(success) {
                runOnUiThread{
                    adapter.msgs.add(text)
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
}