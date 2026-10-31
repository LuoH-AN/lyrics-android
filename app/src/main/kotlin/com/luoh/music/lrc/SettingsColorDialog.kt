package com.luoh.music.lrc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class PickerColor(val hue: Float, val saturation: Float, val value: Float) {
    fun argb(): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
    fun hex(): String = "#%06X".format(argb() and 0xFFFFFF)

    fun atPoint(x: Float, y: Float, radius: Float): PickerColor {
        if (radius <= 0f) return this
        val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 360.0) % 360.0).toFloat()
        return copy(hue = if (x == 0f && y == 0f) hue else angle, saturation = (hypot(x, y) / radius).coerceIn(0f, 1f))
    }

    companion object {
        fun parse(hex: String): PickerColor? {
            val normalized = hex.trim().removePrefix("#")
            if (!normalized.matches(Regex("[0-9a-fA-F]{6}"))) return null
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#$normalized"), hsv)
            return PickerColor(hsv[0], hsv[1], hsv[2])
        }
    }
}

@Composable
internal fun SettingsColorDialog(title: String, initialColor: String, onDismiss: () -> Unit,
    onApply: (String) -> Unit, onReset: (() -> Unit)? = null) {
    val initial = PickerColor.parse(initialColor) ?: PickerColor(0f, 0f, 1f)
    var hue by rememberSaveable(title) { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable(title) { mutableFloatStateOf(initial.saturation) }
    var brightness by rememberSaveable(title) { mutableFloatStateOf(initial.value) }
    var draft by rememberSaveable(title) { mutableStateOf(initial.hex()) }
    val selected = PickerColor(hue, saturation, brightness)
    val valid = PickerColor.parse(draft) != null
    val select = remember(title) { { color: PickerColor ->
        hue = color.hue; saturation = color.saturation; brightness = color.value
        draft = color.hex()
    } }
    AlertDialog(
        onDismissRequest = onDismiss,
        // A wrap-content platform window can measure the sliders at two different widths
        // every frame. Give the window a stable width and constrain the Material surface.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorWheel(selected, select, Modifier.align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(selected.argb()))
                        .semantics { contentDescription = "当前颜色 ${selected.hex()}" })
                    Text(selected.hex(), style = MaterialTheme.typography.labelLarge)
                }
                ColorSlider("色相", hue, 0f..360f) { select(selected.copy(hue = it)) }
                ColorSlider("饱和度", saturation, 0f..1f) { select(selected.copy(saturation = it)) }
                ColorSlider("明度", brightness, 0f..1f) { select(selected.copy(value = it)) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { text ->
                        draft = text.take(7)
                        PickerColor.parse(draft)?.let {
                            // Preserve the chosen hue while editing a gray/black color.
                            if (it.saturation > 0f) hue = it.hue
                            saturation = it.saturation; brightness = it.value
                        }
                    },
                    label = { Text("十六进制颜色") },
                    supportingText = { Text("输入 6 位颜色值，例如 #9FD8FF") },
                    isError = !valid, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(requireNotNull(PickerColor.parse(draft)).hex()); onDismiss() }, enabled = valid) { Text("应用") } },
        dismissButton = { Row {
            if (onReset != null) TextButton(onClick = { onReset(); onDismiss() }) { Text("恢复森林") }
            TextButton(onClick = onDismiss) { Text("取消") }
        } },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

@Composable
private fun ColorWheel(color: PickerColor, onChange: (PickerColor) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(color)
    val update by rememberUpdatedState(onChange)
    Canvas(modifier.size(192.dp).testTag("color_wheel").semantics {
        contentDescription = "色盘；也可使用下方色相、饱和度和明度滑块选色"
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val radius = minOf(size.width, size.height) / 2f - 12.dp.toPx()
            fun pick(point: Offset) {
                update(current.atPoint(point.x - size.width / 2f, point.y - size.height / 2f, radius))
            }
            pick(down.position)
            down.consume()
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                pick(change.position)
                change.consume()
            } while (change.pressed)
        }
    }) {
        val radius = size.minDimension / 2f - 12.dp.toPx()
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), radius = radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - color.value), radius)
        val radians = Math.toRadians(color.hue.toDouble())
        val marker = center + Offset((cos(radians) * radius * color.saturation).toFloat(), (sin(radians) * radius * color.saturation).toFloat())
        drawCircle(Color.Black.copy(alpha = .7f), 7.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 7.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
    }
}
