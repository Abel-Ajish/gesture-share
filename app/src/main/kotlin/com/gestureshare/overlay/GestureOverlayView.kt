package com.gestureshare.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Custom overlay view that captures gestures but allows events to be handled 
 * or passed through based on internal logic.
 */
class GestureOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onGestureListener: ((MotionEvent) -> Unit)? = null

    fun setOnGestureListener(listener: (MotionEvent) -> Unit) {
        this.onGestureListener = listener
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Forward to our gesture detector
        onGestureListener?.invoke(ev)
        
        // Return false here to let the WindowManager know we didn't consume the touch.
        // This is key for FLAG_NOT_TOUCH_MODAL to work.
        return false
    }
}
