package com.gestureshare.screenshot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * ScreenCapture handles capturing the screen using the MediaProjection API with production-level error handling.
 */
class ScreenCapture(private val context: Context) {

    private val TAG = "ScreenCapture"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = DisplayMetrics()

    init {
        updateDisplayMetrics()
    }

    private fun updateDisplayMetrics() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            displayMetrics.widthPixels = bounds.width()
            displayMetrics.heightPixels = bounds.height()
            displayMetrics.densityDpi = context.resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        }
    }

    /**
     * Sets the MediaProjection object after user grants permission.
     */
    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
        // Register callback to handle projection stopping (e.g., from notification)
        this.mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                release()
            }
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Captures the screen and returns a Bitmap via callback.
     */
    @SuppressLint("WrongConstant")
    fun capture(callback: (Bitmap?) -> Unit) {
        Log.d(TAG, "Starting screen capture")
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized.")
            callback(null)
            return
        }

        updateDisplayMetrics()
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        try {
            // Use RGBA_8888 for high quality
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "GestureShareCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                var bitmap: Bitmap? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        Log.d(TAG, "Image acquired")
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        // Ensure we create a bitmap with correct dimensions even if there's padding
                        val rawBitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        rawBitmap.copyPixelsFromBuffer(buffer)
                        
                        // Crop padding if necessary
                        if (rowPadding != 0) {
                            bitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                            rawBitmap.recycle()
                        } else {
                            bitmap = rawBitmap
                        }
                        Log.d(TAG, "Bitmap created")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing captured image: ${e.localizedMessage}")
                } finally {
                    image?.close()
                    stopCapture() // Release VirtualDisplay and ImageReader after one successful capture
                    callback(bitmap)
                }
            }, Handler(Looper.getMainLooper()))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture: ${e.localizedMessage}")
            stopCapture()
            callback(null)
        }
    }

    /**
     * Stops the current capture and releases temporary resources.
     */
    private fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.localizedMessage}")
        }
    }

    /**
     * Releases all resources including MediaProjection.
     */
    fun release() {
        stopCapture()
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection: ${e.localizedMessage}")
        }
        mediaProjection = null
    }
}
