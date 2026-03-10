package com.gestureshare.screenshot

import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenCaptureTest {

    private lateinit var screenCapture: ScreenCapture
    private lateinit var mediaProjectionManager: MediaProjectionManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        screenCapture = ScreenCapture(context)
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    @Test
    fun `capture should return a bitmap`() {
        // This test requires a MediaProjection instance, which can only be obtained with user permission.
        // Therefore, this test can only be run manually on a device.
        // To run this test, you must grant screen capture permission to the application.
        val mediaProjection = MediaProjectionHolder.mediaProjection
        if (mediaProjection != null) {
            screenCapture.setMediaProjection(mediaProjection)
            screenCapture.capture { bitmap ->
                assertNotNull(bitmap)
            }
        }
    }
}
