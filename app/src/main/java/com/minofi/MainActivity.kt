package com.minofi

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Black, surface = CardBg)) {
                App(this, tick.intValue)
            }
        }
    }
    override fun onResume() { super.onResume(); tick.intValue++ }
}

@Composable
fun App(ctx: Context, trigger: Int) {
    var tab by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf<List<Check>>(emptyList()) }
    var token by remember { mutableStateOf(ctx.mp().getString("token", "") ?: "") }
    var statsTick by remember { mutableIntStateOf(0) }
    var autoDone by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) { checks = withContext(Dispatchers.IO) { Diagnostics.run(ctx) } }
    LaunchedEffect(Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token = it; ctx.mp().edit().putString("token", it).apply() }
        } catch (_: Exception) {}
        while (true) {
            delay(3000); statsTick++
            // Tự áp dụng 1 lần mỗi phiên khi Shizuku sẵn sàng
            if (!autoDone && ctx.mp().getBoolean("autoapply", false) && ShizukuHelper.granted()) {
                autoDone = true
                withContext(Dispatchers.IO) { FixEngine.runFromPrefs(ctx) }
            }
        }
    }
    val st = remember(statsTick, trigger) { Probes.stats(ctx) }
    val lastMs = st.last.lastOrNull { !it.lost }?.latency

    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF141414)) {
                val items = listOf("📡" to "FCM", "⚡" to "Fix", "💻" to "ADB", "📊" to "Hệ thống")
                val colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = Yellow, unselectedTextColor = Color.Gray, indicatorColor = Color(0xFF3A3300))
                items.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) }, label = { Text(label, fontSize = 11.sp) }, colors = colors)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Text("Design by Lê Hoàng Hưng", color = Yellow, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), null, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MinoFi", color = Yellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("FCM Notification Fixer • Xiaomi", color = Color.LightGray, fontSize = 12.sp)
                }
                Chip(if (lastMs == null) "FCM: chưa đo" else "FCM: " + String.format("%.1fs", lastMs / 1000.0),
                    if (lastMs == null) Color.Gray else latencyColor(lastMs))
            }
            when (tab) {
                0 -> FcmScreen(ctx, checks, token, statsTick)
                1 -> FixTab(ctx, checks)
                2 -> ShellTab(ctx)
                else -> MonitorScreen(ctx)
            }
        }
    }
}
