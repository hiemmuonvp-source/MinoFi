package com.minofi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED || !c.mp().getBoolean("autoapply", false)) return
        val app = c.applicationContext
        val pending = goAsync()
        Thread {
            try {
                var n = 0
                while (!ShizukuHelper.granted() && n < 15) { Thread.sleep(3000); n++ }
                if (ShizukuHelper.granted()) FixEngine.runFromPrefs(app)
            } finally { pending.finish() }
        }.start()
    }
}
