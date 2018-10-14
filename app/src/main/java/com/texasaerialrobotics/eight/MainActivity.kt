package com.texasaerialrobotics.eight

import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlin.concurrent.thread

class InitAndRunViewModelTask: AsyncTask<SocketViewModel, Void, Void>() {
    override fun doInBackground(vararg params: SocketViewModel?): Void? {
        for (param in params) {
            param?.launch()
        }
        return null
    }

    override fun onPostExecute(result: Void?) {
        // begin actual broadcast stuff
    }
}

class MainActivity : AppCompatActivity() {

    lateinit var model: SocketViewModel
    lateinit var dataObserver: Observer<Int?>
    lateinit var mData: TextView
    lateinit var mSend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mData = findViewById(R.id.data_text_view_main_activity)
        mSend = findViewById(R.id.send_button_main_activity)

        model = ViewModelProviders
                .of(this, SocketViewModelProvider(true))
                .get(SocketViewModel::class.java)
        InitAndRunViewModelTask().execute(model)

        dataObserver = Observer {
            d -> mData.text = d?.toString() ?: "null"
        }
        model.data.observe(this, dataObserver)

        mSend.setOnClickListener {
            thread(start = true) { model.send(16) }
        }

        Toast.makeText(this, model.connectionHealth().toString(), Toast.LENGTH_LONG).show()
    }
}
