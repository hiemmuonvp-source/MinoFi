package com.minofi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Probe(val ts: Long, val latency: Long, val seq: Long, val id: String, val lost: Boolean)
data class Stats(val n: Int, val avg: Long, val p95: Long, val max: Long, val lost: Int, val last: List<Probe>)

object Probes {
    private fun prefs(c: Context) = c.getSharedPreferences("minofi", Context.MODE_PRIVATE)

    @Synchronized fun load(c: Context): List<Probe> {
        val a = JSONArray(prefs(c).getString("probes", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Probe(o.getLong("ts"), o.getLong("l"), o.getLong("s"), o.optString("id"), o.optBoolean("x"))
        }
    }

    @Synchronized private fun save(c: Context, list: List<Probe>) {
        val a = JSONArray()
        list.takeLast(200).forEach {
            a.put(JSONObject().put("ts", it.ts).put("l", it.latency).put("s", it.seq).put("id", it.id).put("x", it.lost))
        }
        prefs(c).edit().putString("probes", a.toString()).apply()
    }

    @Synchronized fun add(c: Context, p: Probe) = save(c, load(c) + p)
    fun clear(c: Context) = prefs(c).edit().remove("probes").remove("lastSeq").apply()

    @Synchronized fun onReceive(c: Context, id: String, seq: Long, sentTime: Long) {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Probe>()
        if (seq > 0) {
            val last = prefs(c).getLong("lastSeq", 0)
            if (seq > last + 1 && last > 0) {
                for (s in (last + 1) until minOf(seq, last + 51)) out += Probe(now, -1, s, "", true)
            }
            prefs(c).edit().putLong("lastSeq", seq).apply()
        }
        out += Probe(now, (now - sentTime).coerceAtLeast(0), seq, id, false)
        save(c, load(c) + out)
    }

    fun stats(c: Context): Stats {
        val all = load(c)
        val ok = all.filter { !it.lost }.map { it.latency }.sorted()
        return Stats(
            n = ok.size,
            avg = if (ok.isEmpty()) 0 else ok.average().toLong(),
            p95 = if (ok.isEmpty()) 0 else ok[((ok.size - 1) * 0.95).toInt()],
            max = ok.lastOrNull() ?: 0,
            lost = all.count { it.lost },
            last = all.takeLast(10)
        )
    }

    fun verdict(s: Stats): String {
        if (s.n == 0 && s.lost == 0) return ""
        val total = s.n + s.lost
        val lossPct = if (total == 0) 0 else s.lost * 100 / total
        return when {
            lossPct >= 10 || s.p95 > 60_000 -> "TỆ: GMS bị giết/ngủ sâu. Làm lại Autostart + Pin “Không hạn chế” cho GMS & Gmail."
            s.p95 > 8_000 -> "TRUNG BÌNH: có trễ khi máy ngủ. Kiểm tra Doze và Data Saver."
            else -> "TỐT: FCM đến nhanh và ổn định."
        }
    }
}
