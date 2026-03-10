package com.gestureshare

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gestureshare.databinding.ActivityMainBinding
import com.gestureshare.screenshot.MediaProjectionHolder

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d(TAG, "MediaProjection permission granted")
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                MediaProjectionHolder.mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
            } else {
                showError("Screen capture permission is required to share screenshots.")
            }
        } catch (e: Exception) {
            showError("Failed to get MediaProjection: ${e.localizedMessage}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAction.setOnClickListener {
            checkAndRequestPermissions()
        }

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

    private fun checkAndRequestPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Toast.makeText(this, "Please enable the GestureShare accessibility service", Toast.LENGTH_LONG).show()
            startActivity(intent)
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

    private fun updateUI() {
        val running = isAccessibilityServiceEnabled()
        binding.tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        binding.btnAction.text = if (running) "Permissions Granted" else "Grant Permissions"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
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
