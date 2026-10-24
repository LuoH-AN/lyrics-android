package com.luoh.music.lrc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedLyricsCacheTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var now = 1_800_000_000_000L

    @Before fun clearCaches() {
        for (name in listOf(SharedLyricsCache.PREFS, "home_lyric_cache_v1")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun activityAndServiceReuseRichLyricsAcrossAlbumUpdatesAndRespectExpiry() {
        val home = SharedLyricsCache(context) { now }
        val overlay = SharedLyricsCache(context) { now }
        var requests = 0
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:00.00]hello", translatedLyrics = "[00:00.00]你好",
            wordLyrics = "[0,1000](0,1000)hello", durationMs = 10_000,
            source = "QQ音乐", recordId = "qq-1", cover = "https://example.org/cover",
            alternatives = listOf(DirectLyricsRepository.Result(
                lyrics = "[00:00.00]hello", source = "LRCLIB", recordId = "lrc-1")),
        )
        assertEquals(result, home.resolve(" Song ", "Artist", "", 10_000) { requests++; result })
        assertEquals(result, overlay.resolve("song", "artist", "Late album", 10_000) { requests++; result })
        assertEquals(1, requests)
        assertNull(overlay.read("song", "artist", durationMs = 30_000))
        now += 15 * 86_400_000L
        assertNull(home.read("song", "artist"))
    }

    @Test fun legacyLyricsMigrateAndCustomLyricsNeverPolluteTheProviderCache() {
        val old = JSONObject().put("at", now).put("lyrics", "[00:00.00]old")
            .put("translated", "[00:00.00]旧").put("word", "[0,1000](0,1000)old")
            .put("source", "QQ音乐").put("recordId", "old-1")
        context.getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).edit()
            .putString("Song\u0000Artist\u0000Old album", old.toString()).commit()
        val cache = SharedLyricsCache(context) { now }
        val migrated = requireNotNull(cache.read("song", "artist", "New album"))
        assertEquals("[00:00.00]旧", migrated.translatedLyrics)
        context.getSharedPreferences("home_lyric_cache_v1", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(migrated, SharedLyricsCache(context) { now }.read("Song", "Artist"))
        cache.resolve("Custom", "Artist", "", 0) {
            DirectLyricsRepository.Result(lyrics = "[00:00.00]mine", source = CustomLyricsStore.SOURCE)
        }
        assertNull(cache.read("Custom", "Artist"))
    }
}
