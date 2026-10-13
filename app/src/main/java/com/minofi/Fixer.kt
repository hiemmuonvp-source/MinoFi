package com.minofi

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Fixer {

    private fun start(c: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) {}
        }
        return false
    }

    private fun appInfo(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    private fun powerKeeper(pkg: String, label: String) = Intent().apply {
        component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        putExtra("package_name", pkg); putExtra("package_label", label)
    }

    @SuppressLint("BatteryLife")
    fun apply(c: Context, id: FixId): String = when (id) {
        FixId.BATTERY_SELF -> {
            start(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); "Chọn “Cho phép”."
        }
        FixId.GMS_INFO -> {
            start(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appInfo(GMS))
            "Chọn “Tất cả ứng dụng” → Google Play services & Gmail → Không tối ưu."
        }
        FixId.AUTOSTART -> {
            val ok = start(c,
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                appInfo(c.packageName))
            if (ok) "Bật công tắc cho Google Play services, Gmail, MinoFi." else "Không mở được."
        }
        FixId.POWERKEEPER_GMAIL -> { start(c, powerKeeper(GMAIL, "Gmail"), appInfo(GMAIL)); "Chọn “Không hạn chế”." }
        FixId.POWERKEEPER_GMS -> { start(c, powerKeeper(GMS, "Google Play services"), appInfo(GMS)); "Chọn “Không hạn chế”." }
        FixId.NOTIF_SETTINGS -> {
            start(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)); "Bật thông báo."
        }
        FixId.DATA_SAVER -> {
            start(c, Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$GMS")),
                Intent(Settings.ACTION_DATA_USAGE_SETTINGS)); "Cho phép dữ liệu nền cho GMS & Gmail hoặc tắt Tiết kiệm dữ liệu."
        }
        FixId.WAKE_FCM -> { wakeFcm(c); "Đã gửi heartbeat." }
        FixId.NONE -> ""
    }

    fun wakeFcm(c: Context) {
        for (a in listOf("com.google.android.intent.action.GTALK_HEARTBEAT", "com.google.android.intent.action.MCS_HEARTBEAT")) {
            try { c.sendBroadcast(Intent(a).setPackage(GMS)) } catch (_: Exception) {}
        }
    }
}
