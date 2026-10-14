package com.minofi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

@Composable
fun tfColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = Yellow, unfocusedBorderColor = Color.Gray,
    focusedLabelColor = Yellow, unfocusedLabelColor = Color.Gray, cursorColor = Yellow)

@Composable
fun FixTab(ctx: Context, checks: List<Check>) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ShizukuHelper.state(ctx)) }
    LaunchedEffect(Unit) { while (true) { state = ShizukuHelper.state(ctx); delay(1000) } }

    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }
    var log by remember { mutableStateOf(listOf<FixResult>()) }
    var last by remember { mutableStateOf(ctx.mp().getString("lastFix", "") ?: "") }
    var adv by remember { mutableStateOf(ctx.mp().getBoolean("adv", false)) }
    var auto by remember { mutableStateOf(ctx.mp().getBoolean("autoapply", false)) }
    var extras by remember { mutableStateOf(ctx.mp().getString("extras", "") ?: "") }
    var note by remember { mutableStateOf("") }
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)

    fun setupAction() {
        when (state) {
            ShizukuState.NOT_INSTALLED -> ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShizukuState.NOT_RUNNING -> ctx.packageManager.getLaunchIntentForPackage(ShizukuHelper.PKG)?.let { ctx.startActivity(it) }
            ShizukuState.NO_PERMISSION -> ShizukuHelper.requestPermission()
            ShizukuState.READY -> {}
        }
    }

    fun fixAll() {
        if (running) return
        running = true; log = emptyList(); done = 0
        scope.launch(Dispatchers.IO) {
            val plan = FixEngine.planFromPrefs(ctx)
            total = plan.size.coerceAtLeast(1)
            val sum = FixEngine.run(plan) { i, _, r -> done = i + 1; log = log + r }
            last = FixEngine.saveLast(ctx, sum)
            running = false
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {

        item {
            Panel("⚡ FIX THÔNG BÁO – 1 CHẠM") {
                Button(
                    onClick = { if (state == ShizukuState.READY) fixAll() else setupAction() },
                    modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp),
                    colors = btn, enabled = !running
                ) {
                    Text(
                        when {
                            running -> "Đang áp dụng… $done/$total"
                            state == ShizukuState.READY -> "⚡ FIX TẤT CẢ"
                            else -> "⚡ FIX TẤT CẢ  (cần Shizuku)"
                        }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Yellow, trackColor = Card2)
                }
                if (last.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Lần fix gần nhất: $last", color = Color.LightGray, fontSize = 12.sp)
                }
                if (log.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    log.takeLast(14).forEach { r ->
                        val (icon, col) = when (r.status) { 0 -> "✔" to Green; 1 -> "⚠" to Orange; else -> "✖" to Red }
                        Text("$icon ${r.title}" + (if (r.status != 0 && r.detail.isNotBlank()) "  – ${r.detail}" else ""),
                            color = col, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            val ready = state == ShizukuState.READY
            Panel("🔌 SHIZUKU", if (ready) Green else Orange) {
                Text(
                    when (state) {
                        ShizukuState.READY -> "✔ Sẵn sàng • chế độ ${ShizukuHelper.mode()}"
                        ShizukuState.NO_PERMISSION -> "Shizuku đang chạy nhưng MinoFi chưa được cấp quyền."
                        ShizukuState.NOT_RUNNING -> "Đã cài Shizuku nhưng chưa khởi động."
                        ShizukuState.NOT_INSTALLED -> "Chưa cài Shizuku."
                    }, color = Color.White, fontSize = 14.sp)
                if (!ready) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1) Cài Shizuku (shizuku.rikka.app hoặc Google Play).\n" +
                        "2) Cài đặt → Về điện thoại → bấm 7 lần vào phiên bản MIUI/HyperOS để bật Tùy chọn nhà phát triển.\n" +
                        "3) Bật “Gỡ lỗi không dây”, nối Wi-Fi. Xiaomi: bật thêm “Gỡ lỗi USB (Cài đặt bảo mật)”.\n" +
                        "4) Mở Shizuku → Khởi động qua Gỡ lỗi không dây → ghép nối bằng mã.\n" +
                        "5) Quay lại MinoFi, bấm nút bên dưới để cấp quyền.",
                        color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { setupAction() }, colors = btn) {
                        Text(when (state) {
                            ShizukuState.NOT_INSTALLED -> "Tải Shizuku"
                            ShizukuState.NOT_RUNNING -> "Mở Shizuku"
                            else -> "Cấp quyền cho MinoFi"
                        })
                    }
                }
            }
        }

        item {
            Panel("⚙ TÙY CHỌN") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = auto, onCheckedChange = { auto = it; ctx.mp().edit().putBoolean("autoapply", it).apply() })
                    Spacer(Modifier.width(8.dp))
                    Text("Tự áp dụng khi mở app / bật máy", color = Color.White, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = adv, onCheckedChange = { adv = it; ctx.mp().edit().putBoolean("adv", it).apply() })
                    Spacer(Modifier.width(8.dp))
                    Text("Gồm lệnh nâng cao", color = Color.White, fontSize = 13.sp)
                }
                Text("Nâng cao: tắt App Standby, Adaptive Battery, Cached Apps Freezer. Thông báo nhanh hơn nhưng tốn pin hơn.",
                    color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(extras, { extras = it; ctx.mp().edit().putString("extras", it).apply() },
                    label = { Text("Gói app thêm, cách nhau dấu phẩy") }, placeholder = { Text("com.zing.zalo,com.facebook.orca") },
                    singleLine = true, colors = tfColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val s = FixEngine.run(FixEngine.revert)
                        note = "Đã hoàn tác nâng cao (✔ ${s.ok}, ⚠ ${s.skipped}, ✖ ${s.fail})"
                    }
                }) { Text("Hoàn tác nâng cao", color = Color.White) }
                if (note.isNotEmpty()) Text(note, color = Color.LightGray, fontSize = 11.sp)
            }
        }

        item {
            val bad = checks.count { it.ok == false }
            Panel(if (bad == 0) "✔ Chẩn đoán" else "⚠ Phát hiện $bad vấn đề", if (bad == 0) Green else Red) {
                Text("Những mục MIUI riêng (Pin & hiệu năng, khóa đa nhiệm) lệnh ADB thường không đổi được, hãy bấm nút ở từng mục bên dưới.",
                    color = Color.White, fontSize = 12.sp)
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
