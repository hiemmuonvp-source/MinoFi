package com.minofi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Choreographer
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val listGap = Arrangement.spacedBy(12.dp)
private val listPad = PaddingValues(top = 12.dp, bottom = 16.dp)

@Composable
fun FcmScreen(ctx: Context, checks: List<Check>, token: String, statsTick: Int) {
    val st = remember(statsTick) { Probes.stats(ctx) }
    val bars = remember(statsTick) { Probes.load(ctx).takeLast(30) }
    val score = remember(statsTick, checks) { Health.score(st, checks) }
    val hasData = st.n + st.lost > 0
    val reach = checks.firstOrNull { it.title.startsWith("Kết nối FCM") }?.ok
    val gms = checks.firstOrNull { it.title == "Google Play services" }?.ok
    val color = when { score >= 80 -> Green; score >= 50 -> Yellow; else -> Red }
    val anim by animateFloatAsState(score / 100f, label = "score")

    var server by remember { mutableStateOf(ctx.mp().getString("server", "") ?: "") }
    var key by remember { mutableStateOf(ctx.mp().getString("key", "") ?: "") }
    var auto by remember { mutableStateOf(ctx.mp().getBoolean("auto", false)) }
    val tf = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Yellow, unfocusedBorderColor = Color.Gray,
        focusedLabelColor = Yellow, unfocusedLabelColor = Color.Gray, cursorColor = Yellow)
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)

    LazyColumn(verticalArrangement = listGap, contentPadding = listPad) {
        item {
            Panel("📡 FIREBASE CLOUD MESSAGING") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(anim, "$score", "FCM HEALTH", color, 120.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when { !hasData -> "Chưa có dữ liệu đo"; score >= 80 -> "FCM ổn định"; score >= 50 -> "FCM đang chậm"; else -> "FCM gián đoạn" },
                            color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(if (gms == true) "GMS ✔" else if (gms == false) "GMS ✖" else "GMS …", if (gms == true) Green else Red)
                            Chip(if (reach == true) "5228 ✔" else if (reach == false) "5228 ✖" else "5228 …", if (reach == true) Green else Red)
                        }
                        Chip(if (token.isNotEmpty()) "Token ✔" else "Chưa có token", if (token.isNotEmpty()) Green else Orange)
                    }
                }
                Probes.verdict(st).takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(10.dp)); Text(it, color = Color.White, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Trung bình", if (st.n == 0) "–" else "${st.avg / 1000.0}".take(4) + "s", Green, Modifier.weight(1f))
                StatTile("P95", if (st.n == 0) "–" else "${st.p95 / 1000.0}".take(4) + "s", Yellow, Modifier.weight(1f))
                StatTile("Tối đa", if (st.n == 0) "–" else "${st.max / 1000.0}".take(4) + "s", Orange, Modifier.weight(1f))
                StatTile("Mất tin", "${st.lost}", if (st.lost == 0) Green else Red, Modifier.weight(1f))
            }
        }
        item {
            Panel("Độ trễ 30 lần gần nhất") {
                if (bars.isEmpty()) Text("Chưa có dữ liệu. Chạy server --loop hoặc bật Worker bên dưới.", color = Color.Gray, fontSize = 13.sp)
                else {
                    LatencyBars(bars, Modifier.fillMaxWidth().height(90.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("<3s", Green); Chip("<10s", Yellow); Chip("chậm", Orange); Chip("mất", Red)
                    }
                }
            }
        }
        item {
            Panel("FCM Token") {
                Text(token.ifEmpty { "(chưa có – cần google-services.json)" }, color = Color.Gray, fontSize = 10.sp, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("fcm", token))
                    Toast.makeText(ctx, "Đã chép token", Toast.LENGTH_SHORT).show()
                }, colors = btn) { Text("Chép token") }
            }
        }
        item {
            Panel("Worker tự đo (cần server --serve)") {
                OutlinedTextField(server, { server = it; ctx.mp().edit().putString("server", it).apply() },
                    label = { Text("URL server, vd http://192.168.1.10:8080") }, singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it; ctx.mp().edit().putString("key", it).apply() },
                    label = { Text("Secret") }, singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = auto, onCheckedChange = {
                        auto = it; ctx.mp().edit().putBoolean("auto", it).apply()
                        if (it) ProbeScheduler.enable(ctx, 15) else ProbeScheduler.disable(ctx)
                    })
                    Spacer(Modifier.width(8.dp)); Text("Tự đo mỗi 15 phút", color = Color.White, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ProbeScheduler.runNow(ctx); Toast.makeText(ctx, "Đang đo…", Toast.LENGTH_SHORT).show() }, colors = btn) { Text("Đo ngay") }
                    OutlinedButton(onClick = { Probes.clear(ctx) }) { Text("Xóa dữ liệu", color = Color.White) }
                }
            }
        }
        item {
            Panel("Đo chuẩn nhất (khuyên dùng)") {
                Text("Trên máy tính/VPS:\npython minofi_server.py --sa sa.json --token <TOKEN> --loop 5\n\nKhóa màn hình, để máy nằm yên vài giờ rồi mở lại app xem P95 và số tin mất.",
                    color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun rememberFps(): MutableIntState {
    val fps = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        var frames = 0; var last = System.nanoTime(); var running = true
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                frames++
                val now = System.nanoTime()
                if (now - last >= 1_000_000_000L) { fps.intValue = frames; frames = 0; last = now }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
        onDispose { running = false; Choreographer.getInstance().removeFrameCallback(cb) }
    }
    return fps
}

private fun tempColor(t: Float) = when { t < 40 -> Green; t < 50 -> Yellow; t < 60 -> Orange; else -> Red }

@Composable
fun TempGauge(name: String, t: Float?) {
    if (t == null) Gauge(0f, "N/A", name, Color.Gray, 92.dp)
    else Gauge(((t - 20f) / 60f), String.format("%.0f°", t), name, tempColor(t), 92.dp)
}

@Composable
fun MonitorScreen(ctx: Context) {
    var snap by remember { mutableStateOf<Snap?>(null) }
    var watts by remember { mutableStateOf(listOf<Float>()) }
    val fps = rememberFps()
    val hz = remember { SystemMonitor.refreshHz(ctx) }

    LaunchedEffect(Unit) {
        while (true) {
            val s = withContext(Dispatchers.IO) { SystemMonitor.read(ctx) }
            snap = s; watts = (watts + s.watts).takeLast(60)
            delay(1000)
        }
    }
    val s = snap
    if (s == null) { Text("Đang đọc cảm biến…", color = Color.Gray, modifier = Modifier.padding(16.dp)); return }

    LazyColumn(verticalArrangement = listGap, contentPadding = listPad) {
        item {
            Panel("🌡 NHIỆT ĐỘ") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TempGauge("CPU", s.cpuTemp); TempGauge("GPU", s.gpuTemp); TempGauge("PIN", s.batTemp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Mức nhiệt hệ thống: ${s.thermal}" + (s.headroom?.let { String.format("  •  headroom %.2f", it) } ?: ""),
                    color = Color.White, fontSize = 13.sp)
                if (s.cpuTemp == null || s.gpuTemp == null)
                    Text("N/A = máy chặn đọc cảm biến này (Android 10+ / SELinux). Nhiệt độ pin và mức nhiệt hệ thống vẫn chính xác.",
                        color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            val pc = if (s.charging) Green else if (s.watts > 6f) Orange else Yellow
            Panel("⚡ CÔNG SUẤT PIN") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(String.format("%.2f", s.watts), color = pc, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text(" W", color = pc, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Chip(if (s.charging) "ĐANG SẠC" else "ĐANG XẢ", pc)
                }
                Text("${s.mA} mA  •  ${String.format("%.2f", s.mv / 1000f)} V" +
                    (s.remainingH?.let { String.format("  •  còn ≈ %.1f giờ", it) } ?: ""), color = Color.LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Sparkline(watts, pc, Modifier.fillMaxWidth().height(60.dp))
                Text("60 giây gần nhất • TB " + String.format("%.2f", if (watts.isEmpty()) 0f else watts.average().toFloat()) + " W",
                    color = Color.Gray, fontSize = 11.sp)
            }
        }
        item {
            Panel("🔋 PIN") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.pct}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    LinearProgressIndicator(progress = { s.pct / 100f }, modifier = Modifier.weight(1f).height(10.dp),
                        color = if (s.pct > 20) Green else Red, trackColor = Card2)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(s.plugged, Yellow); Chip("Sức khỏe: ${s.health}", if (s.health == "Tốt") Green else Orange); Chip(s.tech, Color.LightGray)
                }
            }
        }
        item {
            Panel("🎞 FPS / MÀN HÌNH") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("FPS giao diện", "${fps.intValue}", if (fps.intValue >= 55) Green else Orange, Modifier.weight(1f))
                    StatTile("Tần số quét", if (hz > 0) "${hz.toInt()} Hz" else "–", Yellow, Modifier.weight(1f))
                }
                Text("FPS của game/app khác cần root hoặc Shizuku, Android không cho app thường đọc. Số trên là FPS khung hình của chính MinoFi.",
                    color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Panel("🧠 CPU & RAM") {
                if (s.freqsMHz.isEmpty()) Text("Máy chặn đọc xung nhịp CPU.", color = Color.Gray, fontSize = 12.sp)
                else s.freqsMHz.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("Core $i", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(48.dp))
                        LinearProgressIndicator(progress = { (f / 3300f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(6.dp), color = Yellow, trackColor = Card2)
                        Text(" $f MHz", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("RAM ${s.ramUsedMb} / ${s.ramTotalMb} MB", color = Color.White, fontSize = 13.sp)
                LinearProgressIndicator(progress = { s.ramUsedMb / s.ramTotalMb.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp), color = Green, trackColor = Card2)
            }
        }
    }
}

@Composable
fun FixScreen(ctx: Context, checks: List<Check>) {
    val bad = checks.count { it.ok == false }
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)
    LazyColumn(verticalArrangement = listGap, contentPadding = listPad) {
        item {
            Panel(if (bad == 0) "✔ Chẩn đoán" else "⚠ Phát hiện $bad vấn đề", if (bad == 0) Green else Red) {
                Text(if (bad == 0) "Không có lỗi tự kiểm tra được. Làm thêm các mục thủ công (•) bên dưới."
                     else "Bấm nút “Sửa” ở từng mục, sau đó quay lại app để quét lại.", color = Color.White, fontSize = 13.sp)
            }
        }
        items(checks) { c ->
            val mark = when (c.ok) { true -> "✔"; false -> "✖"; null -> "•" }
            Panel("$mark  ${c.title}", when (c.ok) { true -> Green; false -> Red; null -> Yellow }) {
                Text(c.detail, color = Color.White, fontSize = 13.sp)
                if (c.fix != FixId.NONE && c.ok != true) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { Toast.makeText(ctx, Fixer.apply(ctx, c.fix), Toast.LENGTH_LONG).show() }, colors = btn) { Text(c.fixLabel) }
                }
            }
        }
    }
}
