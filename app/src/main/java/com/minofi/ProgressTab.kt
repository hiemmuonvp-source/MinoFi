package com.minofi

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.withContext

private class PItem(
    val title: String, val weight: Int, val done: Boolean?, val manualKey: String? = null,
    val hint: String = "", val action: (() -> Unit)? = null, val actionLabel: String = "Mở"
)

private val MANUAL_KEYS = listOf("autostart", "pk_gms", "pk_gmail", "gmail_notif", "lock")

@Composable
fun ProgressTab(ctx: Context, checks: List<Check>) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ShizukuHelper.state(ctx)) }
    var bucketOk by remember { mutableStateOf<Boolean?>(null) }
    var bgOk by remember { mutableStateOf<Boolean?>(null) }
    var fcmScore by remember { mutableIntStateOf(ctx.mp().getInt("fcmScore", -1)) }
    var lastFix by remember { mutableStateOf(ctx.mp().getString("lastFix", "").orEmpty()) }
    val manual = remember {
        mutableStateMapOf<String, Boolean>().apply { MANUAL_KEYS.forEach { put(it, ctx.mp().getBoolean("man_$it", false)) } }
    }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            state = ShizukuHelper.state(ctx)
            fcmScore = ctx.mp().getInt("fcmScore", -1)
            lastFix = ctx.mp().getString("lastFix", "").orEmpty()
            if (state == ShizukuState.READY) {
                withContext(Dispatchers.IO) {
                    bucketOk = Verify.bucket(GMS)?.let { it <= 10 }
                    bgOk = Verify.bgAllowed(GMS)
                }
            } else { bucketOk = null; bgOk = null }
            delay(3000)
        }
    }

    fun ck(prefix: String): Boolean? = checks.firstOrNull { it.title.startsWith(prefix) }?.ok
    fun fix(id: FixId) { Toast.makeText(ctx, Fixer.apply(ctx, id), Toast.LENGTH_LONG).show() }

    val auto = listOf(
        PItem("Shizuku sẵn sàng", 10, state == ShizukuState.READY, hint = "Thiết lập ở tab ⚡ Fix"),
        PItem("Đã chạy ⚡ FIX TẤT CẢ", 15, lastFix.isNotEmpty(), hint = if (lastFix.isEmpty()) "Chưa chạy lần nào" else lastFix),
        PItem("GMS được miễn Doze", 10, ck("Doze/Tối ưu pin – Google Play"), action = { fix(FixId.GMS_INFO) }),
        PItem("Gmail được miễn Doze", 10, ck("Doze/Tối ưu pin – Gmail"), action = { fix(FixId.GMS_INFO) }),
        PItem("GMS ở nhóm ACTIVE", 6, bucketOk, hint = "Cần Shizuku để kiểm tra"),
        PItem("GMS được chạy nền", 6, bgOk, hint = "Cần Shizuku để kiểm tra"),
        PItem("Quyền thông báo MinoFi", 5, ck("Quyền thông báo"), action = { fix(FixId.NOTIF_SETTINGS) }),
        PItem("Không hạn chế nền MinoFi", 4, ck("Hạn chế nền")),
        PItem("Tiết kiệm dữ liệu không chặn nền", 4, ck("Tiết kiệm dữ liệu"), action = { fix(FixId.DATA_SAVER) }),
        PItem("Kết nối FCM :5228 thông", 8, ck("Kết nối FCM")),
        PItem("Đo FCM ≥ 80%", 10, if (fcmScore < 0) null else fcmScore >= 80, hint = "Bấm 📡 ĐO FCM ở tab FCM")
    )
    val man = listOf(
        PItem("Autostart: GMS, Gmail, MinoFi", 6, manual["autostart"], "autostart", "Bật công tắc cho cả 3 app", { fix(FixId.AUTOSTART) }),
        PItem("Pin & hiệu năng → Không hạn chế (GMS)", 5, manual["pk_gms"], "pk_gms", "", { fix(FixId.POWERKEEPER_GMS) }),
        PItem("Pin & hiệu năng → Không hạn chế (Gmail)", 5, manual["pk_gmail"], "pk_gmail", "", { fix(FixId.POWERKEEPER_GMAIL) }),
        PItem("Thông báo Gmail: mức Quan trọng", 4, manual["gmail_notif"], "gmail_notif", "Bật mọi kênh, cho hiện màn hình khóa", {
            try {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, GMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }),
        PItem("Khóa Gmail & MinoFi trong đa nhiệm", 3, manual["lock"], "lock", "Mở Recent, nhấn giữ thẻ app, chọn ổ khóa")
    )

    val all = auto + man
    val totalW = all.sumOf { it.weight }
    val gotW = all.sumOf { if (it.done == true) it.weight else 0 }
    val pct = if (totalW == 0) 0 else gotW * 100 / totalW
    val anim by animateFloatAsState(pct / 100f, label = "pct")
    val color = if (pct >= 80) Green else if (pct >= 40) Yellow else Orange
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)

    fun runFix() {
        if (running) return
        running = true; done = 0
        scope.launch(Dispatchers.IO) {
            val plan = FixEngine.planFromPrefs(ctx)
            total = plan.size.coerceAtLeast(1)
            val sum = FixEngine.run(plan) { i, _, _ -> done = i + 1 }
            lastFix = FixEngine.saveLast(ctx, sum)
            running = false
        }
    }

    @Composable
    fun Row1(it: PItem) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (it.manualKey != null) {
                Checkbox(
                    checked = it.done == true,
                    onCheckedChange = { v -> manual[it.manualKey] = v; ctx.mp().edit().putBoolean("man_${it.manualKey}", v).apply() },
                    colors = CheckboxDefaults.colors(checkedColor = Yellow, checkmarkColor = Black, uncheckedColor = Color.Gray))
            } else {
                Text(when (it.done) { true -> "✔"; false -> "✖"; null -> "–" }, fontSize = 16.sp,
                    color = when (it.done) { true -> Green; false -> Red; null -> Color.Gray },
                    modifier = Modifier.width(48.dp).padding(start = 14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(it.title, color = Color.White, fontSize = 13.sp)
                if (it.hint.isNotEmpty()) Text(it.hint, color = Color.Gray, fontSize = 11.sp)
            }
            Chip("+${(it.weight * 100f / totalW).toInt().coerceAtLeast(1)}%", if (it.done == true) Green else Color.Gray)
            if (it.action != null && it.done != true) {
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { it.action.invoke() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text(it.actionLabel, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item {
            Panel("📈 TIẾN ĐỘ FIX THÔNG BÁO") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(anim, "$pct%", "HOÀN THÀNH", color, 150.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(when { pct >= 100 -> "Hoàn tất ✔"; pct >= 80 -> "Gần xong"; pct >= 40 -> "Đang tiến triển"; else -> "Mới bắt đầu" },
                            color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${all.count { it.done == true }}/${all.size} mục đạt", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (state == ShizukuState.READY) runFix()
                    else Toast.makeText(ctx, "Cần Shizuku, thiết lập ở tab ⚡ Fix", Toast.LENGTH_LONG).show()
                }, enabled = !running, colors = btn, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (running) "Đang fix… $done/$total" else "⚡ FIX TẤT CẢ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Yellow, trackColor = Card2)
                }
            }
        }
        item { Panel("Tự động xác minh") { auto.forEach { Row1(it) } } }
        item {
            Panel("Làm thủ công, tick khi xong", Orange) {
                Text("MIUI không cho app đọc/đổi các mục này, nên bạn tự làm rồi tick.", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                man.forEach { Row1(it) }
            }
        }
    }
}
