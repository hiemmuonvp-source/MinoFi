package com.minofi

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Cmd(val title: String, val cmd: String, val soft: Boolean = false)
/** status: 0 = OK, 1 = bỏ qua, 2 = lỗi */
data class FixResult(val title: String, val status: Int, val detail: String)
data class FixSummary(val results: List<FixResult>) {
    val ok get() = results.count { it.status == 0 }
    val skipped get() = results.count { it.status == 1 }
    val fail get() = results.count { it.status == 2 }
}

object FixEngine {
    private val pkgRegex = Regex("[A-Za-z0-9_.]+")

    fun clean(s: String) = s.trim().removePrefix("adb shell ").removePrefix("adb -d shell ").removePrefix("shell ").trim()

    fun pkgs(c: Context): List<String> {
        val extras = c.mp().getString("extras", "").orEmpty()
            .split(",", " ", "\n").map { it.trim() }.filter { it.matches(pkgRegex) }
        return (listOf(GMS, GMAIL, c.packageName) + extras).distinct()
    }

    private fun perPackage(p: String) = listOf(
        Cmd("Doze whitelist · $p", "dumpsys deviceidle whitelist +$p"),
        Cmd("Chạy nền · $p", "cmd appops set $p RUN_IN_BACKGROUND allow && cmd appops set $p RUN_ANY_IN_BACKGROUND allow"),
        Cmd("Wake lock · $p", "cmd appops set $p WAKE_LOCK allow", soft = true),
        Cmd("Standby ACTIVE · $p", "am set-standby-bucket $p active; am set-inactive $p false", soft = true),
        Cmd("Dữ liệu nền · $p",
            "U=\$(cmd package list packages -U | grep 'package:$p ' | sed 's/.*uid://' | head -1); " +
            "[ -n \"\$U\" ] && cmd netpolicy add restrict-background-whitelist \$U", soft = true),
        Cmd("Autostart MIUI · $p", "cmd appops set $p 10008 allow", soft = true)
    )

    private val globalCore = listOf(
        Cmd("Quyền thông báo Gmail", "pm grant $GMAIL android.permission.POST_NOTIFICATIONS", soft = true),
        Cmd("Đánh thức FCM (MCS)", "am broadcast -a com.google.android.intent.action.MCS_HEARTBEAT -p $GMS", soft = true),
        Cmd("Đánh thức FCM (GTALK)", "am broadcast -a com.google.android.intent.action.GTALK_HEARTBEAT -p $GMS", soft = true)
    )

    val advanced = listOf(
        Cmd("Tắt App Standby", "settings put global app_standby_enabled 0", soft = true),
        Cmd("Tắt Adaptive Battery", "settings put global adaptive_battery_management_enabled 0", soft = true),
        Cmd("Tắt Cached Apps Freezer", "settings put global cached_apps_freezer disabled", soft = true)
    )

    val revert = listOf(
        Cmd("Bật lại App Standby", "settings put global app_standby_enabled 1", soft = true),
        Cmd("Bật lại Adaptive Battery", "settings put global adaptive_battery_management_enabled 1", soft = true),
        Cmd("Khôi phục Freezer", "settings delete global cached_apps_freezer", soft = true)
    )

    fun buildPlan(c: Context, adv: Boolean, customOn: Boolean, custom: String): List<Cmd> {
        val plan = mutableListOf<Cmd>()
        pkgs(c).forEach { plan += perPackage(it) }
        plan += globalCore
        if (adv) plan += advanced
        if (customOn) custom.lines().map { clean(it) }.filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { plan += Cmd("Script: $it", it, soft = true) }
        return plan
    }

    fun planFromPrefs(c: Context): List<Cmd> {
        val p = c.mp()
        return buildPlan(c, p.getBoolean("adv", false), p.getBoolean("customOn", false), p.getString("custom", "").orEmpty())
    }

    fun run(plan: List<Cmd>, onStep: (Int, Int, FixResult) -> Unit = { _, _, _ -> }): FixSummary {
        val res = mutableListOf<FixResult>()
        plan.forEachIndexed { i, cmd ->
            val r = ShizukuHelper.exec(cmd.cmd)
            val bad = r.code != 0 || r.err.contains("Exception") || r.out.contains("Exception") || r.out.contains("Unknown")
            val first = (r.out + "\n" + r.err).trim().lines().firstOrNull().orEmpty().take(70)
            val fr = FixResult(cmd.title, if (!bad) 0 else if (cmd.soft) 1 else 2, first)
            res += fr
            onStep(i, plan.size, fr)
        }
        return FixSummary(res)
    }

    fun saveLast(c: Context, s: FixSummary): String {
        val t = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val txt = "$t  •  ✔ ${s.ok}  ⚠ ${s.skipped}  ✖ ${s.fail}"
        c.mp().edit().putString("lastFix", txt).apply()
        return txt
    }

    fun runFromPrefs(c: Context): FixSummary {
        val s = run(planFromPrefs(c)); saveLast(c, s); return s
    }
}
