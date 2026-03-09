package com.gestureshare.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.gestureshare.R

/**
 * ReceiveAnimation handles the visual effects when a screenshot is received.
 */
class ReceiveAnimation(private val context: Context, private val windowManager: WindowManager) {

    private val TAG = "ReceiveAnimation"
    private var animationView: View? = null
    private var isShowing = false

    /**
     * Shows the received screenshot with animation.
     */
    @SuppressLint("InflateParams")
    fun show(bitmap: Bitmap) {
        if (isShowing) {
            Log.d(TAG, "Already showing animation, ignoring new screenshot.")
            return
        }
        isShowing = true

        val inflater = LayoutInflater.from(context)
        animationView = inflater.inflate(R.layout.receive_animation, null)

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
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val ivScreenshot = animationView!!.findViewById<ImageView>(R.id.iv_screenshot)
        val vGlowCircle = animationView!!.findViewById<View>(R.id.v_glow_circle)

        ivScreenshot.setImageBitmap(bitmap)
        
        windowManager.addView(animationView, params)

        // Start animation
        startAnimations(ivScreenshot, vGlowCircle)
    }

    private fun startAnimations(ivScreenshot: ImageView, vGlowCircle: View) {
        // Glowing circle expands
        val scaleX = ObjectAnimator.ofFloat(vGlowCircle, View.SCALE_X, 0.5f, 20f)
        val scaleY = ObjectAnimator.ofFloat(vGlowCircle, View.SCALE_Y, 0.5f, 20f)
        val alphaGlow = ObjectAnimator.ofFloat(vGlowCircle, View.ALPHA, 0f, 0.8f, 0f)
        
        // Screenshot fades in
        val alphaScreenshot = ObjectAnimator.ofFloat(ivScreenshot, View.ALPHA, 0f, 1f)
        
        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaGlow, alphaScreenshot)
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
        }

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                // Keep the screenshot visible for a few seconds before removing
                Handler(Looper.getMainLooper()).postDelayed({
                    removeView()
                }, 3000)
            }
        })

        animatorSet.start()
    }

    private fun removeView() {
        try {
            if (animationView != null && animationView?.parent != null) {
                windowManager.removeView(animationView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing animation view: ${e.message}")
        } finally {
            animationView = null
            isShowing = false
        }
    }
}
