package com.biblelock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.widget.*

class LockActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var emBtn: Button
    private val h = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            if (prefs.goalMet() || prefs.emergencyActive() || !prefs.blockingActive()) { finish(); return }
            val need = prefs.minutes * 60L
            status.text = prefs.reading.joinToString("\n") {
                val s = prefs.progress(it)
                "${label(it)}: ${minOf(s, need) / 60}:${"%02d".format(minOf(s, need) % 60)} / ${prefs.minutes}:00"
            }
            val left = prefs.emergencyCount - prefs.emUsed
            emBtn.visibility = if (left > 0) android.view.View.VISIBLE else android.view.View.GONE
            emBtn.text = "Emergency unlock (${prefs.emergencyMinutes} min, $left left today)"
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60); setBackgroundColor(Color.parseColor("#1B2A41"))
        }
        fun tv(t: String, size: Float) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20)
        }
        box.addView(tv("🔒 Time with God first", 26f))
        box.addView(tv(if (prefs.requireAll) "Spend the time in each app below to unlock your apps."
                       else "Spend the time in any app below to unlock your apps.", 16f))
        status = tv("", 18f); box.addView(status)
        for (pkg in prefs.reading) {
            box.addView(Button(this).apply {
                text = "Open ${label(pkg)}"
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                }
            })
        }
        emBtn = Button(this).apply {
            setOnClickListener { if (prefs.useEmergency()) finish() }
        }
        box.addView(emBtn)
        box.addView(Button(this).apply { text = "Go home"; setOnClickListener { home() } })
        setContentView(box)
    }

    private fun label(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun home() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { home() }
}
