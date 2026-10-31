package com.luoh.music.lrc

import android.content.Context
import java.io.File

/** Only disposable translations and share images; never settings, custom lyrics or language models. */
internal object TemporaryCache {
    private const val DAY_MS = 86_400_000L
    private val lock = Any()

    fun prune(context: Context) {
        pruneTranslations(context)
        pruneCards(context)
    }

    fun pruneTranslations(context: Context) = pruneDirectory(
        File(context.cacheDir, "translations"), 4 * 1024 * 1024L, 1024, 30 * DAY_MS)

    fun pruneCards(context: Context, protectedFile: File? = null) = pruneDirectory(
        File(context.cacheDir, "lyric_cards"), 16 * 1024 * 1024L, 8, DAY_MS, protectedFile = protectedFile)

    fun clear(context: Context): Boolean = synchronized(lock) {
        val files = listOf("translations", "lyric_cards").flatMap {
            File(context.cacheDir, it).listFiles()?.filter(File::isFile).orEmpty()
        }
        var cleared = true
        files.forEach { if (!it.delete() && it.exists()) cleared = false }
        cleared
    }

    internal fun pruneDirectory(directory: File, maxBytes: Long, maxFiles: Int, maxAgeMs: Long,
        now: Long = System.currentTimeMillis(), protectedFile: File? = null) = synchronized(lock) {
        val files = directory.listFiles()?.filter(File::isFile)?.sortedBy(File::lastModified).orEmpty()
        val remaining = files.filter { file ->
            if (file != protectedFile && now - file.lastModified() > maxAgeMs) {
                !file.delete() && file.exists()
            } else true
        }
        // Tiny per-line JSON files still consume a filesystem block on Android.
        fun occupied(file: File) = file.length().coerceAtLeast(4096L)
        var bytes = remaining.sumOf(::occupied)
        var count = remaining.size
        for (file in remaining) {
            if (bytes <= maxBytes && count <= maxFiles) break
            val size = occupied(file)
            if (file != protectedFile && file.delete()) {
                bytes -= size
                count--
            }
        }
    }
}
