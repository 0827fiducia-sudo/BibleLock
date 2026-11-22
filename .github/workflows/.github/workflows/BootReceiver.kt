package com.biblelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs(c).enabled) {
            try { c.startForegroundService(Intent(c, MonitorService::class.java)) } catch (_: Exception) {}
        }
    }
}
