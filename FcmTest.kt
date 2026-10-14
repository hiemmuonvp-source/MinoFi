package com.minofi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** status: 0 = tốt, 1 = cảnh báo, 2 = lỗi, 3 = bỏ qua (thiếu Shizuku) */
data class TestStep(val title: String, val status: Int, val detail: String, val ms: Long = -1, val weight: Int = 1)
data class TestResult(val steps: List<TestStep>, val score: Int)

object Verify {
    fun bucket(pkg: String): Int? {
        val t = ShizukuHelper.exec("am get-standby-bucket $pkg", 8).out.trim().lines().firstOrNull()?.trim().orEmpty()
        t.toIntOrNull()?.let { return it }
        return when {
            t.contains("EXEMPT", true) -> 5
            t.contains("ACTIVE", true) -> 10
            t.contains("WORKING", true) -> 20
            t.contains("FREQUENT", true) -> 30
            t.contains("RARE", true) -> 40
            t.contains("RESTRICTED", true) -> 45
            else -> null
        }
    }

    fun bgAllowed(pkg: String): Boolean? {
        val r = ShizukuHelper.exec("cmd appops get $pkg RUN_ANY_IN_BACKGROUND", 8)
        if (r.code != 0 && r.out.isBlank()) return null
        val o = r.out.lowercase()
        return !(o.contains("ignore") || o.contains("deny") || o.contains("errored"))
    }
}

object FcmTest {
    const val CODE = "*#*#426#*#*"
    const val TOTAL = 9
    private const val HOST = "mtalk.google.com"

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1_000_000
    private fun sh(cmd: String, t: Long = 15) = ShizukuHelper.exec(cmd, t)
    private fun topIsGms() = sh("dumpsys activity activities | grep -E 'ResumedActivity'", 10).out.contains(GMS)

    private fun dial(c: Context): Boolean = try {
        c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(CODE))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    /** Mở màn hình chẩn đoán FCM của Google (dành cho nút "Xem màn chẩn đoán Google"). */
    fun openGoogleDiag(c: Context) {
        if (ShizukuHelper.granted()) {
            sh("am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://426")
            Thread.sleep(1200)
            if (topIsGms()) return
        }
        dial(c)
    }

    private fun gmsStep(c: Context): TestStep {
        val ok = Diagnostics.isInstalled(c, GMS)
        return TestStep("Google Play services", if (ok) 0 else 2, if (ok) "Đã cài" else "Không có GMS → FCM không hoạt động", weight = 2)
    }

    private fun codeStep(c: Context): TestStep {
        val ready = ShizukuHelper.granted()
        val t0 = System.nanoTime()
        var opened = false
        var dialed = false
        if (ready) {
            sh("am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://426")
            Thread.sleep(1500)
            opened = topIsGms()
        }
        if (!opened) {
            dialed = dial(c)
            if (dialed && ready) { Thread.sleep(1500); opened = topIsGms() }
        }
        if (ready) {
            Thread.sleep(1500)   // để GMS làm mới kết nối rồi quay lại MinoFi
            sh("am start -n ${c.packageName}/.MainActivity --activity-reorder-to-front")
        }
        val t = ms(t0)
        return when {
            opened -> TestStep("Gửi mã *#*#426#*#*", 0, "GMS đã mở màn chẩn đoán FCM", t, 1)
            dialed && !ready -> TestStep("Gửi mã *#*#426#*#*", 1, "Đã gửi qua trình quay số (không xác nhận được). Quay lại MinoFi để xem kết quả.", t, 1)
            else -> TestStep("Gửi mã *#*#426#*#*", 1, "Đã gửi nhưng GMS không mở màn chẩn đoán (ROM có thể chặn mã này)", t, 1)
        }
    }

    private fun dnsStep(): TestStep {
        val t0 = System.nanoTime()
        return try {
            val a = InetAddress.getAllByName(HOST)
            val t = ms(t0)
            TestStep("DNS mtalk.google.com", if (t <= 500) 0 else 1, "${a.size} địa chỉ" + (if (t > 500) " – DNS chậm" else ""), t, 1)
        } catch (e: Exception) {
            TestStep("DNS mtalk.google.com", 2, "Không phân giải được tên miền FCM. Kiểm tra mạng/DNS.", weight = 1)
        }
    }

