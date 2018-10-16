package com.texasaerialrobotics.eight

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

val RECORDING_FILE = Environment.getExternalStorageDirectory().absolutePath + "/someAudioFile"

class RecordingManager(private val recorder: MediaRecorder) {
    private val tasks = LinkedBlockingQueue<Double>()
    val state = MutableLiveData<Boolean>()
    init {
        state.postValue(true)
    }
    private val processingth: Thread = thread(start = true) {
        while (!Thread.interrupted()) {
            val task = tasks.take()
            if (task > 0) {
                state.postValue(false)
                Log.d("asdfasdfalsdkifjlas", "Done posting?")
                prepareRecording()
                Log.d("asdfasdfalsdkifjlas", "Done Prepare?")
                beginRecording()
                Log.d("asdfasdfalsdkifjlas", "Done Start?")
                if (!Thread.interrupted()) {
                    Thread.sleep((task * 1000).toLong())
                    Log.d("asdfasdfalsdkifjlas", "Slep?")
                }
                Log.d("asdfasdfalsdkifjlas", "Done?")
                endRecording()
                Log.d("asdfasdfalsdkifjlas", "Done?")
                state.postValue(true)
            }
        }
        recorder.reset()
        recorder.release()
    }

    private fun prepareRecording() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFile(RECORDING_FILE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        recorder.prepare()
    }

    private fun beginRecording() {
        recorder.start()
    }

    private fun endRecording() {
        recorder.stop()
        recorder.reset()
    }

    fun recordAndProcess(seconds: Double) {
        tasks.clear()
        tasks.put(seconds)
    }

    fun halt() {
        tasks.clear()
        processingth.interrupt()
        tasks.put(-1.0)
        processingth.join()
    }
}
