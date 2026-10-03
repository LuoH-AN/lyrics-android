package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeLyricMotionTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var scroll: ScrollView
    private lateinit var rows: List<LyricLineView>
    private lateinit var motion: HomeLyricMotion
    private var animationsEnabled = true

    @Before fun createLyrics() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = ContextThemeWrapper(controller.get(), R.style.Theme_DesktopLyrics)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
        }
        val track = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(160), dp(28), dp(240))
        }
        val phrases = listOf(
            "The night is young" to "夜色才刚刚开始",
            "Follow the melody" to "跟随这段旋律",
            "Stay here with me" to "陪我留在这一刻",
            "Let the music flow" to "让音乐缓缓流淌",
            "One more moment" to "再多停留一会儿",
            "Under the city lights" to "在城市的灯火下"
        )
        rows = (0 until 12).map { index ->
            val (original, translation) = phrases[index % phrases.size]
            LyricLineView(context).apply {
                textSize = 26f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, dp(12), 0, dp(12))
                setLineSpacing(0f, 1.2f)
                bind(LyricLine(index * 2000L, original, translation), "bilingual")
                track.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)))
            }
        }
        scroll.addView(track)
        controller.get().setContentView(scroll)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(480), View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, dp(320), dp(480))
        assertTrue(scroll.isAttachedToWindow)
        assertTrue(rows.all { it.height > 0 })
        motion = HomeLyricMotion(scroll, rows) { animationsEnabled }
        motion.moveTo(2, target(2), animate = false)
    }

    @After fun destroyLyrics() {
        motion.cancel()
        controller.pause().stop().destroy()
    }

    private fun target(index: Int) =
        (rows[index].top + rows[index].height / 2 - (scroll.height * .42f).toInt()).coerceAtLeast(0)

    private fun animator(): ValueAnimator = HomeLyricMotion::class.java.getDeclaredField("animator").let {
        it.isAccessible = true
        requireNotNull(it.get(motion) as ValueAnimator?)
    }

    private fun advanceTo(millis: Long) { animator().currentPlayTime = millis }
    private fun depthBlur(): LyricDepthBlur = HomeLyricMotion::class.java.getDeclaredField("depthBlur").let {
        it.isAccessible = true
        it.get(motion) as LyricDepthBlur
    }
    private fun finishFrames() = repeat(64) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun assertSettled(index: Int) {
        assertFalse(motion.isRunning)
        rows.forEachIndexed { i, row ->
            val distance = abs(i - index)
            val scale = when (distance) { 0 -> 1f; 1 -> .9f; 2 -> .84f; else -> .8f }
            val alpha = when (distance) { 0 -> 1f; 1 -> .64f; 2 -> .46f; 3 -> .32f; else -> .24f }
            assertEquals("Row $i must not retain motion offset", 0f, row.translationY, .001f)
            assertEquals(scale, row.scaleX, .001f)
            assertEquals(scale, row.scaleY, .001f)
            assertEquals(alpha, row.alpha, .001f)
        }
    }

    private fun preview(name: String) {
        val bitmap = Bitmap.createBitmap(scroll.width, scroll.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-scroll.scrollX.toFloat(), -scroll.scrollY.toFloat())
        scroll.draw(canvas)
        val output = File("build/reports/material-ui/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertForwardMotion(previewPrefix: String) {
        val from = scroll.scrollY
        val target = target(3)
        val originalText = rows.map { it.text.toString() }
        preview("$previewPrefix-before")
        motion.moveTo(3, target, animate = true)
        assertTrue(motion.isRunning)
        advanceTo(120L)
        assertTrue("Scroll must have an intermediate frame", scroll.scrollY in (from + 1) until target)
        assertTrue("Current row leads the next row", rows[4].translationY > rows[3].translationY + 1f)
        assertTrue("Current row gradually gains emphasis", rows[3].scaleX > .9f && rows[3].scaleX < 1f)
        assertTrue(rows[3].alpha > .64f && rows[3].alpha < 1f)
        assertTrue("Previous row gradually loses emphasis", rows[2].alpha < 1f && rows[2].alpha > .64f)
        assertEquals("Bilingual content must not be rebound during motion", originalText, rows.map { it.text.toString() })
        assertTrue(rows[3].text.toString().contains("\n让音乐缓缓流淌"))
        preview("$previewPrefix-middle")
        animator().end()
        assertEquals(target, scroll.scrollY)
        assertSettled(3)
        preview("$previewPrefix-after")
    }

    @Test fun forwardMotionStaggersBilingualRowsAndSettlesInLightTheme() = assertForwardMotion("home-motion-light")

    @Test
    @Config(qualifiers = "w320dp-h640dp-night")
    fun forwardMotionStaggersBilingualRowsAndSettlesInDarkTheme() = assertForwardMotion("home-motion-dark")

    @Test fun reverseMotionFollowsTheOppositeDirection() {
        motion.moveTo(4, target(4), animate = false)
        val from = scroll.scrollY
        val target = target(3)
        motion.moveTo(3, target, animate = true)
        advanceTo(120L)
        assertTrue(scroll.scrollY > target && scroll.scrollY < from)
        assertTrue("Delayed neighbors must lag downward travel", rows[4].translationY < rows[3].translationY - 1f)
        animator().end()
        assertEquals(target, scroll.scrollY)
        assertSettled(3)
    }

    @Test fun rapidRetargetStartsAtTheCurrentlyDisplayedTransforms() {
        motion.moveTo(3, target(3), animate = true)
        advanceTo(120L)
        val scrollBefore = scroll.scrollY
        val visible = rows.subList(1, 6)
        val before = visible.map { listOf(it.translationY, it.scaleX, it.alpha) }
        motion.moveTo(4, target(4), animate = true)
        advanceTo(0L)
        assertEquals(scrollBefore, scroll.scrollY)
        visible.forEachIndexed { index, row ->
            assertEquals(before[index][0], row.translationY, .001f)
            assertEquals(before[index][1], row.scaleX, .001f)
            assertEquals(before[index][2], row.alpha, .001f)
        }
        animator().end()
        assertEquals(target(4), scroll.scrollY)
        assertSettled(4)
        finishFrames()
        assertEquals("Canceled animation must not restore an old target", target(4), scroll.scrollY)
        assertSettled(4)
    }

    @Test fun cancelRemovesOffsetsAndDoesNotMoveTheUsersScrollLater() {
        motion.moveTo(3, target(3), animate = true)
        advanceTo(120L)
        val stoppedAt = scroll.scrollY
        motion.cancel()
        assertEquals(stoppedAt, scroll.scrollY)
        assertSettled(3)
        scroll.scrollTo(0, stoppedAt + 24)
        val manualPosition = scroll.scrollY
        finishFrames()
        assertEquals(manualPosition, scroll.scrollY)
        assertSettled(3)
    }

    @Test fun disabledAnimationsImmediatelySettleAnInterruptedTransition() {
        motion.moveTo(3, target(3), animate = true)
        advanceTo(100L)
        animationsEnabled = false
        motion.moveTo(4, target(4), animate = true)
        assertEquals(target(4), scroll.scrollY)
        assertSettled(4)
        finishFrames()
        assertEquals(target(4), scroll.scrollY)
    }

    @Test fun distantSeekSnapsRatherThanAnimatingTheWholeSong() {
        assertTrue(target(10) - scroll.scrollY > scroll.height)
        motion.moveTo(10, target(10), animate = true)
        assertEquals(target(10), scroll.scrollY)
        assertSettled(10)
    }

    @Test fun focusChangesWithoutFollowingPreserveManualScroll() {
        val manualPosition = scroll.scrollY
        motion.moveTo(3, null, animate = true)
        advanceTo(120L)
        assertEquals(manualPosition, scroll.scrollY)
        rows.forEach { assertEquals(0f, it.translationY, .001f) }
        animator().end()
        assertEquals(manualPosition, scroll.scrollY)
        assertSettled(3)
    }

    @Test fun activeLineHasABoundedSpringWithoutOpacityOvershoot() {
        motion.moveTo(3, target(3), animate = true)
        advanceTo(300L)
        assertTrue("Current line gently grows past its resting scale", rows[3].scaleX > 1f)
        assertTrue("The spring must remain subtle", rows[3].scaleX < 1.02f)
        rows.forEach { assertTrue(it.alpha in 0f..1f) }
        animator().end()
        assertSettled(3)
    }

    @Test fun longerTravelGetsMoreTimeWithoutAnUnboundedTail() {
        val from = scroll.scrollY
        motion.moveTo(3, from + 32, animate = true)
        val shortDuration = animator().duration
        motion.moveTo(2, target(2), animate = false)
        motion.moveTo(3, from + scroll.height * 3 / 4, animate = true)
        val longDuration = animator().duration
        assertTrue(longDuration > shortDuration)
        assertTrue("Staggered motion must finish in under a second", longDuration < 1_000L)
        animator().end()
        assertSettled(3)
    }

    @Test fun legacyDevicesKeepDepthWithoutNativeBlur() {
        rows.indices.forEach { assertEquals(0f, depthBlur().radiusOf(rows[it]), 0f) }
        motion.moveTo(3, target(3), animate = true)
        advanceTo(300L)
        rows.indices.forEach { assertEquals(0f, depthBlur().radiusOf(rows[it]), 0f) }
        animator().end()
        assertSettled(3)
    }

    @Test
    @Config(sdk = [28, 31])
    fun rowsCanArriveAfterTheMotionControllerAndGrowOnTheNextSong() {
        motion.cancel()
        val liveRows = mutableListOf<LyricLineView>()
        val liveMotion = HomeLyricMotion(scroll, liveRows) { animationsEnabled }
        liveRows.addAll(rows.take(3))
        liveMotion.moveTo(1, target(1), animate = false)
        assertEquals(1f, liveRows[1].scaleX, 0f)
        liveMotion.cancel()
        liveRows.clear()
        liveRows.addAll(rows)
        liveMotion.moveTo(7, target(7), animate = false)
        assertEquals(1f, liveRows[7].scaleX, 0f)
        assertEquals(target(7), scroll.scrollY)
        liveMotion.cancel()
    }

    @Test
    @Config(sdk = [31])
    fun nativeBlurIsSubtleKeepsTheCurrentLineSharpAndClearsForManualBrowsing() {
        val blur = depthBlur()
        assertEquals(0f, blur.radiusOf(rows[2]), 0f)
        assertEquals(0f, blur.radiusOf(rows[3]), 0f)
        assertEquals(.5f, blur.radiusOf(rows[4]), 0f)
        assertEquals(.875f, blur.radiusOf(rows[5]), 0f)
        assertEquals(1.25f, blur.radiusOf(rows[6]), 0f)
        motion.moveTo(4, target(4), animate = true)
        assertEquals("A newly focused line must be sharp immediately", 0f, blur.radiusOf(rows[4]), 0f)
        advanceTo(120L)
        rows.forEach { assertTrue(blur.radiusOf(it) in 0f..1.25f) }
        motion.cancel()
        rows.forEach { assertEquals(0f, blur.radiusOf(it), 0f) }
        motion.moveTo(4, target(4), animate = false)
        assertTrue(blur.radiusOf(rows[6]) > 0f)
        motion.moveTo(5, null, animate = true)
        rows.forEach { assertEquals(0f, blur.radiusOf(it), 0f) }
        animator().end()
        rows.forEach { assertEquals(0f, blur.radiusOf(it), 0f) }
    }

    @Test fun realChoreographerFramesFinishTheSharedTimeline() {
        motion.moveTo(3, target(3), animate = true)
        assertTrue(motion.isRunning)
        finishFrames()
        assertEquals(target(3), scroll.scrollY)
        assertSettled(3)
    }
}
