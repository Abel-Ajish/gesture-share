package com.gestureshare.network

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sender connects to a receiver IP and sends a compressed image with production-level handling.
 */
class Sender(private val port: Int = 5000) {

    private val TAG = "Sender"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Sends a bitmap to the specified IP.
     */
    fun send(bitmap: Bitmap, ip: String, callback: (Boolean) -> Unit) {
        scope.launch {
            val success = try {
                Log.d(TAG, "Attempting to connect to $ip:$port")
                
                // Use withContext to ensure we handle the socket operations properly
                withContext(Dispatchers.IO) {
                    Socket().use { socket ->
                        // Set a short timeout for connection to avoid hanging on unreachable IPs
                        socket.connect(InetSocketAddress(ip, port), 2000)
                        socket.soTimeout = 5000 // 5 seconds timeout for write operations
                        
                        BufferedOutputStream(socket.getOutputStream()).use { outputStream ->
                            Log.d(TAG, "Connected. Sending bitmap...")
                            // Use JPEG for faster transfer if quality isn't paramount, 
                            // but sticking to PNG as per requirement with compression.
                            val result = bitmap.compress(Bitmap.CompressFormat.PNG, 70, outputStream)
                            outputStream.flush()
                            Log.d(TAG, "Bitmap sent successfully to $ip")
                            result
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to $ip: ${e.localizedMessage}")
                false
            }

            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }
}
