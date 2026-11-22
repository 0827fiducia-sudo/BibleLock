package com.biblelock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var cur: String? = null
    private var lastQuery = 0L

    private val tick = object : Runnable {
        override fun run() {
            try { check() } catch (_: Exception) {}
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        prefs = Prefs(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bl", "Bible Lock", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "bl")
            .setContentTitle("Bible Lock is running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        lastQuery = System.currentTimeMillis() - 3_600_000
        h.post(tick)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(tick) }

    private fun check() {
        prefs.rollover()
        val now = System.currentTimeMillis()
        val um = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = um.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) cur = e.packageName
            else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == cur) cur = null
        }
        lastQuery = now - 2000

        val pkg = cur ?: return
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return

        if (pkg in prefs.reading) { prefs.addProgress(pkg, 1); return }

        if (pkg in prefs.blocked && prefs.blockingActive() && !prefs.goalMet() && !prefs.emergencyActive()) {
            startActivity(Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
}
