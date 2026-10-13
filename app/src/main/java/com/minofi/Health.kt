package com.minofi

object Health {
    fun score(st: Stats, checks: List<Check>): Int {
        val gms = checks.firstOrNull { it.title == "Google Play services" }?.ok
        val reach = checks.firstOrNull { it.title.startsWith("Kết nối FCM") }?.ok
        if (gms == false) return 0
        val total = st.n + st.lost
        var s = if (total == 0) 70 else {
            val lat = when {
                st.n == 0 -> 0
                st.p95 <= 3_000 -> 100
                st.p95 <= 8_000 -> 80
                st.p95 <= 30_000 -> 55
                st.p95 <= 60_000 -> 35
                else -> 15
            }
            (lat - (st.lost * 100 / total) * 2).coerceAtLeast(0)
        }
        if (reach == false) s = minOf(s, 40)
        val bad = checks.count {
            it.ok == false && it.title != "Thiết bị" && it.title != "Google Play services" && !it.title.startsWith("Kết nối FCM")
        }
        s -= minOf(bad * 4, 20)
        return s.coerceIn(0, 100)
    }
}
