package com.minofi

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MinoFiMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        getSharedPreferences("minofi", Context.MODE_PRIVATE).edit().putString("token", token).apply()
    }

    override fun onMessageReceived(m: RemoteMessage) {
        if (m.data["type"] != "probe") return
        val seq = m.data["seq"]?.toLongOrNull() ?: 0L
        Probes.onReceive(this, m.data["id"].orEmpty(), seq, m.sentTime)
    }
}
