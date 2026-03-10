package com.gestureshare.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.gestureshare.gesture.CircleDetector
import com.gestureshare.network.Sender
import com.gestureshare.screenshot.MediaProjectionHolder
import com.gestureshare.screenshot.ScreenCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

class GestureAccessibilityService : AccessibilityService() {

    private val TAG = "GestureAccessibilityService"
    private lateinit var circleDetector: CircleDetector
    private lateinit var screenCapture: ScreenCapture
    private lateinit var sender: Sender
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
        circleDetector = CircleDetector()
        screenCapture = ScreenCapture(this)
        sender = Sender()

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                circleDetector.reset()
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                if (circleDetector.isCircle()) {
                    Log.d(TAG, "Circle gesture detected!")
                    captureAndSend()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    private fun captureAndSend() {
        val mediaProjection = MediaProjectionHolder.mediaProjection
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized.")
            return
        }

        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        screenCapture.setMediaProjection(mediaProjection)
        screenCapture.capture { bitmap ->
            stopService(serviceIntent)
            if (bitmap != null) {
                Log.d(TAG, "Screenshot captured, broadcasting...")
                broadcastToLocalSubnet(bitmap)
            } else {
                Log.e(TAG, "Failed to capture screenshot")
            }
        }
    }

    private fun broadcastToLocalSubnet(bitmap: Bitmap) {
        serviceScope.launch(Dispatchers.IO) {
            val localIP = getLocalIPAddress()
            if (localIP == null) {
                Log.e(TAG, "No local IP address found. Are you connected to Wi-Fi?")
                return@launch
            }

            val prefix = localIP.substringBeforeLast(".")
            for (i in 1..254) {
                val targetIP = "$prefix.$i"
                if (targetIP != localIP) {
                    sender.send(bitmap, targetIP) { success ->
                        if (success) Log.d(TAG, "Sent screenshot to $targetIP")
                    }
                }
            }
        }
    }

    private fun getLocalIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.localizedMessage}")
        }
        return null
    }
}
