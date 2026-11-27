package com.luoh.music.lrc.ui.home.pop

import android.content.Context
import com.luoh.music.lrc.ui.home.HomeLyricAppearance
import com.luoh.music.lrc.ui.home.HomeLyricsPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PopLyricScheme(val id: String, val name: String,
    val motion: PopLyricsStyle, val text: HomeLyricAppearance = HomeLyricAppearance(),
    val description: String? = null)

/** Named snapshots; applying or editing a scheme never mutates the saved snapshot. */
internal object PopLyricSchemes {
    private const val KEY = "pop_saved_schemes"
    const val LIMIT = 12
    const val RECOMMENDED_ID = "flow"
    val builtIns = listOf(
        // Match fresh-install motion and typography defaults; preserve the established motion profile.
        PopLyricScheme(RECOMMENDED_ID, "默认", PopLyricsStyle(), description = "错落、跟随"),
        PopLyricScheme("reading", "小幅", PopLyricsStyle(layout = "centered", entrance = "rise",
            entranceMs = 350, lookaheadMs = 40, travelPercent = 0, activeScalePercent = 105,
            rotationDegrees = 0, camera = "off", cameraPercent = 0, breathing = false,
            transition = "fade", rowMotionPercent = 0, staggerPercent = 0, sustainShakePercent = 0,
            passedOpacityPercent = 90, motionStrength = "gentle", shortPhraseScalePercent = 125,
            stagePaddingDp = 24, translationGapDp = 12),
            HomeLyricAppearance(fontSize = 30, translationSize = 18, lineSpacing = 12), "居中、小幅动画"),
        PopLyricScheme("calm", "缓慢", PopLyricsStyle(layout = "centered", entrance = "rise",
            entranceMs = 600, travelPercent = 35, activeScalePercent = 120, rotationDegrees = 0,
            cameraPercent = 35, breathing = false, transition = "fade", rowMotionPercent = 25,
            staggerPercent = 0, sustainShakePercent = 35, motionStrength = "gentle"),
            description = "上移、淡入"),
        PopLyricScheme("wave", "波浪", PopLyricsStyle(layout = "wave", entrance = "rise",
            entranceMs = 520, lookaheadMs = 100, travelPercent = 65, activeScalePercent = 130,
            rotationDegrees = 4, camera = "drift", cameraPercent = 45, transition = "slide",
            pushDirection = "vertical", staggerPercent = 85, rowMotionPercent = 35,
            sustainShakePercent = 55, rowGapPercent = 100, shortPhraseScalePercent = 145),
            HomeLyricAppearance(fontSize = 28, translationSize = 17, lineSpacing = 12), "波浪、上下切换"),
        PopLyricScheme("orbit", "环绕", PopLyricsStyle(layout = "staggered", entrance = "disperse",
            entranceMs = 650, lookaheadMs = 110, travelPercent = 70, activeScalePercent = 130,
            rotationDegrees = 5, camera = "orbit", cameraPercent = 55, cameraSpeedPercent = 85,
            transition = "zoom", rowMotionPercent = 30, staggerPercent = 90, sustainShakePercent = 50,
            stageAnchorPercent = 44, stagePaddingDp = 24, shortPhraseScalePercent = 145,
            translationGapDp = 12),
            HomeLyricAppearance(fontSize = 30, translationSize = 17, lineSpacing = 12), "环绕、缩放切换"),
        PopLyricScheme("horizontal", "横向", PopLyricsStyle(layout = "stairs", entrance = "rise",
            entranceMs = 400, lookaheadMs = 70, travelPercent = 50, activeScalePercent = 130,
            rotationDegrees = 0, cameraPercent = 45, breathing = false, transition = "slide",
            pushDirection = "horizontal", staggerPercent = 80, rowMotionPercent = 35,
            cameraSpeedPercent = 110, sustainShakePercent = 35, shortPhraseScalePercent = 145),
            description = "阶梯、左右切换"),
        PopLyricScheme("converge", "聚合", PopLyricsStyle(layout = "centered", entrance = "converge",
            entranceMs = 550, lookaheadMs = 90, travelPercent = 80, activeScalePercent = 135,
            rotationDegrees = 3, camera = "off", cameraPercent = 0, breathing = false,
            transition = "disperse", rowMotionPercent = 30, staggerPercent = 0,
            sustainShakePercent = 45, stageAnchorPercent = 45, shortPhraseScalePercent = 150),
            HomeLyricAppearance(fontSize = 30, translationSize = 17, fontWeight = 600), "聚合入场、扩散切换"),
        PopLyricScheme("dynamic", "快速", PopLyricsStyle(entranceMs = 300, lookaheadMs = 60,
            activeScalePercent = 160, rotationDegrees = 14, cameraPercent = 90,
            cameraSpeedPercent = 150, rowMotionPercent = 80, pageLength = 4, sustainShakePercent = 140,
            motionStrength = "strong"), description = "快速入场、大幅动画"),
        PopLyricScheme("random", "随机", PopLyricsStyle(layout = "random", entrance = "random",
            transition = "random", pushDirection = "random", entranceMs = 480, travelPercent = 75,
            activeScalePercent = 140, rotationDegrees = 6, cameraPercent = 55, cameraSpeedPercent = 105,
            staggerPercent = 100, rowMotionPercent = 40, sustainShakePercent = 65,
            shortPhraseScalePercent = 150), description = "排版、入场与切换随机"),
    )
    val recommended: PopLyricScheme get() = builtIns.first { it.id == RECOMMENDED_ID }

