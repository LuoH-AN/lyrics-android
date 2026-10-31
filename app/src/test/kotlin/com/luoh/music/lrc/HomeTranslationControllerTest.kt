package com.luoh.music.lrc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class HomeTranslationControllerTest {
    private val document = LyricDocument(listOf(
        LyricLine(0, "hello", "你好"), LyricLine(2000, "world")), true)

    @Test fun fillsOnlyMissingTranslationsWithoutChangingTimingOrOfficialText() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val documents = mutableListOf<LyricDocument>()
        val statuses = mutableListOf<String>()
        var calls = 0
        val controller = HomeTranslationController(scope, { missing, required ->
            calls++
            required()
            assertEquals(listOf(HomeTranslationController.MissingLine(1, "world")), missing)
            HomeTranslationController.Result(mapOf(0 to "wrong override", 1 to "世界", 9 to "unrequested"))
        }, documents::add, statuses::add)
        controller.update("song", document, "bilingual", "offline")
        assertEquals(listOf("你好", "世界"), documents.single().lines.map { it.translation })
        assertEquals(document.lines.map { it.startMs }, documents.single().lines.map { it.startMs })
        assertEquals("", statuses.last())
        controller.update("song", document, "bilingual", "offline")
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test fun displayModeDoesNotSilentlyEnableMachineTranslation() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        var calls = 0
        val controller = HomeTranslationController(scope, { _, _ -> calls++; HomeTranslationController.Result() }, {}, statuses::add)
        controller.update("song", document, "bilingual", "off")
        assertEquals("Display mode must not nag users to enable machine translation", "", statuses.last())
        controller.update("chinese", LyricDocument(listOf(LyricLine(0, "让歌词陪着你")), true), "bilingual", "off")
        assertEquals("Chinese lyrics should not prompt for unnecessary machine translation", "", statuses.last())
        controller.update("chinese-with-name", LyricDocument(listOf(LyricLine(0, "Jay 陪你走过每个夜晚")), true), "bilingual", "off")
        assertEquals("", statuses.last())
        controller.update("song", document, "original", "api")
        assertEquals("", statuses.last())
        controller.update("song", document.copy(lines = document.lines.take(1)), "bilingual", "api")
        assertEquals(0, calls)
        scope.cancel()
    }

    @Test fun missingModelErrorsAreVisibleAndDoNotDiscardOfficialLyrics() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        val documents = mutableListOf<LyricDocument>()
        val controller = HomeTranslationController(scope, { _, required ->
            required()
            throw IllegalArgumentException("请先下载中文语言包")
        }, documents::add, statuses::add)
        controller.update("song", document, "bilingual", "offline")
        assertTrue(statuses.last().contains("请先下载中文语言包"))
        assertTrue(documents.isEmpty())
        scope.cancel()
    }

    @Test fun oldSongAndCancelledConfigurationCannotPublishLateResults() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val old = CompletableDeferred<Map<Int, String>>()
        val next = CompletableDeferred<Map<Int, String>>()
        var count = 0
        val documents = mutableListOf<LyricDocument>()
        val controller = HomeTranslationController(scope, { _, _ ->
            val result = if (count++ == 0) old else next
            HomeTranslationController.Result(withContext(NonCancellable) { result.await() })
        }, documents::add, {})
        controller.update("old", document, "bilingual", "api")
        controller.update("new", document, "bilingual", "api")
        old.complete(mapOf(1 to "旧歌译文"))
        assertTrue(documents.isEmpty())
        controller.cancel()
        next.complete(mapOf(1 to "切换配置前的译文"))
        assertTrue(documents.isEmpty())
        scope.cancel()
    }

    @Test fun chineseLyricsNeverStartTranslationOrShowAStatus() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        var calls = 0
        val controller = HomeTranslationController(scope, { _, _ -> calls++; HomeTranslationController.Result() },
            { fail("Chinese lyrics should remain unchanged") }, statuses::add)
        val chinese = LyricDocument(listOf(
            LyricLine(0, "让歌词陪着你"), LyricLine(2000, "讓歌詞陪著你"),
            LyricLine(4000, "𠮷，2026！♪"), LyricLine(6000, "已有歌词", "已有译文")), true)
        for (display in listOf("bilingual", "translated")) for (mode in listOf("off", "offline", "api")) {
            controller.update("chinese", chinese, display, mode)
        }
        assertEquals(0, calls)
        assertTrue(statuses.isNotEmpty() && statuses.all(String::isEmpty))
        scope.cancel()
    }

    @Test fun chineseIdentifiedWithLatinNamesIsSilentlySkipped() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        var calls = 0
        val controller = HomeTranslationController(scope, { _, _ ->
            calls++
            HomeTranslationController.Result(outcome = SupplementOutcome.NOT_NEEDED)
        }, { fail("Skipping must not insert a placeholder translation") }, statuses::add)
        val chinese = LyricDocument(listOf(LyricLine(0, "Jay 陪你走过每个夜晚"), LyricLine(2000, "让音乐陪你到天亮")), true)
        for (display in listOf("bilingual", "translated")) for (mode in listOf("offline", "api")) {
            controller.update("chinese", chinese, display, mode)
        }
        assertEquals(4, calls)
        assertTrue(statuses.all(String::isEmpty))
        scope.cancel()
    }

    @Test fun foreignLyricsStillTranslateAndActualEmptyResponsesRemainFailures() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        var calls = 0
        val controller = HomeTranslationController(scope, { _, required ->
            calls++
            required()
            HomeTranslationController.Result()
        }, {}, statuses::add)
        for (text in listOf("Hello world", "世界の歌", "노래")) {
            controller.update(text, LyricDocument(listOf(LyricLine(0, text)), true), "bilingual", "offline")
            assertTrue(statuses.last().contains("未获得补充译文"))
        }
        assertEquals(3, calls)
        scope.cancel()
    }

    @Test fun switchingToChineseClearsFailureAndRejectsLateProgressAndErrors() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val statuses = mutableListOf<String>()
        val old = CompletableDeferred<Unit>()
        var calls = 0
        lateinit var lateProgress: () -> Unit
        val controller = HomeTranslationController(scope, { _, required ->
            if (calls++ == 0) throw IllegalArgumentException("network error")
            lateProgress = required
            withContext(NonCancellable) {
                old.await()
                required()
                throw IllegalArgumentException("old error")
            }
        }, { fail("A stale request must not change the new song") }, statuses::add)
        val chinese = LyricDocument(listOf(LyricLine(0, "让歌词陪着你")), true)
        controller.update("failed", document, "bilingual", "api")
        assertTrue(statuses.last().contains("network error"))
        controller.update("chinese", chinese, "bilingual", "api")
        assertEquals("", statuses.last())
        controller.update("pending", document, "bilingual", "api")
        controller.update("chinese", chinese, "bilingual", "api")
        val count = statuses.size
        lateProgress()
        old.complete(Unit)
        assertEquals(count, statuses.size)
        assertEquals("", statuses.last())
        scope.cancel()
    }
}
