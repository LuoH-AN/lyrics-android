package com.luoh.music.lrc

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 为一首歌粘贴或导入 LRC 歌词；保存后主页和悬浮窗都优先使用它。 */
class CustomLyricsEditActivity : AppCompatActivity() {
    private lateinit var titleField: EditText
    private lateinit var artistField: EditText
    private lateinit var lyricsField: EditText
    private lateinit var status: TextView
    private var editingId: String? = null
    private var savedSnapshot = ""

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    private fun col(res: Int) = ContextCompat.getColor(this, res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        // 可能从悬浮窗直接打开，此时主页还没设置过主题
        ThemePrefs.apply(getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE).getString(ThemePrefs.KEY, ThemePrefs.FOLLOW))
        super.onCreate(state)
        val requestedTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val requestedArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val existing = intent.getStringExtra(EXTRA_ID)?.let { CustomLyricsStore.get(this, it) }
            ?: CustomLyricsStore.find(this, requestedTitle, requestedArtist)
        editingId = existing?.id

        val root = NativeUi.column(this, 0).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(col(R.color.app_bg))
        }
        val toolbar = NativeUi.toolbar(this, if (existing == null) "添加自定义歌词" else "编辑自定义歌词") { leave() }
        val saveButton = NativeUi.button(this, "保存", true) { save() }.apply {
            isSingleLine = true
            minWidth = dp(64)
            setPadding(dp(16), 0, dp(16), 0)
            insetTop = dp(4)
            insetBottom = dp(4)
            cornerRadius = dp(14)
        }
        toolbar.menu.add("保存").apply {
            actionView = FrameLayout(this@CustomLyricsEditActivity).apply {
                setPadding(0, 0, dp(12), 0)
                addView(saveButton, FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER_VERTICAL))
            }
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        root.addView(toolbar)
        val content = NativeUi.column(this, 20)
        root.addView(NestedScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val info = NativeUi.column(this)
        info.addView(NativeUi.field(this, "歌名（必填）", existing?.title ?: requestedTitle).also { titleField = it.editText!! })
        info.addView(NativeUi.field(this, "歌手（留空匹配所有同名歌曲）", existing?.artist ?: requestedArtist).also { artistField = it.editText!! })
        content.addView(NativeUi.card(this).apply { addView(info) })

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("导入 .lrc 文件") { importLauncher.launch(arrayOf("*/*")) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5); topMargin = dp(12) })
            addView(button("粘贴剪贴板") { pasteClipboard() },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5); topMargin = dp(12) })
        })
        content.addView(NativeUi.field(this, "LRC 时间轴歌词", existing?.lyrics.orEmpty(), true).also {
            lyricsField = it.editText!!
            lyricsField.typeface = Typeface.MONOSPACE
            it.placeholderText = "[00:12.30]第一句歌词\n[00:16.85]第二句歌词\n[00:16.85]同一时间戳的第二行作为译文"
        })
        status = text("", 13f).apply { setPadding(dp(2), dp(10), 0, 0) }
        content.addView(status)
        if (existing != null) content.addView(button("删除这份自定义歌词") { confirmDelete(existing) })

        lyricsField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateStatus()
        })
        updateStatus()
        savedSnapshot = snapshot()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
    }

    private fun snapshot(): String =
        listOf(titleField.text, artistField.text, lyricsField.text).joinToString("\u0000")

    private fun updateStatus() {
        val raw = lyricsField.text.toString()
        if (raw.isBlank()) {
            status.text = "粘贴、输入或导入 LRC 歌词"
            status.setTextColor(col(R.color.text_tertiary))
            return
        }
        status.setTextColor(col(R.color.text_secondary))
        if (raw.length > CustomLyricsStore.MAX_LENGTH) {
            status.text = "歌词太长，请删减到 ${CustomLyricsStore.MAX_LENGTH / 1000}K 字符以内"
            return
        }
        val parsed = CustomLyricsStore.parse(raw)
        status.text = if (parsed.lineCount == 0) {
            "没有识别到带时间轴的歌词行"
        } else buildString {
            append("识别到 ${parsed.lineCount} 行歌词")
            if (parsed.translationCount > 0) append(" · ${parsed.translationCount} 行译文")
            if (parsed.ignoredCount > 0) append(" · ${parsed.ignoredCount} 行没有时间轴，已忽略")
        }
    }

    private fun save() {
        val title = titleField.text.toString().trim()
        val artist = artistField.text.toString().trim()
        val raw = lyricsField.text.toString().trim()
        if (title.isEmpty()) {
            toast("请填写歌名")
            titleField.requestFocus()
            return
        }
        if (raw.length > CustomLyricsStore.MAX_LENGTH) {
            toast("歌词太长，请删减后再保存")
            return
        }
        val parsed = CustomLyricsStore.parse(raw)
        if (parsed.lineCount == 0) {
            toast("没有识别到带时间轴的歌词行，格式示例：[01:23.45]歌词")
            return
        }
        CustomLyricsStore.save(this, title, artist, raw, editingId)
        LyricsOverlayService.instance?.reloadCustomLyrics()
        toast("已保存 · ${parsed.lineCount} 行歌词")
        finish()
    }

    private fun leave() {
        if (snapshot() == savedSnapshot) {
            finish()
            return
        }
        confirm("放弃未保存的修改？", "刚才输入或导入的歌词不会保存。", "放弃修改") { finish() }
    }

    private fun confirmDelete(entry: CustomLyricsStore.Entry) {
        confirm("删除这份自定义歌词？", "《${entry.title}》会恢复自动匹配歌词。", "确认删除") {
            CustomLyricsStore.delete(this, entry.id)
            LyricsOverlayService.instance?.reloadCustomLyrics()
            toast("已删除，恢复自动匹配")
            finish()
        }
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            ?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            toast("剪贴板里没有文字")
            return
        }
        if (lyricsField.hasFocus()) {
            // 正在编辑时按光标位置插入，和系统粘贴一致
            val start = lyricsField.selectionStart.coerceAtLeast(0)
            val end = lyricsField.selectionEnd.coerceAtLeast(0)
            lyricsField.text.replace(minOf(start, end), maxOf(start, end), text)
        } else {
            lyricsField.setText(text.trim())
        }
        fillFromTags(text)
    }

    private fun importFile(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_FILE_BYTES) error("File too large")
                }
                decodeText(output.toByteArray())
            }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            toast("无法读取这个文件，请确认是文本格式的 LRC 歌词")
            return
        }
        lyricsField.setText(text.trim())
        fillFromTags(text)
        val parsed = CustomLyricsStore.parse(text)
        toast(if (parsed.lineCount > 0) "已导入 ${parsed.lineCount} 行歌词" else "文件里没有识别到带时间轴的歌词行")
    }

    /** 歌名、歌手还空着时，用 LRC 里的 [ti:] / [ar:] 标签补上。 */
    private fun fillFromTags(raw: String) {
        val tags = CustomLyricsStore.metadata(raw)
        if (titleField.text.isBlank()) tags["ti"]?.takeIf { it.isNotBlank() }?.let(titleField::setText)
        if (artistField.text.isBlank()) tags["ar"]?.takeIf { it.isNotBlank() }?.let(artistField::setText)
    }

    /** LRC 文件常见 UTF-8（可能带 BOM）、UTF-16 和 GBK 编码。 */
    private fun decodeText(bytes: ByteArray): String {
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrElse { String(bytes, Charset.forName("GB18030")) }
        }
        return text.removePrefix(0xFEFF.toChar().toString())
    }

    private fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        NativeUi.confirm(this, title, message, action, onConfirm)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun text(value: String, size: Float) = NativeUi.text(this, value, size)
    private fun button(value: String, action: () -> Unit) = NativeUi.button(this, value, action = action)

    companion object {
        private const val EXTRA_ID = "custom_lyrics_id"
        private const val EXTRA_TITLE = "custom_lyrics_title"
        private const val EXTRA_ARTIST = "custom_lyrics_artist"
        private const val MAX_FILE_BYTES = 1_000_000

        /** 按歌名/歌手打开：已有自定义歌词就编辑它，否则新建并预填。 */
        fun intent(context: Context, title: String, artist: String): Intent =
            Intent(context, CustomLyricsEditActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)

        fun editIntent(context: Context, id: String): Intent =
            Intent(context, CustomLyricsEditActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
