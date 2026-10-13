package com.minofi

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import java.io.File
import kotlin.math.abs

data class Snap(
    val pct: Int, val batTemp: Float, val mv: Int, val mA: Int, val watts: Float,
    val charging: Boolean, val plugged: String, val health: String, val tech: String,
    val cpuTemp: Float?, val gpuTemp: Float?, val thermal: String, val headroom: Float?,
    val freqsMHz: List<Int>, val ramUsedMb: Long, val ramTotalMb: Long, val remainingH: Float?
)

object SystemMonitor {
    private var cpuZones: List<File>? = null
    private var gpuZones: List<File>? = null

    private fun scan() {
        val cpu = mutableListOf<File>(); val gpu = mutableListOf<File>()
        try {
            File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { z ->
                val type = (try { File(z, "type").readText().trim().lowercase() } catch (e: Exception) { null }) ?: return@forEach
                val t = File(z, "temp")
                when {
                    type.contains("gpu") -> gpu += t
                    type.contains("cpu") || type.startsWith("soc") -> cpu += t
                }
            }
        } catch (_: Exception) {}
        cpuZones = cpu; gpuZones = gpu
    }

    private fun maxTemp(files: List<File>): Float? {
        var best: Float? = null
        for (f in files) {
            val v = (try { f.readText().trim().toFloatOrNull() } catch (e: Exception) { null }) ?: continue
            val c = when { v > 1000 -> v / 1000f; v > 150 -> v / 10f; else -> v }
            if (c in 1f..130f && (best == null || c > best)) best = c
        }
        return best
    }

    private fun freqs(): List<Int> {
        val out = mutableListOf<Int>()
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val v = try { File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toIntOrNull() } catch (e: Exception) { null }
            if (v != null) out += v / 1000
        }
        return out
    }

    private fun thermalText(s: Int) = when (s) {
        0 -> "Bình thường"; 1 -> "Hơi nóng"; 2 -> "Nóng (bắt đầu giảm xung)"; 3 -> "Rất nóng"
        4 -> "Nguy hiểm"; 5 -> "Khẩn cấp"; 6 -> "Sắp tắt máy"; else -> "Không rõ"
    }

    fun refreshHz(c: Context): Float = try {
        (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).refreshRate
    } catch (e: Exception) { 0f }

    fun read(c: Context): Snap {
        if (cpuZones == null) scan()
        val bi = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val mv = bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plug = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = when (bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            2 -> "Tốt"; 3 -> "Quá nóng"; 4 -> "Hỏng"; 5 -> "Quá áp"; 7 -> "Quá lạnh"; else -> "Không rõ"
        }
        val tech = bi?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"
        val charging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) && plug > 0
        val plugText = when {
            plug and 4 != 0 -> "Sạc không dây"; plug and 1 != 0 -> "Sạc AC"; plug and 2 != 0 -> "Sạc USB"; else -> "Không cắm sạc"
        }

        val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val a = if (raw == Int.MIN_VALUE) 0 else abs(raw)
        val mA = if (a > 20_000) a / 1000 else a
        val watts = mA / 1000f * mv / 1000f
        val cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val remain = if (!charging && mA > 20 && cc > 0) (cc / 1000f) / mA else null

        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) thermalText(pm.currentThermalStatus) else "Không hỗ trợ"
        val head = if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10).takeIf { !it.isNaN() } else null

        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)

        return Snap(pct, temp, mv, mA, watts, charging, plugText, health, tech,
            maxTemp(cpuZones.orEmpty()), maxTemp(gpuZones.orEmpty()), thermal, head, freqs(),
            (mi.totalMem - mi.availMem) / 1048576, mi.totalMem / 1048576, remain)
    }
}
