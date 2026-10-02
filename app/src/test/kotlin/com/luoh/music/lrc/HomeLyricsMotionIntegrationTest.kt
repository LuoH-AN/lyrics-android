package com.luoh.music.lrc

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.view.ContextThemeWrapper
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeLyricsMotionIntegrationTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var home: HomeLyricsView
    private lateinit var scroll: ScrollView
    private lateinit var track: LinearLayout
    private val snapshot = HomeLyricsView.Snapshot(track = "Song", positionMs = 2000L, durationMs = 30000L)

    @Before fun createHome() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        home = HomeLyricsView(ContextThemeWrapper(controller.get(), R.style.Theme_DesktopLyrics))
        controller.get().setContentView(home)
        home.setSnapshot(snapshot, forcePosition = true)
        home.setLyrics(LyricDocument((0..11).map { LyricLine(it * 2000L, "Lyric line $it", "第 $it 句歌词") }, true))
        repeat(2) {
            home.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
            home.layout(0, 0, 320, 640)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
        scroll = home.findViewById(R.id.home_lyrics_scroll)
        track = home.findViewById(R.id.home_lyrics_track)
        home.setSnapshot(snapshot)
        home.setActive(true)
    }

    @After fun destroyHome() {
        home.setActive(false)
        controller.pause().stop().destroy()
    }

    private fun motion(): HomeLyricMotion = HomeLyricsView::class.java.getDeclaredField("lyricMotion").let {
        it.isAccessible = true
        it.get(home) as HomeLyricMotion
    }

    private fun startTransition() {
        home.setSnapshot(snapshot.copy(positionMs = 4000L))
        assertTrue("Home must start motion when the focused lyric changes", motion().isRunning)
        val animator = HomeLyricMotion::class.java.getDeclaredField("animator").let {
            it.isAccessible = true
            it.get(motion()) as ValueAnimator
        }
        animator.currentPlayTime = 120L
    }

    private fun advanceFrames() = repeat(64) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun assertResting() {
        assertFalse(motion().isRunning)
        for (i in 0 until track.childCount) assertEquals(0f, track.getChildAt(i).translationY, .001f)
    }

    @Test fun homeStartsMotionAndPauseCancelsIt() {
        startTransition()
        assertTrue((0 until track.childCount).any { track.getChildAt(it).translationY > 1f })
        home.setActive(false)
        val stopped = scroll.scrollY
        assertResting()
        advanceFrames()
        assertEquals(stopped, scroll.scrollY)
        assertResting()
    }

    @Test fun touchingLyricsStopsMotionAndKeepsManualBrowsePosition() {
        startTransition()
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        scroll.dispatchTouchEvent(event)
        event.recycle()
        assertResting()
        scroll.scrollTo(0, scroll.scrollY + 20)
        val manual = scroll.scrollY
        home.setSnapshot(snapshot.copy(positionMs = 6000L))
        advanceFrames()
        assertEquals(manual, scroll.scrollY)
        assertResting()
        val up = MotionEvent.obtain(now, now + 1000L, MotionEvent.ACTION_UP, 100f, 100f, 0)
        scroll.dispatchTouchEvent(up)
        up.recycle()
    }

    @Test fun clickingALyricSeeksWithoutKeepingOldMotion() {
        startTransition()
        track.getChildAt(8).performClick()
        val target = scroll.scrollY
        assertResting()
        assertEquals(1f, track.getChildAt(8).alpha, 0f)
        advanceFrames()
        assertEquals(target, scroll.scrollY)
        assertResting()
    }

    @Test fun replacingLyricsCancelsOldRows() {
        startTransition()
        val oldRows = (0 until track.childCount).map { track.getChildAt(it) }
        home.setLyrics(LyricDocument(listOf(LyricLine(0, "Replacement")), true))
        assertFalse(motion().isRunning)
        oldRows.forEach { assertEquals(0f, it.translationY, .001f) }
        assertEquals(1, track.childCount)
    }

    @Test fun detachingHomeCancelsMotion() {
        startTransition()
        (home.parent as ViewGroup).removeView(home)
        assertResting()
        advanceFrames()
        assertResting()
    }
}