    private fun tlsStep(): TestStep {
        val raw = Socket()
        return try {
            val t0 = System.nanoTime()
            raw.connect(InetSocketAddress(HOST, 5228), 5000)
            val tcp = ms(t0)
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, HOST, 5228, true) as SSLSocket
            ssl.soTimeout = 5000
            ssl.startHandshake()
            val total = ms(t0)
            TestStep("Kết nối TLS tới FCM (:5228)", if (total <= 800) 0 else 1,
                "TCP $tcp ms • TLS $total ms" + (if (total > 800) " – chậm" else ""), total, 3)
        } catch (e: Exception) {
            TestStep("Kết nối TLS tới FCM (:5228)", 2,
                "Không kết nối được (${e.javaClass.simpleName}). Mạng/DNS/tường lửa có thể chặn cổng 5228-5230.", weight = 3)
        } finally {
            try { raw.close() } catch (_: Exception) {}
        }
    }

    private fun estStep(ready: Boolean): TestStep {
        val title = "GMS đang giữ kết nối FCM"
        if (!ready) return TestStep(title, 3, "Cần Shizuku để đọc bảng kết nối", weight = 3)
        val u = sh("cmd package list packages -U $GMS").out
        val uid = Regex("package:" + Regex.escape(GMS) + "\\s+uid:(\\d+)").find(u)?.groupValues?.get(1)?.toIntOrNull()
            ?: return TestStep(title, 1, "Không đọc được UID của GMS", weight = 3)
        val net = sh("cat /proc/net/tcp /proc/net/tcp6").out
        var n = 0
        for (line in net.lines()) {
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 10 || p[3] != "01") continue
            val port = p[2].substringAfter(':').toIntOrNull(16) ?: continue
            if (port !in 5228..5230) continue
            if (p[7].toIntOrNull() != uid) continue
            n++
        }
        return if (n > 0) TestStep(title, 0, "$n kết nối ESTABLISHED tới cổng 5228-5230", weight = 3)
        else TestStep(title, 1, "Không thấy kết nối :5228-5230 (GMS có thể đang ngủ/bị giết, hoặc dùng cổng 443)", weight = 3)
    }

    private fun hbStep(ready: Boolean): TestStep {
        val title = "Alarm heartbeat FCM"
        if (!ready) return TestStep(title, 3, "Cần Shizuku để đọc alarm hệ thống", weight = 1)
        val line = sh("dumpsys alarm | grep -i -E 'GCM_HB_ALARM|GCM_CONN_ALARM' | head -3").out.trim().lines().firstOrNull().orEmpty()
        return if (line.isNotBlank()) TestStep(title, 0, "GMS đã đặt alarm giữ kết nối", weight = 1)
        else TestStep(title, 1, "Không thấy alarm heartbeat của GMS (kết nối có thể đã đứt)", weight = 1)
    }

    private fun bucketStep(ready: Boolean): TestStep {
        val title = "Standby bucket của GMS"
        if (!ready) return TestStep(title, 3, "Cần Shizuku", weight = 2)
        val b = Verify.bucket(GMS) ?: return TestStep(title, 1, "Không đọc được", weight = 2)
        val name = when { b <= 5 -> "EXEMPTED"; b <= 10 -> "ACTIVE"; b <= 20 -> "WORKING_SET"; b <= 30 -> "FREQUENT"; b <= 40 -> "RARE"; else -> "RESTRICTED" }
        return TestStep(title, if (b <= 10) 0 else if (b <= 30) 1 else 2, name + (if (b > 10) " – bị hạn chế, hãy bấm ⚡ FIX TẤT CẢ" else ""), weight = 2)
    }

    private fun opsStep(ready: Boolean): TestStep {
        val title = "Quyền chạy nền của GMS"
        if (!ready) return TestStep(title, 3, "Cần Shizuku", weight = 2)
        return when (Verify.bgAllowed(GMS)) {
            true -> TestStep(title, 0, "RUN_ANY_IN_BACKGROUND = allow", weight = 2)
            false -> TestStep(title, 2, "Đang bị chặn chạy nền → bấm ⚡ FIX TẤT CẢ", weight = 2)
            null -> TestStep(title, 1, "Không đọc được", weight = 2)
        }
    }

    private fun dozeStep(c: Context): TestStep {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val g = pm.isIgnoringBatteryOptimizations(GMS)
        val m = if (Diagnostics.isInstalled(c, GMAIL)) pm.isIgnoringBatteryOptimizations(GMAIL) else true
        return when {
            g && m -> TestStep("Miễn Doze (GMS & Gmail)", 0, "Cả hai đã được miễn tối ưu pin", weight = 2)
            g -> TestStep("Miễn Doze (GMS & Gmail)", 1, "GMS ổn, Gmail vẫn bị Doze", weight = 2)
            else -> TestStep("Miễn Doze (GMS & Gmail)", 2, "GMS bị Doze → tin đến trễ khi tắt màn hình", weight = 2)
        }
    }

    fun run(c: Context, onStep: (Int, Int, TestStep) -> Unit): TestResult {
        val ready = ShizukuHelper.granted()
        val jobs = listOf<Pair<String, () -> TestStep>>(
            "Google Play services" to { gmsStep(c) },
            "Gửi mã *#*#426#*#*" to { codeStep(c) },
            "DNS" to { dnsStep() },
            "TLS" to { tlsStep() },
            "Kết nối" to { estStep(ready) },
            "Heartbeat" to { hbStep(ready) },
            "Bucket" to { bucketStep(ready) },
            "Chạy nền" to { opsStep(ready) },
            "Doze" to { dozeStep(c) }
        )
        val steps = mutableListOf<TestStep>()
        jobs.forEachIndexed { i, (name, job) ->
            val s = try { job() } catch (e: Throwable) { TestStep(name, 2, e.message ?: "Lỗi không rõ") }
            steps += s
            onStep(i, jobs.size, s)
        }
        val used = steps.filter { it.status != 3 }
        val tot = used.sumOf { it.weight }
        val got = used.sumOf { it.weight * (if (it.status == 0) 1.0 else if (it.status == 1) 0.5 else 0.0) }
        val score = if (tot == 0) 0 else (got * 100 / tot).toInt()

        val p = c.mp()
        val h = (p.getString("fcmHist", "").orEmpty().split(",").filter { it.isNotBlank() } + score.toString()).takeLast(12)
        p.edit().putInt("fcmScore", score).putLong("fcmTime", System.currentTimeMillis())
            .putString("fcmHist", h.joinToString(",")).apply()
        return TestResult(steps, score)
    }
}

