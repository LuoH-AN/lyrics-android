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
        val controller = HomeTranslationController(scope, { missing ->
            calls++
            assertEquals(listOf(HomeTranslationController.MissingLine(1, "world")), missing)
            mapOf(0 to "wrong override", 1 to "世界", 9 to "unrequested")
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
        val controller = HomeTranslationController(scope, { calls++; emptyMap() }, {}, statuses::add)
        controller.update("song", document, "bilingual", "off")
        assertTrue(statuses.last().contains("设置补充翻译"))
        controller.update("chinese", LyricDocument(listOf(LyricLine(0, "让歌词陪着你")), true), "bilingual", "off")
        assertEquals("Chinese lyrics should not prompt for unnecessary machine translation", "", statuses.last())
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
        val controller = HomeTranslationController(scope, {
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
        val controller = HomeTranslationController(scope, {
            val result = if (count++ == 0) old else next
            withContext(NonCancellable) { result.await() }
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
}
