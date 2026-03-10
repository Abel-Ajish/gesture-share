package com.gestureshare.overlay

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.gestureshare.MainActivity
import com.gestureshare.R
import com.gestureshare.gesture.CircleDetector
import com.gestureshare.network.Receiver
import com.gestureshare.network.Sender
import com.gestureshare.screenshot.ScreenCapture
import com.gestureshare.ui.ReceiveAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * OverlayService is a foreground service that runs a transparent overlay on top of other apps.
 * Improved for production with better lifecycle and error handling.
 */
class OverlayService : LifecycleService() {

    private val TAG = "OverlayService"
    private val CHANNEL_ID = "GestureShareChannel"
    private val NOTIFICATION_ID = 1

    private lateinit var windowManager: WindowManager
    private var overlayView: GestureOverlayView? = null
    private lateinit var circleDetector: CircleDetector
    private lateinit var screenCapture: ScreenCapture
    private lateinit var sender: Sender
    private lateinit var receiver: Receiver
    private lateinit var receiveAnimation: ReceiveAnimation

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        circleDetector = CircleDetector()
        screenCapture = ScreenCapture(this)
        sender = Sender()
        
        receiver = Receiver { bitmap ->
            Log.d(TAG, "Received screenshot from another device")
            showReceivedScreenshot(bitmap)
        }
        receiver.start()
        
        receiveAnimation = ReceiveAnimation(this, windowManager)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Ensure we have overlay permission before adding view
        if (Settings.canDrawOverlays(this)) {
            setupOverlay()
        } else {
            Log.e(TAG, "Overlay permission lost, stopping service.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("RESULT_DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("RESULT_DATA")
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            mediaProjection?.let { 
                screenCapture.setMediaProjection(it)
                Log.d(TAG, "MediaProjection set successfully")
            }
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        try {
            overlayView = GestureOverlayView(this).apply {
                setOnGestureListener { event ->
                    handleTouch(event)
                }
            }

            // The secret to touch pass-through while detecting gestures:
            // 1. TYPE_APPLICATION_OVERLAY: The correct type for overlays.
            // 2. FLAG_NOT_FOCUSABLE: So keyboard/IME doesn't focus on our overlay.
            // 3. FLAG_NOT_TOUCH_MODAL: Crucial for passing touches to background.
            // 4. FLAG_WATCH_OUTSIDE_TOUCH: Helps watch touches that happen outside bounds (though we are full screen).
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay added to window manager with pass-through flags")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay: ${e.localizedMessage}")
            Toast.makeText(this, "Failed to initialize overlay", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                circleDetector.reset()
                circleDetector.addPoint(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                circleDetector.addPoint(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                circleDetector.addPoint(event.x, event.y)
                if (circleDetector.isCircle()) {
                    Log.d(TAG, "Circle gesture detected, capturing screen...")
                    captureAndSend()
                }
            }
        }
    }

    private fun captureAndSend() {
        screenCapture.capture { bitmap ->
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
            // Broadcast to typical 254 IPs on the subnet
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

    private fun showReceivedScreenshot(bitmap: Bitmap) {
        receiveAnimation.show(bitmap)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // Low priority to be less intrusive
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy")
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay during destroy: ${e.localizedMessage}")
        }
        receiver.stop()
        screenCapture.release()
    }
}
