package com.luoh.music.lrc

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal typealias AgoraFloat = Animatable<Float, AnimationVector1D>

/** Use Agora's named Compose constants, not numeric guesses for similarly named Android springs. */
internal fun agoraPageSpring(): SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy, // .75, not DampingRatioMediumBouncy (.5).
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = .001f
)

/**
 * One frame clock and one cancellable coroutine tree per host, as in Agora's LaunchedEffect.
 * Compose owns spring physics, tween timing, velocity reset, cancellation and convergence.
 * BroadcastFrameClock delivers the same Choreographer timestamp to all six page animations.
 */
internal class AgoraTransitionRuntime(context: Context) {
    private val choreographer = Choreographer.getInstance()
    private var scheduled = false
    private var disposed = false
    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { time ->
        scheduled = false
        if (!disposed) clock.sendFrame(time)
    }
    private val clock: BroadcastFrameClock = BroadcastFrameClock {
        if (!disposed && !scheduled) {
            scheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }
    private val resolver = context.applicationContext.contentResolver
    private fun readDurationScale() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        .coerceAtLeast(0f)
    private var cachedDurationScale = readDurationScale()
    var allowSpatialMotion = allowAgoraSpatialMotion(context)
        private set
    var onPolicyChanged: () -> Unit = {}
    private val scaleKeys = listOf(Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE)
    private val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            cachedDurationScale = readDurationScale()
            allowSpatialMotion = scaleKeys.none { Settings.Global.getFloat(resolver, it, 1f) == 0f }
            onPolicyChanged()
        }
    }
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = cachedDurationScale
    }
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob() + clock + durationScale)
    private var transition: Job? = null

    init { scaleKeys.forEach { resolver.registerContentObserver(Settings.Global.getUriFor(it), false, scaleObserver) } }

    fun start(block: suspend CoroutineScope.() -> Unit) {
        cancel()
        if (!disposed) {
            cachedDurationScale = readDurationScale()
            allowSpatialMotion = scaleKeys.none { Settings.Global.getFloat(resolver, it, 1f) == 0f }
            transition = scope.launch(block = block)
        }
    }
    fun cancel() { transition?.cancel(); transition = null }
    fun dispose() {
        if (disposed) return
        disposed = true
        cancel()
        scope.cancel()
        choreographer.removeFrameCallback(frameCallback)
        resolver.unregisterContentObserver(scaleObserver)
        scheduled = false
    }
}

internal suspend fun animateAgora(
    value: AgoraFloat,
    target: Float,
    spec: AnimationSpec<Float>,
    render: () -> Unit
) {
    value.animateTo(target, animationSpec = spec) { render() }
}
