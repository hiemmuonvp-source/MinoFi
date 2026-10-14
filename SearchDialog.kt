package com.minofi

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.text.Normalizer

private class Feature(val name: String, val keywords: String, val tab: Int)

// tab: 0 FCM, 1 Tiến độ, 2 Fix, 3 ADB, 4 Hệ thống
private val features = listOf(
    Feature("📡 Đo FCM", "fcm firebase do ping 426 ma chan doan", 0),
    Feature("📈 Tiến độ fix thông báo (%)", "tien do phan tram progress", 1),
    Feature("⚡ Fix tất cả 1 chạm", "fix thong bao doze chay nen 1 cham", 2),
    Feature("🔌 Shizuku", "shizuku adb khong day cap quyen", 2),
    Feature("✔ Chẩn đoán lỗi / Autostart / Pin MIUI", "chan doan loi autostart pin miui tiet kiem", 2),
    Feature("💻 Soạn lệnh ADB tùy chỉnh", "adb shell lenh script soan", 3),
    Feature("🌡 Nhiệt độ CPU / GPU / Pin", "nhiet do cpu gpu pin nong", 4),
    Feature("⚡ Công suất pin (W)", "cong suat watt pin sac xa", 4),
    Feature("🎞 FPS & tần số quét", "fps man hinh hz", 4),
    Feature("🧠 Xung CPU & RAM", "xung cpu ram bo nho", 4)
)

private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

private fun loadApps(c: Context): List<Pair<String, String>> {
    val pm = c.packageManager
    return try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    } catch (e: Exception) { emptyList() }
}

@Composable
fun SearchDialog(ctx: Context, onDismiss: () -> Unit, onGoto: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var extras by remember { mutableStateOf(ctx.mp().getString("extras", "").orEmpty()) }
    val apps = remember { loadApps(ctx) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); try { fr.requestFocus() } catch (_: Exception) {} }

    val fq = fold(q.trim())
    val feats = features.filter { fq.isEmpty() || fold(it.name + " " + it.keywords).contains(fq) }
    val matchApps = if (fq.isEmpty()) emptyList()
        else apps.filter { fold(it.first).contains(fq) || it.second.lowercase().contains(fq) }.take(15)
    val added = extras.split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).background(CardBg, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("🔍 Tìm kiếm", color = Yellow, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(fr),
                placeholder = { Text("Tính năng hoặc tên app (vd: zalo, nhiệt độ, adb)") }, colors = tfColors())
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (feats.isNotEmpty()) item { Text("Tính năng", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                items(feats) { f ->
                    Text(f.name, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onGoto(f.tab); onDismiss() }.padding(vertical = 10.dp))
                }
                if (matchApps.isNotEmpty()) item {
                    Text("App đã cài – thêm vào danh sách ⚡ Fix", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                items(matchApps) { (label, pkg) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Color.White, fontSize = 14.sp)
                            Text(pkg, color = Color.Gray, fontSize = 10.sp)
                        }
                        if (pkg in added) Text("✔ Đã thêm", color = Green, fontSize = 12.sp)
                        else Button(
                            onClick = {
                                extras = if (extras.isBlank()) pkg else "$extras,$pkg"
                                ctx.mp().edit().putString("extras", extras).apply()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) { Text("Thêm", fontSize = 12.sp) }
                    }
                }
                if (feats.isEmpty() && matchApps.isEmpty()) item { Text("Không có kết quả", color = Color.Gray, fontSize = 13.sp) }
            }
        }
    }
}