private fun rttColor(ms: Long) = if (ms < 300) Green else if (ms < 1000) Yellow else Orange

@Composable
fun FcmTab(ctx: Context) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ShizukuHelper.state(ctx)) }
    LaunchedEffect(Unit) { while (true) { state = ShizukuHelper.state(ctx); delay(1000) } }

    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var steps by remember { mutableStateOf(listOf<TestStep>()) }
    var score by remember { mutableIntStateOf(ctx.mp().getInt("fcmScore", -1)) }
    var timeText by remember {
        val t = ctx.mp().getLong("fcmTime", 0L)
        mutableStateOf(if (t == 0L) "" else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(t)))
    }
    val hist = ctx.mp().getString("fcmHist", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)

    val color = when { score < 0 -> Color.Gray; score >= 80 -> Green; score >= 50 -> Yellow; else -> Red }
    val anim by animateFloatAsState(if (running) done / FcmTest.TOTAL.toFloat() else if (score < 0) 0f else score / 100f, label = "fcm")

    fun start() {
        if (running) return
        running = true; done = 0; steps = emptyList()
        scope.launch(Dispatchers.IO) {
            val r = FcmTest.run(ctx) { i, _, s -> done = i + 1; steps = steps + s }
            score = r.score
            timeText = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
            running = false
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item {
            Panel("📡 ĐO FCM (Firebase Cloud Messaging)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(anim, if (running) "${done * 100 / FcmTest.TOTAL}%" else if (score < 0) "–" else "$score%",
                        if (running) "ĐANG ĐO" else "SỨC KHỎE FCM", color, 140.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when { running -> "Đang đo…"; score < 0 -> "Chưa đo lần nào"; score >= 80 -> "FCM ổn định"; score >= 50 -> "FCM chưa tốt"; else -> "FCM có vấn đề" },
                            color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (timeText.isNotEmpty()) Text("Lần đo: $timeText", color = Color.Gray, fontSize = 12.sp)
                        Chip(if (state == ShizukuState.READY) "Shizuku ✔ đo đầy đủ" else "Chưa có Shizuku: đo hạn chế",
                            if (state == ShizukuState.READY) Green else Orange)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { start() }, enabled = !running, colors = btn,
                    modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (running) "Đang đo… $done/${FcmTest.TOTAL}" else "📡 ĐO FCM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { done / FcmTest.TOTAL.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Yellow, trackColor = Card2)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { FcmTest.openGoogleDiag(ctx) } }) {
                    Text("Xem màn chẩn đoán của Google (*#*#426#*#*)", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (steps.isNotEmpty()) {
            item {
                Panel("Kết quả chi tiết") {
                    steps.forEach { s ->
                        val (icon, col) = when (s.status) { 0 -> "✔" to Green; 1 -> "⚠" to Orange; 2 -> "✖" to Red; else -> "➖" to Color.Gray }
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Text(icon, color = col, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(s.detail, color = if (s.status == 0) Color.Gray else col, fontSize = 11.sp)
                            }
                            if (s.ms >= 0) Chip("${s.ms} ms", rttColor(s.ms))
                        }
                    }
                }
            }
        }

        if (hist.size >= 2) {
            item {
                Panel("Lịch sử điểm FCM (${hist.size} lần đo)") {
                    Sparkline(hist.map { it.toFloat() }, color, Modifier.fillMaxWidth().height(60.dp))
                    Text("Đo trước và sau khi bấm ⚡ FIX để thấy hiệu quả.", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }

        item {
            Panel("Mã *#*#426#*#* là gì?") {
                Text("Đây là mã chẩn đoán FCM của Google Play services. MinoFi gửi mã này để GMS làm mới kết nối. " +
                    "App không đọc được nội dung màn hình của Google, nên MinoFi tự đo thêm DNS, TLS, kết nối đang mở, " +
                    "alarm heartbeat, standby bucket và quyền chạy nền của GMS rồi gộp thành điểm %.\n\n" +
                    "Không có Shizuku thì chỉ đo được DNS, TLS và Doze, các mục còn lại bị bỏ qua.",
                    color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}