    fun saved(context: Context): List<PopLyricScheme> {
        val source = context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = runCatching { JSONArray(source) }.getOrNull() ?: return emptyList()
        return (0 until minOf(array.length(), LIMIT)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                val name = item.getString("name").trim().take(32)
                require(id.isNotBlank() && name.isNotBlank())
                PopLyricScheme(id, name, readMotion(item.getJSONObject("motion")),
                    item.getJSONObject("text").let {
                        HomeLyricAppearance(it.getInt("fontSize"), it.getInt("translationSize"),
                            it.getInt("fontWeight"), it.getInt("lineSpacing")).normalized()
                    })
            }.getOrNull()
        }.distinctBy { it.id }
    }

    fun save(context: Context, name: String): Boolean {
        val normalized = name.trim().take(32)
        if (normalized.isBlank()) return false
        val items = saved(context).toMutableList()
        val index = items.indexOfFirst { it.name == normalized }
        if (index < 0 && items.size >= LIMIT) return false
        val item = PopLyricScheme(if (index >= 0) items[index].id else UUID.randomUUID().toString(),
            normalized, PopLyricsPrefs.style(context), HomeLyricsPrefs.appearance(context))
        if (index >= 0) items[index] = item else items.add(item)
        write(context, items)
        return true
    }

    fun delete(context: Context, id: String) = write(context, saved(context).filter { it.id != id })

    fun apply(context: Context, scheme: PopLyricScheme) {
        PopLyricsPrefs.save(context, scheme.motion)
        HomeLyricsPrefs.saveAppearance(context, scheme.text)
    }

    private fun write(context: Context, items: List<PopLyricScheme>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name)
            .put("motion", motionJson(item.motion)).put("text", JSONObject()
                .put("fontSize", item.text.fontSize).put("translationSize", item.text.translationSize)
                .put("fontWeight", item.text.fontWeight).put("lineSpacing", item.text.lineSpacing))) }
        context.getSharedPreferences(HomeLyricsPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }

    private fun motionJson(s: PopLyricsStyle) = JSONObject().apply {
        put("reveal", s.reveal); put("layout", s.layout); put("entrance", s.entrance)
        put("entranceMs", s.entranceMs); put("lookaheadMs", s.lookaheadMs); put("travelPercent", s.travelPercent)
        put("activeScalePercent", s.activeScalePercent); put("rotationDegrees", s.rotationDegrees)
        put("camera", s.camera); put("cameraPercent", s.cameraPercent); put("breathing", s.breathing)
        put("transition", s.transition); put("semanticGrouping", s.semanticGrouping); put("dynamicSpacing", s.dynamicSpacing)
        put("groupGapDp", s.groupGapDp); put("innerGapDp", s.innerGapDp); put("rowGapPercent", s.rowGapPercent)
        put("lineWidthPercent", s.lineWidthPercent); put("staggerPercent", s.staggerPercent)
        put("rowMotionPercent", s.rowMotionPercent); put("cameraSpeedPercent", s.cameraSpeedPercent)
        put("adaptiveTiming", s.adaptiveTiming); put("textHoldMs", s.textHoldMs)
        put("passedOpacityPercent", s.passedOpacityPercent); put("paginate", s.paginate)
        put("pageLength", s.pageLength); put("sustainShakePercent", s.sustainShakePercent)
        put("pushDirection", s.pushDirection)
        put("flowTransitions", s.flowTransitions)
        put("motionStrength", s.motionStrength)
        put("adaptiveEmphasis", s.adaptiveEmphasis)
        put("coordinateMotion", s.coordinateMotion)
        put("stageAnchorPercent", s.stageAnchorPercent)
        put("stagePaddingDp", s.stagePaddingDp)
        put("shortPhraseScalePercent", s.shortPhraseScalePercent)
        put("translationGapDp", s.translationGapDp)
        put("translationMaxLines", s.translationMaxLines)
        put("stageFillPercent", s.stageFillPercent)
        put("sizingMode", s.sizingMode)
        put("classicScalePercent", s.classicScalePercent)
    }

    private fun readMotion(j: JSONObject): PopLyricsStyle {
        val d = PopLyricsStyle()
        return d.copy(
            reveal = j.optString("reveal", d.reveal), layout = j.optString("layout", d.layout),
            entrance = j.optString("entrance", d.entrance), entranceMs = j.optInt("entranceMs", d.entranceMs),
            lookaheadMs = j.optInt("lookaheadMs", d.lookaheadMs), travelPercent = j.optInt("travelPercent", d.travelPercent),
            activeScalePercent = j.optInt("activeScalePercent", d.activeScalePercent),
            rotationDegrees = j.optInt("rotationDegrees", d.rotationDegrees), camera = j.optString("camera", d.camera),
            cameraPercent = j.optInt("cameraPercent", d.cameraPercent), breathing = j.optBoolean("breathing", d.breathing),
            transition = j.optString("transition", d.transition), semanticGrouping = j.optBoolean("semanticGrouping", d.semanticGrouping),
            dynamicSpacing = j.optBoolean("dynamicSpacing", d.dynamicSpacing), groupGapDp = j.optInt("groupGapDp", d.groupGapDp),
            innerGapDp = j.optInt("innerGapDp", d.innerGapDp), rowGapPercent = j.optInt("rowGapPercent", d.rowGapPercent),
            lineWidthPercent = j.optInt("lineWidthPercent", d.lineWidthPercent), staggerPercent = j.optInt("staggerPercent", d.staggerPercent),
            rowMotionPercent = j.optInt("rowMotionPercent", d.rowMotionPercent), cameraSpeedPercent = j.optInt("cameraSpeedPercent", d.cameraSpeedPercent),
            adaptiveTiming = j.optBoolean("adaptiveTiming", d.adaptiveTiming), textHoldMs = j.optInt("textHoldMs", d.textHoldMs),
            passedOpacityPercent = j.optInt("passedOpacityPercent", d.passedOpacityPercent), paginate = j.optBoolean("paginate", d.paginate),
            pageLength = j.optInt("pageLength", d.pageLength), sustainShakePercent = j.optInt("sustainShakePercent", d.sustainShakePercent),
            pushDirection = j.optString("pushDirection", d.pushDirection),
            flowTransitions = j.optBoolean("flowTransitions", d.flowTransitions),
            motionStrength = j.optString("motionStrength", d.motionStrength),
            adaptiveEmphasis = j.optBoolean("adaptiveEmphasis", d.adaptiveEmphasis),
            coordinateMotion = j.optBoolean("coordinateMotion", d.coordinateMotion),
            stageAnchorPercent = j.optInt("stageAnchorPercent", d.stageAnchorPercent),
            stagePaddingDp = j.optInt("stagePaddingDp", d.stagePaddingDp),
            shortPhraseScalePercent = j.optInt("shortPhraseScalePercent", d.shortPhraseScalePercent),
            translationGapDp = j.optInt("translationGapDp", d.translationGapDp),
            translationMaxLines = j.optInt("translationMaxLines", d.translationMaxLines),
            stageFillPercent = j.optInt("stageFillPercent", d.stageFillPercent),
            sizingMode = j.optString("sizingMode", d.sizingMode),
            classicScalePercent = j.optInt("classicScalePercent", d.classicScalePercent),
        ).normalized()
    }
}
