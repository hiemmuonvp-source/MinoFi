package com.minofi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val snippets = listOf(
    "Doze whitelist" to "dumpsys deviceidle whitelist",
    "Bucket GMS" to "am get-standby-bucket com.google.android.gms",
    "AppOps GMS" to "cmd appops get com.google.android.gms",
    "Pin" to "dumpsys battery",
    "Bản MIUI" to "getprop ro.miui.ui.version.name"
)

private fun loadHist(c: Context) = c.mp().getString("hist", "").orEmpty().split('\u001F').filter { it.isNotBlank() }
private fun saveHist(c: Context, l: List<String>) =
    c.mp().edit().putString("hist", l.takeLast(10).joinToString("\u001F")).apply()

@Composable
fun ShellTab(ctx: Context) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(ctx.mp().getString("custom", "") ?: "") }
    var customOn by remember { mutableStateOf(ctx.mp().getBoolean("customOn", false)) }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var hist by remember { mutableStateOf(loadHist(ctx)) }
    var state by remember { mutableStateOf(ShizukuHelper.state(ctx)) }
    LaunchedEffect(Unit) { while (true) { state = ShizukuHelper.state(ctx); delay(1000) } }
    val btn = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Black)

    fun setInput(v: String) { input = v; ctx.mp().edit().putString("custom", v).apply() }

    fun runIt() {
        if (running) return
        val lines = input.lines().map { FixEngine.clean(it) }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return
        running = true
        scope.launch(Dispatchers.IO) {
            val sb = StringBuilder(output)
            for (l in lines) {
                sb.append("$ ").append(l).append('\n'); output = sb.toString().takeLast(8000)
                val r = ShizukuHelper.exec(l, 30)
                sb.append(r.out)
                if (r.err.isNotBlank()) sb.append(r.err)
                sb.append("\n[exit ").append(r.code).append("]\n\n"); output = sb.toString().takeLast(8000)
            }
            hist = (hist + lines).distinct().takeLast(10); saveHist(ctx, hist)
            running = false
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item {
            Panel("💻 ADB SHELL (qua Shizuku)") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state == ShizukuState.READY) Chip("Sẵn sàng • ${ShizukuHelper.mode()}", Green)
                    else Chip("Shizuku chưa sẵn sàng – sang tab ⚡ Fix để thiết lập", Orange)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(input, { setInput(it) }, label = { Text("Lệnh (mỗi dòng 1 lệnh, # là ghi chú)") },
                    placeholder = { Text("dumpsys deviceidle whitelist +com.zing.zalo") },
                    minLines = 4, maxLines = 10, colors = tfColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runIt() }, enabled = !running, colors = btn) { Text(if (running) "Đang chạy…" else "▶ Chạy") }
                    OutlinedButton(onClick = { setInput("") }) { Text("Xóa ô", color = Color.White) }
                    OutlinedButton(onClick = { output = "" }) { Text("Xóa log", color = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Switch(checked = customOn, onCheckedChange = { customOn = it; ctx.mp().edit().putBoolean("customOn", it).apply() })
                    Spacer(Modifier.width(8.dp))
                    Text("Chạy script này cùng ⚡ FIX TẤT CẢ", color = Color.White, fontSize = 13.sp)
                }
                Text("Lệnh chạy với quyền shell/root của Shizuku. Đừng dán lệnh từ nguồn lạ.", color = Color.Gray, fontSize = 11.sp)
            }
        }
        item {
            Panel("Lệnh mẫu") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(snippets) { (name, cmd) ->
                        Box(Modifier.clickable { setInput(if (input.isBlank()) cmd else input.trimEnd() + "\n" + cmd) }) { Chip(name, Yellow) }
                    }
                }
                if (hist.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Lịch sử (bấm để chèn)", color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hist.reversed()) { h ->
                            Box(Modifier.clickable { setInput(if (input.isBlank()) h else input.trimEnd() + "\n" + h) }) {
                                Chip(h.take(34), Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel("Kết quả") {
                Box(Modifier.fillMaxWidth().background(Black, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    SelectionContainer {
                        Text(output.ifEmpty { "(chưa có output)" }, color = Color(0xFF9EE493),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("adb", output))
                    Toast.makeText(ctx, "Đã chép kết quả", Toast.LENGTH_SHORT).show()
                }) { Text("Chép kết quả", color = Color.White) }
            }
        }
    }
}
