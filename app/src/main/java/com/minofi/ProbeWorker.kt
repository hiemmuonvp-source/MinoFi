package com.minofi

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProbeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        val p = c.getSharedPreferences("minofi", Context.MODE_PRIVATE)
        val server = p.getString("server", "")?.trim().orEmpty()
        val key = p.getString("key", "").orEmpty()
        val token = p.getString("token", "").orEmpty()
        if (server.isEmpty() || token.isEmpty()) return Result.failure()

        val id = UUID.randomUUID().toString().take(8)
        val sent = withContext(Dispatchers.IO) {
            try {
                val url = URL("${server.trimEnd('/')}/ping?id=$id&key=${enc(key)}&token=${enc(token)}")
                val con = url.openConnection() as HttpURLConnection
                con.connectTimeout = 10_000; con.readTimeout = 15_000
                val ok = con.responseCode in 200..299
                con.disconnect(); ok
            } catch (e: Exception) { false }
        }
        if (!sent) { Probes.add(c, Probe(System.currentTimeMillis(), -1, 0, id, true)); return Result.success() }

        repeat(45) {
            if (Probes.load(c).any { it.id == id && !it.lost }) return Result.success()
            delay(2_000)
        }
        Probes.add(c, Probe(System.currentTimeMillis(), -1, 0, id, true))
        return Result.success()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

object ProbeScheduler {
    private const val NAME = "minofi_probe"

    fun enable(c: Context, minutes: Long = 15) {
        val req = PeriodicWorkRequestBuilder<ProbeWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
    fun disable(c: Context) { WorkManager.getInstance(c).cancelUniqueWork(NAME) }
    fun runNow(c: Context) { WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<ProbeWorker>().build()) }
}
