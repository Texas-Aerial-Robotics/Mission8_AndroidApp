package com.texasaerialrobotics.eight

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.AsyncTask
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlin.concurrent.thread

class InitAndRunViewModelTask: AsyncTask<SocketViewModel, Unit, Unit>() {
    override fun doInBackground(vararg params: SocketViewModel?) {
        for (param in params) {
            param?.launch()
        }
    }
}

class RequestAudioPermissionsTask: AsyncTask<MainActivity, Unit, Unit>() {
    override fun doInBackground(vararg params: MainActivity?) {
        val act = params[0]
        act?.requestAudioPermissions()
    }
}

const val LISTENING_TIME = 0.1
const val AUDIO_REQ_CODE = 1
class MainActivity : AppCompatActivity() {

    private lateinit var model: SocketViewModel
    private var audioModel: RecordingManager? = null
    private lateinit var dataObserver: Observer<Int?>
    private lateinit var audioStateObserver: Observer<Boolean>
    private lateinit var mData: TextView
    private lateinit var mSend: Button
    private lateinit var mRecord: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startNetwork()
        linkAudioControls()
    }

    override fun onResume() {
        super.onResume()
        startAudio()
    }
    override fun onPause() {
        endAudio()
        super.onPause()
    }

    private fun linkNetworkControls() {
        mData = findViewById(R.id.data_text_view_main_activity)
        mSend = findViewById(R.id.send_button_main_activity)
        dataObserver = Observer { mData.text = it?.toString() ?: "null" }
        model.data.observe(this, dataObserver)

        mSend.setOnClickListener { thread(start = true) { model.send('\n'.toInt()) } }
    }
    private fun startNetwork() {
        model = ViewModelProviders
                .of(this, SocketViewModelProvider(true, 2))
                .get(SocketViewModel::class.java)
        InitAndRunViewModelTask().execute(model)
        linkNetworkControls()
    }
    private fun linkAudioControls() {
        mRecord = findViewById(R.id.rec_button_main_activity)
        mRecord.setOnClickListener {
            Log.d("asdf", "Trying to record.")
            audioModel?.recordAndProcess(LISTENING_TIME)
        }
        audioStateObserver = Observer { mRecord.isEnabled = it }
    }
    private fun startAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("asdf", "Requesting permissions...")
            RequestAudioPermissionsTask().execute(this)
        } else {
            Log.d("asdf", "Start media stuffs")
            val recorder = MediaRecorder()
            audioModel = RecordingManager(recorder)
            audioModel?.state?.observe(this, audioStateObserver)
        }
    }
    private fun endAudio() {
        audioModel?.halt()
        audioModel = null
    }

    private fun displayPrompt() {
        TODO("Display reason.")
        startAudio()
    }
    internal fun requestAudioPermissions() {
        // Permission is not granted
        val shouldShowBlurb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        } else {
            false
        }
        if (shouldShowBlurb) {
            displayPrompt()
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_REQ_CODE
            )
        }
    }
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            AUDIO_REQ_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startAudio()
                }
            }
            else -> {}
        }
    }
}
