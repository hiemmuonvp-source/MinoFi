package com.minofi

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

data class ShellResult(val code: Int, val out: String, val err: String)

object ShizukuHelper {
    const val PKG = "moe.shizuku.privileged.api"

    fun running(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun state(c: Context): ShizukuState = when {
        granted() -> ShizukuState.READY
        running() -> ShizukuState.NO_PERMISSION
        Diagnostics.isInstalled(c, PKG) -> ShizukuState.NOT_RUNNING
        else -> ShizukuState.NOT_INSTALLED
    }

    fun requestPermission() {
        try { if (running()) Shizuku.requestPermission(4242) } catch (_: Throwable) {}
    }

    fun mode(): String = try { if (Shizuku.getUid() == 0) "Root" else "ADB (shell)" } catch (e: Throwable) { "?" }

    fun exec(cmd: String, timeoutSec: Long = 20): ShellResult {
        if (!granted()) return ShellResult(-1, "", "Shizuku chưa sẵn sàng hoặc chưa cấp quyền")
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            var out = ""; var err = ""; var code = -2
            val t1 = Thread { out = try { p.inputStream.bufferedReader().readText() } catch (e: Exception) { "" } }
            val t2 = Thread { err = try { p.errorStream.bufferedReader().readText() } catch (e: Exception) { "" } }
            val w = Thread { code = try { p.waitFor() } catch (e: Exception) { -3 } }
            t1.start(); t2.start(); w.start()
            w.join(timeoutSec * 1000)
            if (w.isAlive) {
                try { p.destroy() } catch (_: Exception) {}
                err += "\n[timeout]"; code = -4
            }
            t1.join(1000); t2.join(1000)
            ShellResult(code, out, err)
        } catch (e: Throwable) {
            ShellResult(-5, "", (e.cause ?: e).message ?: e.toString())
        }
    }
}
