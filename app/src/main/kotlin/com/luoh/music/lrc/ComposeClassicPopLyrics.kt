package com.luoh.music.lrc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import kotlin.math.sin

/** Slot-based renderer from 3f25b1c, before fitting and recentering each visible word.
 * A phrase keeps its measured positions and font size throughout the word entrances. */
@Composable
internal fun ClassicPopLyricCanvas(scenes: PopScenes, builder: PopSceneBuilder, position: State<Long>,
    style: PopLyricsStyle, allowMotion: Boolean, foreground: Color, activeColor: Color,
    modifier: Modifier, sentenceStartMs: Long = 0L,
    sentenceDurationMs: Int = 0, enteringSentence: Boolean = true) {
    val density = LocalDensity.current
    val width = builder.width
    val height = builder.height
    val animateHandoff = allowMotion
    val window by remember(scenes, position, animateHandoff) { derivedStateOf {
        popPhraseWindow(scenes.ranges, scenes.timeline, scenes.handoffs, position.value, animateHandoff)
    } }
    val current = scenes.pages[window.current]
    val phraseSeed = remember(current) {
        PopVisualMotion.seed(current.timeline.startMs, current.timeline.tokens.joinToString("") { it.text })
    }
    val phrasePlan = remember(style, phraseSeed, scenes) {
        val flow = PopStageMotion.flow(scenes.cameraPath, scenes.cameraPath, current.timeline.startMs,
            PopMotionOrchestration.cameraStyle(style), width, height)
        PopMotionOrchestration.transition(style, phraseSeed, flow)
    }
    Canvas(modifier.testTag(if (enteringSentence) "home_pop_canvas" else "home_pop_retiring_canvas").semantics {
        text = AnnotatedString(current.timeline.tokens.joinToString("") { it.text })
    }) {
        val now = position.value
        val handoff = scenes.handoffs[window.current]
        val fraction = if (window.retiring != null && handoff.durationMs > 0L)
            ((now - handoff.startMs).toFloat() / handoff.durationMs).coerceIn(0f, 1f) else 1f
        val sentenceProgress = if (sentenceDurationMs > 0)
            ((now - sentenceStartMs).toFloat() / sentenceDurationMs).coerceIn(0f, 1f) else 1f
        val progress = minOf(fraction, sentenceProgress)
        val wordGain = PopMotionOrchestration.handoffGain(progress, style.coordinateMotion, false)
        val rowGain = PopMotionOrchestration.handoffGain(progress, style.coordinateMotion, true)
        fun drawPhrase(page: PopScene, pose: PopVisualPose) {
            if (pose.alpha <= 0f) return
            val timeline = page.timeline
            val timing = page.timing
            val animated = timeline.wordTimed && allowMotion
            withTransform({
                translate(pose.x, pose.y)
                scale(pose.scale, pose.scale, Offset(width / 2f, height / 2f))
            }) {
                for (row in page.rows) {
                    val enteredRow = if (animated && row.entranceMs > 0L)
                        PopLyricMotion.enter(now - row.startMs, row.entranceMs) else 1f
                    val passedRow = if (animated && row.passMs > 0L)
                        PopLyricMotion.pass(now - row.passStartMs, row.passMs) else 0f
                    val rowLife = if (animated) (1f - passedRow) * PopVisualMotion.travel(
                        (now - row.startMs).toFloat() / row.entranceMs.coerceAtLeast(1L)) else 0f
                    val sway = if (animated) sin((now - row.startMs) / 650f) * row.travel * .12f * rowLife * rowGain else 0f
                    val rowY = if (animated) row.travel * .55f * (1f - enteredRow) * rowGain + sway else 0f
                    withTransform({
                        translate(if (animated) row.travelX * .55f * (1f - enteredRow) * rowGain else 0f, rowY)
                        val rowScale = if (animated && row.travel > 0f) 1f - .04f * (1f - enteredRow) * rowGain else 1f
                        scale(rowScale, rowScale, row.center)
                    }) {
                        for (index in row.from until row.until) {
                            val token = timeline.tokens[index]
                            val elapsed = now - (token.startMs - timing.lookaheadMs[index] + token.visualDelayMs)
                            if (animated && elapsed < 0L) continue
                            val entered = if (animated && timing.entranceMs[index] > 0L)
                                PopLyricMotion.enter(elapsed, timing.entranceMs[index]) else 1f
                            val passed = if (animated && timing.passMs[index] > 0L)
                                PopLyricMotion.pass(now - timing.passStartMs[index], timing.passMs[index]) else if (animated && now >= timing.passStartMs[index]) 1f else 0f
                            val fadeMs = minOf(140L, timing.entranceMs[index] / 3).coerceAtLeast(1L)
                            val wordAlpha = if (animated) (if (timing.entranceMs[index] == 0L) 1f else PopVisualMotion.travel(elapsed.toFloat() / fadeMs)) *
                                (1f - passed * (1f - style.passedOpacityPercent / 100f)) else 1f
                            val glyph = page.glyphs[index]
                            val targetScale = 1f + (glyph.dynamics.activeScale - 1f) * wordGain
                            val entranceScale = .78f + entered * (targetScale - .78f)
                            val scale = if (animated) (entranceScale + (1f - entranceScale) * passed)
                                .coerceIn(.5f, glyph.dynamics.maxScale) else 1f
                            val shake = PopSustainMotion.sample(if (animated) timing.sustain.getOrNull(index) else null, now)
                            withTransform({
                                // Preserve the negative overshoot: the word lands, rebounds, then settles.
                                val remaining = if (animated) 1f - entered else 0f
                                translate(glyph.slot.x + glyph.entranceOffset.x * remaining * wordGain, glyph.slot.y + glyph.entranceOffset.y * remaining * wordGain)
                                scale(page.scale * scale * glyph.baseScale, page.scale * scale * glyph.baseScale, pivot = Offset.Zero)
                                translate(shake.xDp * density.density * wordGain, shake.yDp * density.density * wordGain)
                                if (animated) rotate((glyph.angle * (1f - passed * .65f) + style.rotationDegrees * glyph.dynamics.rotation * (1f - entered) + shake.degrees) * wordGain, pivot = Offset.Zero)
                                translate(-glyph.layout.size.width / 2f, -glyph.layout.size.height / 2f)
                            }) {
                                drawText(glyph.layout, color = if (animated) lerp(activeColor, foreground, passed) else foreground,
                                    alpha = wordAlpha * pose.alpha, shadow = if (animated && now >= token.startMs && now < token.endMs)
                                        glyph.glow else Shadow.None)
                            }
                        }
                    }
                }
            }
        }
        window.retiring?.let { drawPhrase(scenes.pages[it],
            PopMotionOrchestration.pose(phrasePlan, fraction, false, width, height)) }
        drawPhrase(current, PopMotionOrchestration.pose(phrasePlan, fraction, true, width, height))
    }
}
