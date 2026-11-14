package com.luoh.music.lrc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PopLyricScheme(val id: String, val name: String,
    val motion: PopLyricsStyle, val text: HomeLyricAppearance = HomeLyricAppearance())

/** Named snapshots; applying or editing a scheme never mutates the saved snapshot. */
internal object PopLyricSchemes {
    private const val KEY = "pop_saved_schemes"
    const val LIMIT = 12
    val builtIns = listOf(
        PopLyricScheme("calm", "舒缓", PopLyricsStyle(layout = "centered", entrance = "rise",
            entranceMs = 600, travelPercent = 35, activeScalePercent = 120, rotationDegrees = 0,
            cameraPercent = 35, breathing = false, transition = "fade", rowMotionPercent = 25,
            staggerPercent = 0, sustainShakePercent = 35)),
        PopLyricScheme("flow", "灵动", PopLyricsStyle()),
        PopLyricScheme("dynamic", "强节奏", PopLyricsStyle(entranceMs = 300, lookaheadMs = 60,
            activeScalePercent = 160, rotationDegrees = 14, cameraPercent = 90,
            cameraSpeedPercent = 150, rowMotionPercent = 80, pageLength = 8, sustainShakePercent = 140)),
    )

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
        ).normalized()
    }
}
