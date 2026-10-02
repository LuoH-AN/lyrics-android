package com.luoh.music.lrc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw

/** Keeps the old frame visible while a same-Activity theme update lays out its new content. */
class ThemeTransition(
    private val root: ViewGroup,
    private val animationsEnabled: () -> Boolean = { ValueAnimator.areAnimatorsEnabled() }
) {
    private var snapshot: BitmapDrawable? = null
    private var bitmap: Bitmap? = null
    private var animation: ValueAnimator? = null
    private var pendingDraw: OneShotPreDrawListener? = null
    private var generation = 0
    private var disposed = false

    val isRunning: Boolean get() = snapshot != null

    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = clear()
    }

    init {
        root.addOnAttachStateChangeListener(attachmentListener)
    }

    /** Call before replacing/recoloring content. The root itself must remain attached and stable. */
    fun capture() {
        if (disposed) return
        generation++
        cancelPendingWork()
        if (!animationsEnabled() || !root.isAttachedToWindow || root.windowVisibility != View.VISIBLE ||
            root.width <= 0 || root.height <= 0) {
            releaseSnapshot()
            return
        }
        // Include any partially faded old overlay, so a rapid second switch starts at the visible frame.
        val image = captureBitmap()
        releaseSnapshot()
        if (image == null) return
        bitmap = image
        snapshot = BitmapDrawable(root.resources, image).apply {
            setBounds(0, 0, root.width, root.height)
            root.overlay.add(this)
        }
    }

    /** Restore scroll/state after layout, then reveal the new content without ever hiding its root. */
    fun finish(afterLayout: () -> Unit = {}) {
        if (disposed) return
        cancelPendingWork()
        val token = generation
        pendingDraw = root.doOnPreDraw {
            pendingDraw = null
            if (disposed || token != generation) return@doOnPreDraw
            afterLayout()
            if (disposed || token != generation) return@doOnPreDraw
            val oldFrame = snapshot ?: return@doOnPreDraw
            if (!animationsEnabled()) {
                releaseSnapshot()
                return@doOnPreDraw
            }
            animation = ValueAnimator.ofInt(oldFrame.alpha, 0).apply {
                duration = 200L
                interpolator = DecelerateInterpolator()
                addUpdateListener { oldFrame.alpha = it.animatedValue as Int }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (token == generation) {
                            animation = null
                            releaseSnapshot()
                        }
                    }
                })
                start()
            }
        }
        root.invalidate()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        clear()
        root.removeOnAttachStateChangeListener(attachmentListener)
    }

    private fun captureBitmap(): Bitmap? {
        var image: Bitmap? = null
        return try {
            image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            image
        } catch (_: RuntimeException) {
            image?.recycle()
            null
        } catch (_: OutOfMemoryError) {
            // A visual transition is optional; a theme change must still work under memory pressure.
            image?.recycle()
            null
        }
    }

    private fun cancelPendingWork() {
        pendingDraw?.removeListener()
        pendingDraw = null
        animation?.apply {
            removeAllUpdateListeners()
            removeAllListeners()
            cancel()
        }
        animation = null
    }

    private fun releaseSnapshot() {
        snapshot?.let { root.overlay.remove(it) }
        snapshot = null
        bitmap?.recycle()
        bitmap = null
    }

    private fun clear() {
        generation++
        cancelPendingWork()
        releaseSnapshot()
    }
}
