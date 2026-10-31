package com.luoh.music.lrc

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TemporaryCacheTest {
    private val root = Files.createTempDirectory("lyrics-cache-test").toFile()
    private val now = 1_800_000_000_000L

    @After fun removeTestFiles() { root.deleteRecursively() }

    private fun file(name: String, bytes: Int, age: Long = 0) = File(root, name).apply {
        writeBytes(ByteArray(bytes))
        assertTrue(setLastModified(now - age))
    }

    @Test fun expiryAndSizeLimitsRemoveOldestFilesAndKeepRecentTranslations() {
        val expired = file("expired", 8192, 2000)
        val oldest = file("oldest", 8192, 900)
        val middle = file("middle", 8192, 500)
        val recent = file("recent", 8192, 100)
        TemporaryCache.pruneDirectory(root, 16_384, 10, 1000, now)
        assertFalse(expired.exists())
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(recent.exists())
        assertEquals(16_384L, root.listFiles()!!.sumOf(File::length))
    }

    @Test fun tinyJsonFilesCountAsBlocksAndFileCountAlsoHasALimit() {
        file("first", 10, 300)
        val middle = file("middle", 10, 200)
        val last = file("last", 10, 100)
        TemporaryCache.pruneDirectory(root, 8192, 100, 1000, now)
        assertEquals(setOf("middle", "last"), root.listFiles()!!.map(File::getName).toSet())
        TemporaryCache.pruneDirectory(root, Long.MAX_VALUE, 1, 1000, now)
        assertFalse(middle.exists())
        assertTrue(last.exists())
    }

    @Test fun currentShareRemainsReadableEvenIfItExceedsTheBudgetAndDirectoriesAreUntouched() {
        val current = file("current.png", 32_768, 100)
        val old = file("old.png", 8192, 200)
        val unrelated = File(root, "language_models").apply { mkdirs() }
        val model = File(unrelated, "downloaded.model").apply { writeText("model") }
        TemporaryCache.pruneDirectory(root, 8192, 1, 1000, now, protectedFile = current)
        assertTrue(current.exists())
        assertFalse(old.exists())
        assertEquals("model", model.readText())
    }
}
