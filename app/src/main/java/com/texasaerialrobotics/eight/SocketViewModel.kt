package com.texasaerialrobotics.eight

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedTransferQueue
import kotlin.concurrent.thread

private const val portno = 9878
private val inet6 = Inet6Address.getByName("10.0.2.15")

class SocketViewModelProvider(private val server: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return if (modelClass.canonicalName?.equals(SocketViewModel::class.java.canonicalName) == true) {
            SocketViewModel(server) as T
        } else {
            null as T
        }
    }
}

class SocketViewModel(private val isServer: Boolean) : ViewModel() {
    val data by lazy {
        MutableLiveData<Int?>()
    }

    private val srvSock = if (isServer) ServerSocket(portno) else null
    private var launched = false

    private lateinit var commSock: Socket
    private lateinit var output: BufferedOutputStream
    private lateinit var input: BufferedInputStream

    private var sendQ = LinkedTransferQueue<Int>()

    private lateinit var recvth: Thread
    private lateinit var sendth: Thread

    fun launch() {
        synchronized(launched) {
            if (!launched) {
                launched = true

                Log.d("SocketViewModel", srvSock?.inetAddress?.canonicalHostName)
                Log.d("SocketViewModel", srvSock?.inetAddress?.hostName)
                Log.d("SocketViewModel", srvSock?.inetAddress?.hostAddress)
                Log.d("SocketViewModel", srvSock?.localSocketAddress.toString())

                commSock = if (isServer) srvSock!!.accept() else Socket(inet6, portno)

                Log.d("SocketViewModel", "Connected.")

                output = BufferedOutputStream(commSock.getOutputStream())
                input = BufferedInputStream(commSock.getInputStream())
                //FREAKING BYTE MANIPULATION IS A PAIN IN JAVA
                // TODO: make sure all this crap works
                recvth = thread(start = true) {
                    while (!Thread.interrupted()) {
                        val buf = ByteBuffer.allocate(4)
                        buf.order(ByteOrder.BIG_ENDIAN)
                        for (i in 0 until 4) {
                            var reading = 0
                            do { reading = input.read() } while (reading < 0)
                            buf.put(i, reading.toByte())
                            Log.d("SocketViewModel", "data: $reading, index: $i")
                        }
                        data.postValue(buf.getInt(0))
                        Thread.sleep(100)
                        Log.d("SocketViewModel", "full int: ${data.value}")
                    }
                }
                sendth = thread(start = true) {
                    val buf = ByteBuffer.allocate(4)
                    buf.order(ByteOrder.BIG_ENDIAN)
                    val arr = byteArrayOf(0, 0, 0, 0)
                    while (!Thread.interrupted()) {
                        val cmd = sendQ.take()
                        Log.d("SocketViewModel", "Sending value: $cmd")
                        if (cmd >= 0) {
                            buf.putInt(0, cmd)
                            for (i in 0 until 4) {
                                output.write(buf[i] as Int)
                            }
                            output.flush()
                        }
                    }
                }
                srvSock?.close()
            }
        }
    }

    fun send(cmd: Int) {
        var stored = false
        while (!stored) {
            synchronized(launched) { stored = launched }
            if (stored) sendQ.put(cmd)
        }
    }

    fun connectionHealth(): InetAddress? {
        return srvSock?.inetAddress
    }

    override fun onCleared() {
        sendth.interrupt()
        recvth.interrupt()

        sendQ.put(-1)
        sendQ.put(-1)

        if (launched) {
            output.close()
            input.close()
            commSock.close()
        }
        super.onCleared()
    }
}