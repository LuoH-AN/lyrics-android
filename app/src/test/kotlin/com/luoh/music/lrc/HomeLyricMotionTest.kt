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
    private fun finishFrames() = repeat(64) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun assertSettled(index: Int) {
        assertFalse(motion.isRunning)
        rows.forEachIndexed { i, row ->
            val distance = abs(i - index)
            val scale = when (distance) { 0 -> 1f; 1 -> .92f; 2 -> .87f; else -> .82f }
            val alpha = when (distance) { 0 -> 1f; 1 -> .7f; 2 -> .55f; 3 -> .4f; else -> .26f }
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
        assertTrue("Current row gradually gains emphasis", rows[3].scaleX > .92f && rows[3].scaleX < 1f)
        assertTrue(rows[3].alpha > .7f && rows[3].alpha < 1f)
        assertTrue("Previous row gradually loses emphasis", rows[2].alpha < 1f && rows[2].alpha > .7f)
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

    @Test fun realChoreographerFramesFinishTheSharedTimeline() {
        motion.moveTo(3, target(3), animate = true)
        assertTrue(motion.isRunning)
        finishFrames()
        assertEquals(target(3), scroll.scrollY)
        assertSettled(3)
    }
}
