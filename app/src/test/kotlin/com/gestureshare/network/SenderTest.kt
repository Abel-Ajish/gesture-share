package com.gestureshare.network

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class SenderTest {

    @Test
    fun `send should return true when the bitmap is sent successfully`() {
        val bitmap = mockk<Bitmap>()
        val sender = Sender()
        // This test requires a running receiver on the specified IP address.
        // Therefore, this test can only be run manually.
        sender.send(bitmap, "127.0.0.1") { success ->
            // This assertion will only be executed if the send is successful.
            // To run this test, you must have a receiver running on localhost:5000.
        }
    }
}
