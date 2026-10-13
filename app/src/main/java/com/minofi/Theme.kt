package com.minofi

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Yellow = Color(0xFFFFD400)
val Black = Color(0xFF0D0D0D)
val CardBg = Color(0xFF1A1A1A)
val Card2 = Color(0xFF262626)
val Green = Color(0xFF5FD38D)
val Red = Color(0xFFFF6B6B)
val Orange = Color(0xFFFF9F43)

fun Context.mp() = getSharedPreferences("minofi", Context.MODE_PRIVATE)

@Composable
fun Panel(title: String, accent: Color = Yellow, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Card2, RoundedCornerShape(14.dp)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun Gauge(progress: Float, centerText: String, label: String, color: Color, diameter: Dp = 96.dp) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 10.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color(0xFF333333), 135f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * progress.coerceIn(0f, 1f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = Color.White, fontSize = (diameter.value * 0.19f).sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(0.1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - size.height * (v / max)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

fun latencyColor(ms: Long) = when { ms < 3_000 -> Green; ms < 10_000 -> Yellow; else -> Orange }

@Composable
fun LatencyBars(probes: List<Probe>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (probes.isEmpty()) return@Canvas
        val n = probes.size
        val gap = 4.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val maxL = (probes.filter { !it.lost }.maxOfOrNull { it.latency } ?: 1000L).coerceAtLeast(1000L).toFloat()
        val r = CornerRadius(4.dp.toPx())
        probes.forEachIndexed { i, p ->
            val x = i * (bw + gap)
            if (p.lost) drawRoundRect(Red.copy(alpha = 0.85f), Offset(x, 0f), Size(bw, size.height), r)
            else {
                val h = size.height * (p.latency / maxL).coerceIn(0.05f, 1f)
                drawRoundRect(latencyColor(p.latency), Offset(x, size.height - h), Size(bw, h), r)
            }
        }
    }
}
