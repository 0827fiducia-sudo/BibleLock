package com.biblelock

import android.content.Context
import java.time.LocalDateTime

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }
    var blocked: Set<String>
        get() = p.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("blocked", v).apply() }
    var reading: Set<String>
        get() = p.getStringSet("reading", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("reading", v).apply() }
    var minutes: Int
        get() = p.getInt("minutes", 10)
        set(v) { p.edit().putInt("minutes", v).apply() }
    var requireAll: Boolean
        get() = p.getBoolean("requireAll", true)
        set(v) { p.edit().putBoolean("requireAll", v).apply() }
    var startMin: Int
        get() = p.getInt("startMin", 0)
        set(v) { p.edit().putInt("startMin", v).apply() }
    var endMin: Int
        get() = p.getInt("endMin", 1440)
        set(v) { p.edit().putInt("endMin", v).apply() }
    var days: Int // bit0 = Monday ... bit6 = Sunday
        get() = p.getInt("days", 127)
        set(v) { p.edit().putInt("days", v).apply() }
    var resetMin: Int
        get() = p.getInt("resetMin", 0)
        set(v) { p.edit().putInt("resetMin", v).apply() }
    var emergencyCount: Int
        get() = p.getInt("emCount", 1)
        set(v) { p.edit().putInt("emCount", v).apply() }
    var emergencyMinutes: Int
        get() = p.getInt("emMinutes", 5)
        set(v) { p.edit().putInt("emMinutes", v).apply() }
    var pin: String
        get() = p.getString("pin", "") ?: ""
        set(v) { p.edit().putString("pin", v).apply() }
    var emUsed: Int
        get() = p.getInt("emUsed", 0)
        set(v) { p.edit().putInt("emUsed", v).apply() }

    fun emergencyActive() = System.currentTimeMillis() < p.getLong("emUntil", 0)

    fun useEmergency(): Boolean {
        if (emUsed >= emergencyCount) return false
        emUsed += 1
        p.edit().putLong("emUntil", System.currentTimeMillis() + emergencyMinutes * 60_000L).apply()
        return true
    }

    /** Clears daily progress when the reset time passes. */
    fun rollover() {
        val key = LocalDateTime.now().minusMinutes(resetMin.toLong()).toLocalDate().toString()
        if (p.getString("dayKey", "") != key) {
            val e = p.edit()
            p.all.keys.filter { it.startsWith("prog_") }.forEach { e.remove(it) }
            e.putString("dayKey", key).putInt("emUsed", 0).putLong("emUntil", 0).apply()
        }
    }

    fun progress(pkg: String) = p.getLong("prog_$pkg", 0)
    fun addProgress(pkg: String, sec: Long) { p.edit().putLong("prog_$pkg", progress(pkg) + sec).apply() }

    fun goalMet(): Boolean {
        if (reading.isEmpty()) return true // never lock you out if no reading app is set
        val need = minutes * 60L
        return if (requireAll) reading.all { progress(it) >= need } else reading.any { progress(it) >= need }
    }

    fun blockingActive(): Boolean {
        if (!enabled) return false
        val now = LocalDateTime.now()
        val m = now.hour * 60 + now.minute
        val overnight = startMin > endMin
        val inWindow = if (overnight) (m >= startMin || m < endMin) else (m in startMin until endMin)
        if (!inWindow) return false
        val d = if (overnight && m < endMin) now.minusDays(1) else now
        return ((days shr (d.dayOfWeek.value - 1)) and 1) == 1
    }
}
