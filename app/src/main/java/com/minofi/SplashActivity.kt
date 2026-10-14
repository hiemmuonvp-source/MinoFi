package com.minofi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.random.Random

class SplashActivity : ComponentActivity() {
    private var gone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent { SplashScreen(onDone = { goMain() }) }
    }

    private fun goMain() {
        if (gone) return
        gone = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** Hiệu ứng nảy khi chạm đáy (easeOutBounce). */
private val BounceOut = Easing { t ->
    val n = 7.5625f
    val d = 2.75f
    when {
        t < 1f / d -> n * t * t
        t < 2f / d -> { val u = t - 1.5f / d; n * u * u + 0.75f }
        t < 2.5f / d -> { val u = t - 2.25f / d; n * u * u + 0.9375f }
        else -> { val u = t - 2.625f / d; n * u * u + 0.984375f }
    }
}

private class Drop(val x: Float, val speed: Float, val len: Float, val phase: Float)

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val name = Normalizer.normalize("Lê Hoàng Hưng", Normalizer.Form.NFC)
    val letters = remember { name.toList() }
    val fall = remember { letters.map { Animatable(0f) } }
    val sub = remember { Animatable(0f) }

    val density = LocalDensity.current
    val heightDp = LocalConfiguration.current.screenHeightDp
    val screenH = with(density) { heightDp.dp.toPx() }

    var time by remember { mutableFloatStateOf(0f) }
    val drops = remember {
        List(36) { Drop(Random.nextFloat(), 0.12f + Random.nextFloat() * 0.3f, 0.05f + Random.nextFloat() * 0.12f, Random.nextFloat()) }
    }

    // đồng hồ cho mưa nền
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = (it - t0) / 1_000_000_000f }
        }
    }

    // từng chữ rơi lần lượt rồi vào app
    LaunchedEffect(Unit) {
        letters.indices.forEach { i ->
            launch {
                delay(300L + i * 140L)
                fall[i].animateTo(1f, tween(1000, easing = BounceOut))
            }
        }
        delay(300L + letters.size * 140L + 1000L)
        sub.animateTo(1f, tween(600))
        delay(1100)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drops.forEach { d ->
                val y = ((time * d.speed + d.phase) % 1.2f - 0.1f) * size.height
                val x = d.x * size.width
                drawLine(
                    Yellow.copy(alpha = 0.16f), Offset(x, y), Offset(x, y + d.len * size.height),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                letters.forEachIndexed { i, ch ->
                    Text(
                        ch.toString(), color = Yellow, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.graphicsLayer { translationY = -(1f - fall[i].value) * screenH }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "MinoFi  •  FCM Notification Fixer", color = Color.LightGray, fontSize = 14.sp,
                modifier = Modifier.graphicsLayer { alpha = sub.value }
            )
        }
    }
}
