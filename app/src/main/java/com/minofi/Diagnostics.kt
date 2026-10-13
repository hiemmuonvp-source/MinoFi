package com.minofi

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import java.net.InetSocketAddress
import java.net.Socket

const val GMAIL = "com.google.android.gm"
const val GMS = "com.google.android.gms"

enum class FixId { BATTERY_SELF, AUTOSTART, POWERKEEPER_GMS, POWERKEEPER_GMAIL, NOTIF_SETTINGS, GMS_INFO, DATA_SAVER, WAKE_FCM, NONE }

data class Check(val title: String, val ok: Boolean?, val detail: String, val fix: FixId = FixId.NONE, val fixLabel: String = "Sửa")

object Diagnostics {

    private fun sysProp(key: String): String = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as String
    } catch (e: Exception) { "" }

    fun isInstalled(c: Context, pkg: String) = try { c.packageManager.getPackageInfo(pkg, 0); true }
    catch (e: PackageManager.NameNotFoundException) { false }

    fun isXiaomi() = Build.MANUFACTURER.equals("xiaomi", true) || Build.MANUFACTURER.equals("redmi", true) || Build.MANUFACTURER.equals("poco", true)
    fun miuiVersion() = sysProp("ro.miui.ui.version.name").ifEmpty { sysProp("ro.mi.os.version.name") }
    fun region() = sysProp("ro.miui.region")
    fun isChinaRom() = region().equals("CN", true) || sysProp("ro.product.mod_device").endsWith("_cn")

    fun run(c: Context): List<Check> {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val out = mutableListOf<Check>()

        out += Check("Thiết bị", if (isXiaomi()) null else false,
            "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} • ROM ${miuiVersion().ifEmpty { "không rõ" }} • vùng ${region().ifEmpty { "?" }}" +
                if (isChinaRom()) "\nROM nội địa (CN): rất hay giết tiến trình nền." else "")

        val gms = isInstalled(c, GMS)
        out += Check("Google Play services", gms,
            if (gms) "Đã cài – FCM chạy qua dịch vụ này." else "KHÔNG có GMS → không nhận được FCM/Gmail push. Cần cài GMS.")

        out += Check("Gmail", isInstalled(c, GMAIL), if (isInstalled(c, GMAIL)) "Đã cài." else "Chưa cài Gmail.")

        val notif = NotificationManagerCompat.from(c).areNotificationsEnabled()
        out += Check("Quyền thông báo MinoFi", notif, if (notif) "Đã bật." else "Đang tắt.", FixId.NOTIF_SETTINGS)

        for ((label, pkg) in listOf("Google Play services" to GMS, "Gmail" to GMAIL)) {
            if (!isInstalled(c, pkg)) continue
            val ign = pm.isIgnoringBatteryOptimizations(pkg)
            out += Check("Doze/Tối ưu pin – $label", ign,
                if (ign) "Được miễn tối ưu pin." else "Đang bị Doze hạn chế → thông báo trễ khi tắt màn hình.",
                FixId.GMS_INFO, "Mở danh sách")
        }
        val selfIgn = pm.isIgnoringBatteryOptimizations(c.packageName)
        out += Check("Tối ưu pin – MinoFi", selfIgn, if (selfIgn) "OK" else "Chưa miễn tối ưu.", FixId.BATTERY_SELF)

        val restricted = if (Build.VERSION.SDK_INT >= 28) am.isBackgroundRestricted else false
        out += Check("Hạn chế nền – MinoFi", !restricted, if (restricted) "Bị hạn chế chạy nền." else "OK")

        val ds = cm.restrictBackgroundStatus
        out += Check("Tiết kiệm dữ liệu", ds == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
            if (ds == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED) "Tắt – tốt." else "Đang bật/whitelist một phần → chặn data nền.",
            FixId.DATA_SAVER)

        val fcm = try { Socket().use { it.connect(InetSocketAddress("mtalk.google.com", 5228), 4000) }; true } catch (e: Exception) { false }
        out += Check("Kết nối FCM (mtalk.google.com:5228)", fcm,
            if (fcm) "Thông." else "Không kết nối được: mạng/DNS/tường lửa chặn cổng 5228-5230 hoặc GMS không có mạng.")

        out += Check("Tự khởi động (Autostart) – GMS & Gmail", null,
            "MIUI không cho app khác đọc trạng thái. Hãy bật thủ công cho Google Play services, Gmail và MinoFi.", FixId.AUTOSTART, "Mở Autostart")
        out += Check("Tiết kiệm pin MIUI – Gmail", null, "Đặt “Không hạn chế” trong Pin & hiệu năng.", FixId.POWERKEEPER_GMAIL, "Mở")
        out += Check("Tiết kiệm pin MIUI – GMS", null, "Đặt “Không hạn chế” cho Google Play services.", FixId.POWERKEEPER_GMS, "Mở")
        out += Check("Đánh thức kết nối FCM", null,
            "Gửi heartbeat tới GMS để nối lại kết nối push (không phải máy nào cũng cho phép).", FixId.WAKE_FCM, "Chạy")
        return out
    }
}
