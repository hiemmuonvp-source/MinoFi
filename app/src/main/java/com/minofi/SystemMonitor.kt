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
    val freqsMHz: List<Int>, val ramUsedMb: Long, val ramTotalMb: Long, val remainingH: Float?,
    val tempSource: String = ""
)

object SystemMonitor {
    private class Zone(val dir: File, val type: String) { val temp = File(dir, "temp") }

    private var cpuZ: List<Zone>? = null
    private var gpuZ: List<Zone>? = null
    private val hist = HashMap<String, ArrayDeque<Float>>()

    // Các sensor KHÔNG phải nhiệt độ thật (ngưỡng, bước giảm xung, ảo...)
    private val bad = Regex("step|lowf|limit|trip|hot|warm|cold|virt|cdev|mitigat|skin|bcl|ibat|vbat|quiet|xo|ddr|modem|mdm|wifi|wlan|charger|usb|conn|camera|flash|pa-|bat")
    private val tRe = Regex("Temperature\\{mValue=(-?[0-9.]+), mType=(\\d+), mName=([^,}]+)")

    private var shCache: Pair<Float?, Float?>? = null
    private var shAt = 0L

    private fun scan() {
        val cpu = mutableListOf<Zone>()
        val gpu = mutableListOf<Zone>()
        try {
            File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { d ->
                val type = (try { File(d, "type").readText().trim().lowercase() } catch (e: Exception) { null }) ?: return@forEach
                if (bad.containsMatchIn(type)) return@forEach
                if (type.contains("gpu") || type == "g3d") {
                    gpu.add(Zone(d, type))
                } else if (type.contains("cpu") || type == "big" || type == "mid" || type == "little" || type.startsWith("soc")) {
                    cpu.add(Zone(d, type))
                }
            }
        } catch (_: Exception) {}
        cpuZ = cpu.filter { it.type.contains("usr") }.ifEmpty { cpu }
        gpuZ = gpu.filter { it.type.contains("usr") }.ifEmpty { gpu }
    }

    private fun readZone(z: Zone): Float? {
        val v = (try { z.temp.readText().trim().toFloatOrNull() } catch (e: Exception) { null }) ?: return null
        val c = when { v > 1000 -> v / 1000f; v > 150 -> v / 10f; else -> v }
        if (c < 5f || c > 110f) return null
        // Loại cảm biến trả về đúng điểm trip (ngưỡng)
        for (i in 0..9) {
            val tp = (try { File(z.dir, "trip_point_${i}_temp").readText().trim().toFloatOrNull() } catch (e: Exception) { null }) ?: continue
            val t = if (tp > 1000) tp / 1000f else tp
            if (abs(t - c) < 0.6f) return null
        }
        // Loại cảm biến đứng yên ở mức cao (nhiều khả năng là hằng số/ngưỡng)
        val h = hist.getOrPut(z.dir.path) { ArrayDeque() }
        h.addLast(c)
        if (h.size > 8) h.removeFirst()
        if (h.size >= 8 && c >= 80f && h.all { it == c }) return null
        return c
    }

    private fun maxTemp(zones: List<Zone>): Float? {
        var best: Float? = null
        for (z in zones) {
            val c = readZone(z) ?: continue
            if (best == null || c > best) best = c
        }
        return best
    }

    /** Nhiệt độ thật qua Shizuku: chỉ lấy dòng "Temperature{...}", bỏ phần ngưỡng "TemperatureThreshold{...}". */
    private fun fromThermalService(): Pair<Float?, Float?>? {
        val now = System.currentTimeMillis()
        if (now - shAt < 2500) return shCache
        shAt = now
        if (!ShizukuHelper.granted()) { shCache = null; return null }
        val r = ShizukuHelper.exec("dumpsys thermalservice", 8)
        if (r.out.isBlank()) { shCache = null; return null }
        var cpu: Float? = null
        var gpu: Float? = null
        for (m in tRe.findAll(r.out)) {
            val v = m.groupValues[1].toFloatOrNull() ?: continue
            val type = m.groupValues[2].toIntOrNull() ?: continue
            val name = m.groupValues[3].lowercase()
            if (v < 5f || v > 110f || bad.containsMatchIn(name)) continue
            if (type == 0 && (cpu == null || v > cpu)) cpu = v
            if (type == 1 && (gpu == null || v > gpu)) gpu = v
        }
        shCache = if (cpu == null && gpu == null) null else Pair(cpu, gpu)
        return shCache
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
        if (cpuZ == null) scan()
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

        // Nhiệt độ CPU/GPU
        val sh = fromThermalService()
        val cpuT: Float?
        val gpuT: Float?
        val src: String
        if (sh != null) {
            cpuT = sh.first ?: maxTemp(cpuZ.orEmpty())
            gpuT = sh.second ?: maxTemp(gpuZ.orEmpty())
            src = "Shizuku"
        } else {
            cpuT = maxTemp(cpuZ.orEmpty())
            gpuT = maxTemp(gpuZ.orEmpty())
            src = if (cpuT != null || gpuT != null) "cảm biến đã lọc ngưỡng" else ""
        }

        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)

        return Snap(pct, temp, mv, mA, watts, charging, plugText, health, tech,
            cpuT, gpuT, thermal + (if (src.isNotEmpty()) " • nguồn: $src" else ""), head, freqs(),
            (mi.totalMem - mi.availMem) / 1048576, mi.totalMem / 1048576, remain, src)
    }
}
