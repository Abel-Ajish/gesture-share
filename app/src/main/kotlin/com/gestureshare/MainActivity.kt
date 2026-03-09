package com.gestureshare

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gestureshare.databinding.ActivityMainBinding
import com.gestureshare.overlay.OverlayService

/**
 * MainActivity handles production-level permission requests and service management.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission granted")
            requestMediaProjection()
        } else {
            showError("Overlay permission is required to detect gestures.")
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d(TAG, "MediaProjection permission granted")
            startOverlayService(result.resultCode, result.data!!)
        } else {
            showError("Screen capture permission is required to share screenshots.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAction.setOnClickListener {
            if (isServiceRunning(OverlayService::class.java)) {
                stopOverlayService()
            } else {
                checkPermissionsAndStart()
            }
        }
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            showError("Failed to request screen capture: ${e.localizedMessage}")
        }
    }

    private fun startOverlayService(resultCode: Int, resultData: Intent) {
        try {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("RESULT_DATA", resultData)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            // UI will update on next resume or via listener if we had one
            updateUI()
        } catch (e: Exception) {
            showError("Failed to start service: ${e.localizedMessage}")
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        updateUI()
    }

    private fun updateUI() {
        val running = isServiceRunning(OverlayService::class.java)
        binding.tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        binding.btnAction.text = if (running) getString(R.string.btn_stop) else getString(R.string.btn_start)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }
}
