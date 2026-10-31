package com.luoh.music.lrc

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/** Frozen song metadata: a track change while picking lines cannot change the exported card. */
internal data class LyricShareDraft(
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val lines: List<LyricLine>,
    val initialIndex: Int,
)

internal object LyricShareCard {
    const val MAX_LINES = 6
    const val MAX_CHARACTERS = 1200

    fun selectedLines(draft: LyricShareDraft, indices: Set<Int>) = draft.lines.filterIndexed { index, _ -> index in indices }

    fun excerpt(lines: List<LyricLine>, translated: Boolean): String = lines.joinToString("\n\n") { line ->
        listOf(line.text, if (translated) line.translation else "")
            .filter { it.isNotBlank() }.joinToString("\n")
    }

    fun fits(lines: List<LyricLine>, translated: Boolean) =
        lines.size in 1..MAX_LINES && excerpt(lines, translated).length <= MAX_CHARACTERS

    fun snapshotCover(cover: Bitmap?): Bitmap? {
        if (cover == null || cover.isRecycled) return null
        val source = if (cover.config == Bitmap.Config.HARDWARE) requireNotNull(cover.copy(Bitmap.Config.ARGB_8888, false)) else cover
        return try {
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
                val side = minOf(source.width, source.height)
                val left = (source.width - side) / 2
                val top = (source.height - side) / 2
                Canvas(bitmap).drawBitmap(source, Rect(left, top, left + side, top + side), Rect(0, 0, 512, 512),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        } finally { if (source !== cover) source.recycle() }
    }

    /** The same bitmap drives preview, PNG saving and sharing; lyrics are never ellipsized. */
    fun render(context: Context, draft: LyricShareDraft, lines: List<LyricLine>, translated: Boolean): Bitmap {
        require(fits(lines, translated)) { "请选择最多 6 句、共 1200 字以内的歌词" }
        val width = 900
        val margin = 72
        val textWidth = width - margin * 2
        val foreground = Color.WHITE
        val secondary = Color.rgb(229, 229, 229)
        fun layout(text: String, size: Float, color: Int, centered: Boolean = false,
                   serif: Boolean = false, maxLines: Int = Int.MAX_VALUE): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(if (serif) "serif" else "sans-serif", Typeface.NORMAL)
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(8f, 1.25f).setMaxLines(maxLines)
                .apply { if (maxLines != Int.MAX_VALUE) setEllipsize(TextUtils.TruncateAt.END) }.build()
        }
        val title = layout(draft.title, 43f, foreground, maxLines = 2)
        val artist = draft.artist.takeIf { it.isNotBlank() }?.let { layout(it, 25f, secondary, maxLines = 2) }
        val bodies = lines.map { line -> buildList {
            add(layout(line.text, 49f, foreground, centered = true, serif = true))
            if (translated && line.translation.isNotBlank()) add(layout(line.translation, 29f, secondary, centered = true))
        } }
        val cover = draft.cover?.takeIf { !it.isRecycled }
        val headerBottom = margin + title.height + (artist?.let { 18 + it.height } ?: 0)
        val bodyHeight = bodies.sumOf { group -> group.sumOf { it.height } + (group.size - 1) * 12 } + (bodies.size - 1) * 40
        // Keep the lyric block vertically centered while reserving space for both header and icon.
        val iconSize = 70
        val clearance = maxOf(headerBottom + 100, margin + iconSize + 100)
        val height = maxOf(1200, bodyHeight + clearance * 2)
        require(height <= 6400) { "歌词过长，请减少选句" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(39, 39, 39))
        if (cover != null) {
            val background = blurredCover(cover, width, height)
            try {
                canvas.drawBitmap(background, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            } finally { background.recycle() }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.argb(119, 0, 0, 0), Color.argb(99, 0, 0, 0), Color.argb(99, 0, 0, 0)),
                floatArrayOf(0f, minOf(280f / height, 1f), 1f), Shader.TileMode.CLAMP)
        })
        fun draw(text: StaticLayout, y: Float) {
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            text.draw(canvas)
            canvas.restore()
        }
        draw(title, margin.toFloat())
        if (artist != null) draw(artist, (margin + title.height + 18).toFloat())
        var y = (height - bodyHeight) / 2f
        bodies.forEachIndexed { index, group ->
            group.forEachIndexed { part, text ->
                draw(text, y)
                y += text.height + if (part < group.lastIndex) 12 else 0
            }
            if (index < bodies.lastIndex) y += 40
        }
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_app_mark)).mutate().apply {
            setBounds(width - margin - iconSize, height - margin - iconSize, width - margin, height - margin)
            this.draw(canvas)
        }
        return bitmap
    }

    /** Three separable box passes approximate a gentle Gaussian blur on Android 8 and newer. */
    private fun blurredCover(cover: Bitmap, width: Int, height: Int): Bitmap {
        val sampleWidth = 180
        val sampleHeight = (height.toLong() * sampleWidth / width).toInt().coerceAtLeast(1)
        val sample = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
        val scale = maxOf(sampleWidth.toFloat() / cover.width, sampleHeight.toFloat() / cover.height)
        val sourceWidth = (sampleWidth / scale).toInt().coerceIn(1, cover.width)
        val sourceHeight = (sampleHeight / scale).toInt().coerceIn(1, cover.height)
        val left = (cover.width - sourceWidth) / 2
        val top = (cover.height - sourceHeight) / 2
        Canvas(sample).apply {
            drawColor(Color.rgb(39, 39, 39))
            drawBitmap(cover, Rect(left, top, left + sourceWidth, top + sourceHeight),
                Rect(0, 0, sampleWidth, sampleHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        var pixels = IntArray(sampleWidth * sampleHeight)
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        repeat(3) {
            pixels = boxBlur(pixels, sampleWidth, sampleHeight, horizontal = true)
            pixels = boxBlur(pixels, sampleWidth, sampleHeight, horizontal = false)
        }
        sample.setPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        return sample
    }

    private fun boxBlur(input: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val output = IntArray(input.size)
        val radius = 2
        val count = radius * 2 + 1
        val rows = if (horizontal) height else width
        val length = if (horizontal) width else height
        for (row in 0 until rows) {
            fun offset(position: Int) = if (horizontal) row * width + position else position * width + row
            var red = 0
            var green = 0
            var blue = 0
            for (position in -radius..radius) {
                val color = input[offset(position.coerceIn(0, length - 1))]
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
            }
            for (position in 0 until length) {
                output[offset(position)] = Color.rgb(red / count, green / count, blue / count)
                val remove = input[offset((position - radius).coerceIn(0, length - 1))]
                val add = input[offset((position + radius + 1).coerceIn(0, length - 1))]
                red += Color.red(add) - Color.red(remove)
                green += Color.green(add) - Color.green(remove)
                blue += Color.blue(add) - Color.blue(remove)
            }
        }
        return output
    }

    fun save(context: Context, bitmap: Bitmap, uri: Uri) {
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入所选位置" }
        stream.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "图片保存失败" } }
    }

    fun shareIntent(context: Context, bitmap: Bitmap): Intent {
        val directory = File(context.cacheDir, "lyric_cards").apply { check(mkdirs() || isDirectory) }
        val expiry = System.currentTimeMillis() - 86_400_000L
        directory.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.delete() }
        val file = File(directory, "lyrics-${UUID.randomUUID()}.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.lyriccards", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "歌词卡片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
