package com.gestureshare.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receiver runs a TCP server on port 5000 to listen for incoming screenshots.
 */
class Receiver(private val port: Int = 5000, private val onReceived: (Bitmap) -> Unit) {

    private val TAG = "Receiver"
    private var serverSocket: ServerSocket? = null
    private var isRunning = AtomicBoolean(false)
    private var receiverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Starts the server.
     */
    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        receiverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Receiver server started on port $port")
                while (isRunning.get()) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        handleSocket(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error in receiver server: ${e.message}")
                }
            }
        }
    }

    /**
     * Stops the server.
     */
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        receiverJob?.cancel()
        serverSocket = null
    }

    private fun handleSocket(socket: Socket) {
        scope.launch {
            try {
                Log.d(TAG, "Connection received from ${socket.inetAddress}")
                val inputStream = BufferedInputStream(socket.getInputStream())
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap received and decoded successfully")
                    launch(Dispatchers.Main) {
                        onReceived(bitmap)
                    }
                } else {
                    Log.e(TAG, "Failed to decode bitmap from stream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client connection: ${e.message}")
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing client socket: ${e.message}")
                }
            }
        }
    }
}
