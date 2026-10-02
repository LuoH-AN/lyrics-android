package com.luoh.music.lrc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import kotlin.math.abs

/** All visible home UI is native; the activity supplies data and executes media actions. */
class HomeLyricsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    data class Snapshot(
        val track: String = "",
        val artist: String = "",
        val cover: Bitmap? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playing: Boolean = false,
        val speed: Float = 1f,
        val canPrevious: Boolean = false,
        val canNext: Boolean = false
    )

    interface Actions {
        fun toggleOverlay()
        fun openSettings()
        fun seekTo(positionMs: Long)
        fun togglePlay()
        fun skipPrev()
        fun skipNext()
        fun editCustomLyrics()
        fun manageCustomLyrics()
    }

    var actions: Actions? = null
    var onTranslationSettings: (() -> Unit)? = null
    private val clock = LyricClock()
    private var snapshot = Snapshot()
    private var document = LyricDocument(emptyList(), true)
    private var translationMode = "bilingual"
    private var lyricOffsetMs = 0L
    private var custom = false
    private var lyricStatus = "loading"
    private var active = false
    private var activeIndex = -2
    private var dragging = false
    private var overlayRunning = false
    private var wideLayout = false
    private var lastProgressPaint = 0L
    private var manualScrollUntil = 0L
    private var menu: BottomSheetDialog? = null
    private val lyricRows = mutableListOf<LyricLineView>()

    private val playerCard = MaterialCardView(context).apply {
        id = R.id.home_player_card
        radius = dp(28).toFloat()
        cardElevation = dp(1).toFloat()
        maxCardElevation = dp(2).toFloat()
        useCompatPadding = false
        preventCornerOverlap = false
        strokeWidth = 0
    }
    private val bar = LinearLayout(context).apply { orientation = VERTICAL }
    private val transport = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val stage = FrameLayout(context)
    private val scroll: ScrollView = object : ScrollView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Observe touches before clickable lyric rows consume them.
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                lyricMotion.cancel()
                manualScrollUntil = SystemClock.uptimeMillis() + 4000L
            }
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        id = R.id.home_lyrics_scroll
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
    }
    private val lyricMotion = HomeLyricMotion(scroll, lyricRows)
    private val lyricsTrack = LinearLayout(context).apply {
        id = R.id.home_lyrics_track
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private val emptyPanel = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(16), dp(28), dp(16))
    }
    private val emptyIconTile = MaterialCardView(context).apply {
        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val emptyIcon = ShapeableImageView(context).apply {
        setImageResource(R.drawable.ic_home_lyrics)
        setPadding(dp(22), dp(22), dp(22), dp(22))
    }
    private val empty = label("未在播放", 22f).apply {
        id = R.id.home_empty
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
    }
    private val emptyDetail = label("先在音乐应用中播放一首歌", 13f).apply {
        id = R.id.home_empty_detail
        gravity = Gravity.CENTER
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private val interlude = label("···", 32f).apply {
        visibility = GONE
        contentDescription = "间奏"
        letterSpacing = .18f
    }
    private val topFade = View(context)
    private val bottomFade = View(context)
    private val coverTile = MaterialCardView(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        clipToOutline = true
    }
    private val cover = ShapeableImageView(context).apply {
        id = R.id.home_cover
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(dp(18).toFloat()).build()
        strokeWidth = 0f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val song = label("未在播放", 20f).apply {
        id = R.id.home_song
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val artist = label("", 13f).apply {
        id = R.id.home_artist
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val translationStatus = label("", 12f).apply {
        tag = "home_translation_status"
        visibility = GONE
        minHeight = dp(48)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener { onTranslationSettings?.invoke() }
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
    }
    private val overlay = iconButton(R.drawable.ic_home_lyrics, "显示桌面歌词").apply {
        id = R.id.home_overlay
        setOnClickListener { actions?.toggleOverlay() }
    }
    private val more = iconButton(R.drawable.ic_home_more, "更多").apply {
        id = R.id.home_more
        setOnClickListener { showMore() }
    }
    private val progress = Slider(context).apply {
        id = R.id.home_progress
        valueFrom = 0f
        valueTo = 1f
        stepSize = 0f
        labelBehavior = LabelFormatter.LABEL_GONE
        trackHeight = dp(6)
        thumbRadius = dp(9)
        thumbStrokeWidth = dp(3).toFloat()
        thumbElevation = 0f
        haloRadius = dp(20)
        contentDescription = "播放进度"
    }
    private val timeCurrent = label("0:00", 12f).apply { id = R.id.home_time_current }
    private val timeDuration = label("0:00", 12f).apply { id = R.id.home_time_duration }
    private val previous = iconButton(R.drawable.ic_home_previous, "上一首").apply {
        id = R.id.home_previous
        iconSize = dp(24)
        cornerRadius = dp(24)
        setOnClickListener { actions?.skipPrev() }
    }
    private val play = iconButton(R.drawable.ic_home_play, "播放").apply {
        id = R.id.home_play
        iconSize = dp(28)
        cornerRadius = dp(32)
        strokeWidth = 0
        setOnClickListener { actions?.togglePlay() }
    }
    private val next = iconButton(R.drawable.ic_home_next, "下一首").apply {
        id = R.id.home_next
        iconSize = dp(24)
        cornerRadius = dp(24)
        setOnClickListener { actions?.skipNext() }
    }
    private val frame = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow) return
            renderFrame()
            postOnAnimation(this)
        }
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        stage.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scroll.addView(lyricsTrack, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        emptyIconTile.addView(emptyIcon, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        emptyPanel.addView(emptyIconTile, LayoutParams(dp(72), dp(72)))
        emptyPanel.addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
        emptyPanel.addView(emptyDetail, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        stage.addView(emptyPanel, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        stage.addView(interlude, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(28) })
        stage.addView(topFade, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40), Gravity.TOP))
        stage.addView(bottomFade, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40), Gravity.BOTTOM))
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        bar.setPadding(dp(16), dp(16), dp(16), dp(20))
        val utilities = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        utilities.addView(overlay, LayoutParams(dp(48), dp(48)))
        utilities.addView(more, LayoutParams(dp(48), dp(48)))

        coverTile.addView(cover, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val metadata = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        metadata.addView(coverTile, LayoutParams(dp(60), dp(60)))
        val text = LinearLayout(context).apply { orientation = VERTICAL }
        text.addView(song, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        text.addView(artist, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        metadata.addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        metadata.addView(utilities, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply { marginStart = dp(4) })
        bar.addView(metadata, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.addView(translationStatus, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        val times = LinearLayout(context).apply { setPadding(dp(16), 0, dp(16), 0) }
        times.addView(timeCurrent, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        times.addView(timeDuration, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        bar.addView(times)
        transport.addView(previous, LayoutParams(dp(48), dp(48)))
        transport.addView(play, LayoutParams(dp(88), dp(64)).apply { marginStart = dp(20); marginEnd = dp(20) })
        transport.addView(next, LayoutParams(dp(48), dp(48)))
        bar.addView(transport, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        playerCard.addView(bar, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(playerCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(16); marginEnd = dp(16); topMargin = dp(8); bottomMargin = dp(16)
        })
        progress.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                timeCurrent.text = formatTime(value.toLong())
                if (!dragging) seek(value.toLong())
            }
        }
        progress.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) { dragging = true }
            override fun onStopTrackingTouch(slider: Slider) {
                dragging = false
                seek(slider.value.toLong())
            }
        })
        progress.setOnKeyListener { _, key, event ->
            if (snapshot.durationMs > 0L && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.action == KeyEvent.ACTION_DOWN) seek(clock.positionMs() + if (key == KeyEvent.KEYCODE_DPAD_RIGHT) 5000L else -5000L)
                true
            } else false
        }
        applyTheme()
        renderTransport()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val wide = availableWidth >= dp(600) && availableWidth > availableHeight
        val compact = availableHeight < dp(520)
        if (wideLayout != wide) {
            wideLayout = wide
            orientation = if (wide) HORIZONTAL else VERTICAL
            (stage.layoutParams as LayoutParams).apply {
                width = if (wide) 0 else LayoutParams.MATCH_PARENT
                height = if (wide) LayoutParams.MATCH_PARENT else 0
                weight = 1f
            }
        }
        val panelWidth = if (wide) (availableWidth * .44f).toInt().coerceIn(dp(288), dp(360)) else availableWidth - dp(32)
        val smallCover = compact || panelWidth < dp(328)
        (playerCard.layoutParams as LayoutParams).apply {
            width = if (wide) panelWidth else LayoutParams.MATCH_PARENT
            gravity = if (wide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
        }
        val titleLines = if (compact) 1 else 2
        if (song.maxLines != titleLines) song.maxLines = titleLines
        song.textSize = if (smallCover) 18f else 20f
        (coverTile.layoutParams as LayoutParams).apply { width = dp(if (smallCover) 48 else 60); height = width }
        if (snapshot.cover == null) {
            val padding = dp(if (smallCover) 12 else 17)
            cover.setPadding(padding, padding, padding, padding)
        }
        (play.layoutParams as LayoutParams).height = dp(if (compact) 56 else 64)
        (transport.layoutParams as LayoutParams).topMargin = dp(if (compact) 8 else 12)
        bar.setPadding(dp(16), dp(if (compact) 12 else 16), dp(16), dp(if (compact) 12 else 20))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val top = (stage.measuredHeight * .42f).toInt()
        val bottom = (stage.measuredHeight * .58f).toInt()
        if (lyricsTrack.paddingTop != top || lyricsTrack.paddingBottom != bottom) {
            lyricsTrack.setPadding(dp(28), top, dp(28), bottom)
            activeIndex = -2
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun setSnapshot(value: Snapshot, forcePosition: Boolean = false) {
        snapshot = value
        clock.durationMs = value.durationMs
        clock.sync(value.positionMs, value.playing, value.speed, forcePosition)
        song.text = value.track.ifBlank { "未在播放" }
        if (value.cover != null) {
            cover.imageTintList = null
            cover.setPadding(0, 0, 0, 0)
            cover.setImageBitmap(value.cover)
        } else {
            cover.setImageResource(R.drawable.ic_home_music)
            cover.imageTintList = ColorStateList.valueOf(color(R.color.text_secondary))
            cover.setPadding(dp(17), dp(17), dp(17), dp(17))
        }
        if (value.track.isBlank() || forcePosition) dragging = false
        if (value.track.isBlank()) {
            clock.reset()
            document = LyricDocument(emptyList(), true)
            custom = false
            lyricStatus = "loading"
            renderLines()
        } else if (forcePosition) {
            showLoading()
        }
        renderArtist()
        renderTransport()
        renderEmpty()
        paintProgress()
        renderFrame()
    }

    fun setProgress(positionMs: Long, playing: Boolean, speed: Float = 1f) {
        val wasPlaying = clock.playing
        clock.sync(positionMs, playing, speed)
        if (playing != wasPlaying) renderTransport()
        paintProgress()
    }

    fun setLyrics(value: LyricDocument, isCustom: Boolean = false) {
        document = value
        custom = isCustom
        lyricStatus = if (value.lines.isEmpty()) "empty" else "ready"
        renderArtist()
        renderLines()
        renderEmpty()
    }

    fun showLoading() {
        document = LyricDocument(emptyList(), true)
        custom = false
        lyricStatus = "loading"
        renderArtist()
        renderLines()
        renderEmpty()
    }

    fun setTranslationStatus(message: String) {
        if (translationStatus.text.toString() == message) return
        translationStatus.text = message
        translationStatus.visibility = if (message.isBlank()) GONE else VISIBLE
    }

    fun setTranslationMode(mode: String) {
        val normalized = if (mode == "original" || mode == "translated") mode else "bilingual"
        if (normalized == translationMode) return
        translationMode = normalized
        renderLines()
    }

    fun setLyricOffset(offsetMs: Int) {
        lyricOffsetMs = offsetMs.toLong()
        activeIndex = -2
        renderFrame()
    }

    fun setOverlayState(running: Boolean) {
        overlayRunning = running
        overlay.isSelected = running
        overlay.backgroundTintList = ColorStateList.valueOf(
            if (running) ColorUtils.setAlphaComponent(color(R.color.text_primary), 31) else Color.TRANSPARENT
        )
        overlay.contentDescription = if (running) "关闭桌面歌词" else "显示桌面歌词"
        ViewCompat.setStateDescription(overlay, if (running) "已开启" else "已关闭")
    }

    fun setActive(on: Boolean) {
        active = on
        removeCallbacks(frame)
        if (on && isAttachedToWindow) postOnAnimation(frame)
        else {
            lyricMotion.cancel()
            activeIndex = -2
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) { removeCallbacks(frame); postOnAnimation(frame) }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        lyricMotion.cancel()
        activeIndex = -2
        menu?.dismiss()
        menu = null
        super.onDetachedFromWindow()
    }

    fun applyTheme() {
        val primary = color(R.color.text_primary)
        val secondary = color(R.color.text_secondary)
        setBackgroundColor(color(R.color.app_bg))
        playerCard.setCardBackgroundColor(color(R.color.app_surface))
        song.setTextColor(primary)
        artist.setTextColor(secondary)
        translationStatus.setTextColor(secondary)
        empty.setTextColor(primary)
        emptyDetail.setTextColor(secondary)
        emptyIconTile.setCardBackgroundColor(color(R.color.app_surface_container))
        emptyIcon.imageTintList = ColorStateList.valueOf(secondary)
        interlude.setTextColor(primary)
        timeCurrent.setTextColor(secondary)
        timeDuration.setTextColor(secondary)
        coverTile.setCardBackgroundColor(color(R.color.app_surface_container))
        cover.setBackgroundColor(Color.TRANSPARENT)
        if (snapshot.cover == null) cover.imageTintList = ColorStateList.valueOf(secondary)
        val iconColors = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(primary, 72), primary))
        listOf(overlay, more, previous, play, next).forEach {
            it.iconTint = iconColors
            it.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 31))
        }
        listOf(previous, next).forEach {
            it.backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_container))
        }
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        play.backgroundTintList = ColorStateList(states, intArrayOf(color(R.color.app_surface_container), color(R.color.accent)))
        play.iconTint = ColorStateList(states, intArrayOf(color(R.color.text_tertiary), color(R.color.text_on_accent)))
        play.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color(R.color.text_on_accent), 48))
        val progressColors = ColorStateList(states, intArrayOf(color(R.color.text_tertiary), color(R.color.accent)))
        progress.thumbTintList = ColorStateList.valueOf(color(R.color.app_surface))
        progress.thumbStrokeColor = progressColors
        progress.trackActiveTintList = progressColors
        progress.trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 24))
        progress.haloTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 24))
        topFade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color(R.color.app_bg), ColorUtils.setAlphaComponent(color(R.color.app_bg), 0)))
        bottomFade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(color(R.color.app_bg), 0), color(R.color.app_bg)))
        setOverlayState(overlayRunning)
        lyricRows.forEach { it.setTextColor(primary) }
    }

    private fun renderArtist() {
        artist.text = if (custom) snapshot.artist.takeIf { it.isNotBlank() }?.plus(" · 自定义歌词") ?: "自定义歌词" else snapshot.artist
    }

    private fun renderTransport() {
        val hasTrack = snapshot.track.isNotBlank()
        previous.isEnabled = hasTrack && snapshot.canPrevious
        next.isEnabled = hasTrack && snapshot.canNext
        play.isEnabled = hasTrack
        play.setIconResource(if (clock.playing) R.drawable.ic_home_pause else R.drawable.ic_home_play)
        play.contentDescription = if (clock.playing) "暂停" else "播放"
        progress.isEnabled = hasTrack && snapshot.durationMs > 0L
    }

    private fun renderEmpty() {
        emptyPanel.visibility = if (document.lines.isEmpty()) VISIBLE else GONE
        emptyDetail.text = when {
            snapshot.track.isBlank() -> "先在音乐应用中播放一首歌"
            lyricStatus == "empty" -> "可以在「更多」中指定自定义歌词"
            else -> "获取完成后会自动显示"
        }
        empty.text = when {
            snapshot.track.isBlank() -> "未在播放"
            lyricStatus == "empty" -> "找不到歌词"
            else -> "正在获取歌词…"
        }
    }

    private fun renderLines() {
        lyricMotion.cancel()
        lyricsTrack.removeAllViews()
        lyricRows.clear()
        activeIndex = -2
        manualScrollUntil = 0L
        interlude.visibility = GONE
        val position = clock.positionMs() + lyricOffsetMs
        document.lines.forEach { line ->
            val row = LyricLineView(context).apply {
                textSize = if (resources.configuration.screenWidthDp < 360) 26f else 30f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isSingleLine = false
                setHorizontallyScrolling(false)
                maxLines = Int.MAX_VALUE
                ellipsize = null
                setTextColor(color(R.color.text_primary))
                setPaddingRelative(0, dp(12), 0, dp(12))
                setLineSpacing(0f, 1.2f)
                bind(line, translationMode)
                setPlaybackPosition(position)
                if (document.timed) {
                    isFocusable = true
                    setOnClickListener { seek((line.startMs - lyricOffsetMs).coerceAtLeast(0L)) }
                }
            }
            lyricRows += row
            lyricsTrack.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        post { renderFrame() }
    }

    private fun renderFrame() {
        if (document.lines.isEmpty()) return
        val position = clock.positionMs() + lyricOffsetMs
        val index = if (document.timed) LyricTiming.activeIndex(document.lines, position) else {
            if (clock.durationMs <= 0L) 0 else ((clock.positionMs().toDouble() / clock.durationMs) * document.lines.size).toInt().coerceIn(0, document.lines.lastIndex)
        }
        val now = SystemClock.uptimeMillis()
        val resumeFollowing = manualScrollUntil != 0L && now >= manualScrollUntil
        if (resumeFollowing) manualScrollUntil = 0L
        if (activeIndex != index || resumeFollowing) {
            val previousIndex = activeIndex
            lyricRows.getOrNull(previousIndex)?.setPlaybackPosition(position)
            activeIndex = index
            val following = manualScrollUntil == 0L
            val target = if (following) lineScrollTarget(index.coerceAtLeast(0)) else null
            val animate = active && isAttachedToWindow && ViewCompat.isLaidOut(this) &&
                previousIndex != -2 && abs(index - previousIndex) <= 3 && following
            lyricMotion.moveTo(index, target, animate)
        }
        lyricRows.getOrNull(index)?.setPlaybackPosition(position)
        renderInterlude(position, index)
        if (now - lastProgressPaint >= 250L) {
            lastProgressPaint = now
            paintProgress()
        }
    }

    private fun lineScrollTarget(index: Int): Int? {
        val row = lyricRows.getOrNull(index) ?: return null
        if (row.height == 0 || stage.height == 0) {
            activeIndex = -2
            return null
        }
        return (row.top + row.height / 2 - (stage.height * .42f).toInt()).coerceAtLeast(0)
    }

    private fun renderInterlude(positionMs: Long, index: Int) {
        if (!document.timed) { interlude.visibility = GONE; return }
        val line = document.lines.getOrNull(index)
        val gapStart = line?.let(LyricTiming::lineEnd) ?: 0L
        val gapEnd = document.lines.getOrNull(index + 1)?.startMs ?: clock.durationMs
        val visible = gapEnd - gapStart > (if (index < 0) 4000L else 4500L) &&
            positionMs > gapStart + (if (index < 0) 0L else 300L) && positionMs < gapEnd - 250L
        interlude.visibility = if (visible) VISIBLE else GONE
        if (visible) {
            val anchor = lyricRows.getOrNull(index.coerceAtLeast(0)) ?: return
            interlude.translationY = anchor.bottom - scroll.scrollY + anchor.translationY + dp(2)
            val ratio = ((positionMs - gapStart).toFloat() / (gapEnd - gapStart)).coerceIn(0f, 1f)
            interlude.alpha = .35f + ratio * .65f
        }
    }

    private fun paintProgress() {
        if (dragging) return
        val duration = snapshot.durationMs.coerceAtLeast(0L)
        val position = clock.positionMs().coerceAtMost(duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val upper = duration.coerceAtLeast(1L).toFloat()
        if (progress.value > upper) progress.value = 0f
        progress.valueTo = upper
        progress.value = position.toFloat().coerceIn(0f, upper)
        timeCurrent.text = formatTime(position)
        timeDuration.text = formatTime(duration)
        progress.contentDescription = "播放进度 ${formatTime(position)} / ${formatTime(duration)}"
    }

    private fun seek(positionMs: Long) {
        if (snapshot.track.isBlank()) return
        val target = if (snapshot.durationMs > 0L) positionMs.coerceIn(0L, snapshot.durationMs) else positionMs.coerceAtLeast(0L)
        actions?.seekTo(target)
        clock.sync(target, clock.playing, clock.speed, force = true)
        activeIndex = -2
        manualScrollUntil = 0L
        paintProgress()
        renderFrame()
    }

    private fun showMore() {
        menu?.dismiss()
        val dialog = BottomSheetDialog(context)
        val content = LinearLayout(dialog.context).apply {
            id = R.id.home_more_sheet
            orientation = VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        content.addView(BottomSheetDragHandleView(dialog.context), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(label("更多", 24f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.text_primary))
        })
        content.addView(label(snapshot.track.ifBlank { "歌词与显示设置" }, 13f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(color(R.color.text_secondary))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(16) })
        fun action(title: String, icon: Int, viewId: Int = View.NO_ID, callback: () -> Unit) {
            content.addView(MaterialButton(dialog.context).apply {
                id = viewId
                text = title
                textSize = 14f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setIconResource(icon)
                iconSize = dp(22)
                iconPadding = dp(12)
                iconGravity = MaterialButton.ICON_GRAVITY_START
                iconTint = ColorStateList.valueOf(color(R.color.text_primary))
                backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_container))
                setTextColor(color(R.color.text_primary))
                setPaddingRelative(dp(16), dp(10), dp(16), dp(10))
                strokeWidth = 0
                minHeight = dp(56)
                cornerRadius = dp(16)
                setOnClickListener { dialog.dismiss(); callback() }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
        action(if (custom) "编辑这首歌的自定义歌词" else "指定 LRC 歌词", R.drawable.ic_home_edit) { actions?.editCustomLyrics() }
        action("管理自定义歌词", R.drawable.ic_home_library) { actions?.manageCustomLyrics() }
        action("设置", R.drawable.ic_home_settings, R.id.home_more_settings) { actions?.openSettings() }
        content.addView(MaterialDivider(dialog.context).apply { dividerColor = color(R.color.app_line_soft) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) })
        content.addView(MaterialButton(dialog.context).apply {
            text = "取消"
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = 0
            setTextColor(color(R.color.text_secondary))
            setOnClickListener { dialog.dismiss() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        dialog.setContentView(content)
        dialog.setOnDismissListener { if (menu === dialog) menu = null }
        menu = dialog
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.backgroundTintList =
            ColorStateList.valueOf(color(R.color.app_surface))
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun iconButton(iconRes: Int, description: String) = MaterialButton(context).apply {
        text = ""
        setIconResource(iconRes)
        iconSize = dp(22)
        iconPadding = 0
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        gravity = Gravity.CENTER
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0
        setPaddingRelative(0, 0, 0, 0)
        strokeWidth = 0
        cornerRadius = dp(12)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        stateListAnimator = null
        elevation = 0f
        contentDescription = description
    }

    private fun label(value: String, size: Float) = MaterialTextView(context).apply {
        text = value
        textSize = size
        includeFontPadding = false
    }
    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun formatTime(value: Long): String {
        val seconds = value.coerceAtLeast(0L) / 1000L
        return "${seconds / 60L}:${(seconds % 60L).toString().padStart(2, '0')}"
    }
}
