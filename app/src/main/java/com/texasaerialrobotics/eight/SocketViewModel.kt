package com.texasaerialrobotics.eight

import android.os.ConditionVariable
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.*
import java.net.Inet6Address
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

private const val portno = 9878
private val inet6 = Inet6Address.getByName("10.0.2.15")

class SocketViewModelProvider(private val server: Boolean, private val connections: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return if (modelClass.canonicalName?.equals(SocketViewModel::class.java.canonicalName) == true) {
            SocketViewModel(server, connections) as T
        } else {
            null as T
        }
    }
}

class SocketViewModel(private val isServer: Boolean, private val connections: Int) : ViewModel() {
    val data by lazy {
        MutableLiveData<Int?>()
    }

    private var launched = false
    private val termCondition = ConditionVariable(false)

    private val commLocks = (0 until connections).map { ReentrantReadWriteLock() }
    private val commBroken = (0 until connections).map { ConditionVariable(true) }
    private val commAvailable = (0 until connections).map { ConditionVariable(false) }
    private val commSocks = (0 until connections).map { Socket() }.toMutableList()
    private val outputs = commSocks.asSequence().map {
        DataOutputStream(ByteArrayOutputStream())
    }.toMutableList()
    private val inputs = commSocks.asSequence().map {
        DataInputStream(ByteArrayInputStream(ByteArray(0)))
    }.toMutableList()

    private val sendQs = (0 until connections).map { LinkedTransferQueue<Int>() }

    private lateinit var sockth: Thread
    private lateinit var recvths: List<Thread>
    private lateinit var sendths: List<Thread>

    private fun breakSocket(sockId: Int) {
        commLocks[sockId].write {
            Log.d("alsdkfjlkasd", "Connection is broken for socket $sockId")
            commAvailable[sockId].close()
            commBroken[sockId].open()
        }
    }
    private fun makeConnection(sockId : Int, srvSock: ServerSocket?) {
        commLocks[sockId].write {
            Log.d("slkjdklfs0", "closing $sockId")
            if (!commSocks[sockId].isClosed) {
                Log.d("slkjdklfs0", "closed $sockId? ${commSocks[sockId].isClosed}")
                commSocks[sockId].close()
                Log.d("slkjdklfs0", "closed $sockId? ${commSocks[sockId].isClosed}")
            }
            Log.d("slkjdklfs0", "connecting $sockId")
            if (srvSock != null) {
                commSocks[sockId] = srvSock.accept()
            } else {
                commSocks[sockId] = Socket(inet6, portno)
            }
            Log.d("slkjdklfs0", "init streams $sockId")
            outputs[sockId] = DataOutputStream(commSocks[sockId].getOutputStream())
            inputs[sockId] = DataInputStream(commSocks[sockId].getInputStream())
            commBroken[sockId].close()
            commAvailable[sockId].open()
        }
        Log.d("slkjdklfs0", "complete reconnect $sockId")
    }

    fun launch() {
        synchronized(launched) {
            if (!launched) {
                launched = true
                sockth = thread(start = true) {
                    val srvSock = if (isServer) ServerSocket(portno, connections, inet6) else null
                    val sockths: List<Thread> = (0 until connections).map { thread(start = true) {
                        while (!Thread.interrupted()) {
                            commBroken[it].block()
                            try {
                                makeConnection(it, srvSock)
                            } catch (e: SecurityException) { throw e } catch (e: Exception) {
                                Log.w(
                                        "SocketViewModel",
                                        "Error while reestablishing connection for socket $it."
                                )
                                e.printStackTrace()
                            }
                        }
                        commLocks[it].write { commSocks[it].close() }
                    } }
                    termCondition.block()
                    sockths.forEach { it.interrupt() }
                    srvSock?.close()
                    sockths.forEach { it.join() }
                }

                //FREAKING BYTE MANIPULATION IS SUCH A PAIN IN JAVA.
                recvths = (0 until connections).map { thread(start = true) {
                    while (!Thread.interrupted()) {
                        commAvailable[it].block()
                        try {
                            val read = commLocks[it].read { inputs[it].readInt() }
                            data.postValue(read)
                            Log.d("SocketViewModel", "socket $it read $read")
                        } catch (e: SecurityException) { throw e } catch (e: IOException) {
                            breakSocket(it)
                        } catch (e: Exception) {
                            Log.d(
                                    "SocketViewModel",
                                    "Error while reading for socket $it."
                            )
                            e.printStackTrace()
                        }
                    }
                } }
                sendths = (0 until connections).map { thread(start = true) {
                    while (!Thread.interrupted()) {
                        val cmd = sendQs[it].take()
                        Log.i("SocketViewModel", "socket $it sending value $cmd")
                        var sent = false
                        while (!Thread.interrupted() && !sent) {
                            commAvailable[it].block()
                            try {
                                outputs[it].writeInt(cmd)
                                outputs[it].flush()
                                sent = true
                            } catch (e: SecurityException) { throw e } catch (e: IOException) {
                                breakSocket(it)
                                if (sendQs[it].remainingCapacity() == 0) {
                                    sendQs[it].clear()
                                }
                            } catch (e: Exception) {
                                Log.d(
                                        "SocketViewModel",
                                        "Error while sending for socket $it."
                                )
                                e.printStackTrace()
                            }
                        }
                        Log.i("SocketViewModel", "socket $it sent value $cmd.")
                    }
                } }
            }
        }
    }

    fun send(cmd: Int) {
        var stored = false
        while (!stored) {
            synchronized(launched) { stored = launched }
            if (stored) sendQs.forEach { it.put(cmd) }
        }
    }

    override fun onCleared() {
        sockth.interrupt()
        sendths.forEach { it.interrupt() }
        recvths.forEach { it.interrupt() }
        termCondition.open()
        commBroken.forEach{ it.open() }
        sockth.join()
        commAvailable.forEach{ it.open() }
        sendQs.forEach { it.put(-1) }
        sendths.forEach { it.join() }
        commAvailable.forEach{ it.open() }
        recvths.forEach { it.join() }
        Log.d("SocketViewModel", "All threads killed.")
        super.onCleared()
    }
}