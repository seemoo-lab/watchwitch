package net.rec0de.android.watchwitch.activities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import net.rec0de.android.watchwitch.R
import net.rec0de.android.watchwitch.adapter.FilesAdapter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date

class FilesActivity : AppCompatActivity() {
    private val imgExtensions = setOf("png", "jpg", "JPG", "bmp")

    var sourceFilePath = ""
    val savePrompt = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if(uri != null) {
            val thread = Thread {
                try {
                    val sourceFile = FileInputStream(sourceFilePath)
                    applicationContext.contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { stream ->
                            sourceFile.copyTo(stream)
                        }
                    }
                    sourceFile.close()
                } catch (e: Exception) {
                    runOnUiThread {
                        val duration = Toast.LENGTH_LONG
                        val toast = Toast.makeText(this, R.string.files_store_failed, duration)
                        toast.show()
                        e.printStackTrace()
                    }
                }
            }
            thread.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        val msgList = findViewById<RecyclerView>(R.id.listFiles)
        val emptyLabel = findViewById<TextView>(R.id.labelFilesEmpty)

        val storedFiles = filesDir.listFiles()!!.toList().filter {
            if(it.name == "osmdroid" || it.name.startsWith("ww-internal"))
                false
            // delete empty session logs before display
            else if(it.length() == 0L && it.name.startsWith("session-")) {
                it.delete()
                false
            }
            else
                true
        }

        val items = storedFiles.map {file ->
            val bitmap = if(file.extension in imgExtensions) BitmapFactory.decodeFile(file.absolutePath) else null
            FileItem(file.name, Date(file.lastModified()), bitmap, file.absolutePath, file.length())
        }.sortedByDescending { it.timestamp }

        if(storedFiles.isEmpty()) {
            emptyLabel.visibility = VISIBLE
            msgList.visibility = GONE
        }
        else {
            emptyLabel.visibility = GONE
            msgList.visibility = VISIBLE
        }

        println(storedFiles)

        msgList.adapter = FilesAdapter(this, items.toMutableList())
        msgList.setHasFixedSize(false)
    }

    class FileItem(val filename: String, val timestamp: Date, val bitmap: Bitmap?, val fullPath: String, val size: Long)
}