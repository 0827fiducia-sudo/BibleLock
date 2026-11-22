package com.biblelock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private var unlocked = false
    private val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 60) }
        setContentView(ScrollView(this).apply { addView(root) })
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (prefs.pin.isEmpty()) render() else askPin()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) { render(); if (prefs.enabled) syncService() }
    }

    private fun askPin() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("Enter PIN").setView(et).setCancelable(false)
            .setPositiveButton("OK") { _, _ -> if (et.text.toString() == prefs.pin) render() else finish() }
            .setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

    // ---------- helpers ----------
    private fun launchables(): List<Pair<String, String>> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    private fun label(pkg: String) = launchables().firstOrNull { it.first == pkg }?.second ?: pkg
    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun btn(t: String, onClick: () -> Unit) =
        root.addView(Button(this).apply { text = t; isAllCaps = false; setOnClickListener { onClick() } })

    private fun head(t: String) =
        root.addView(TextView(this).apply { text = t; textSize = 18f; setPadding(0, 40, 0, 10) })

    private fun pick(title: String, cur: Set<String>, save: (Set<String>) -> Unit) {
        val apps = launchables()
        val sel = apps.map { it.first in cur }.toBooleanArray()
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), sel) { _, w, c -> sel[w] = c }
            .setPositiveButton("Save") { _, _ ->
                save(apps.indices.filter { sel[it] }.map { apps[it].first }.toSet()); render()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun number(title: String, cur: Int, save: (Int) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(cur.toString()) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("Save") { _, _ ->
                et.text.toString().toIntOrNull()?.let { save(it.coerceAtLeast(0)); render() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun time(cur: Int, save: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> save(h * 60 + m); render() }, cur / 60 % 24, cur % 60, true).show()
    }

    private fun syncService() {
        val i = Intent(this, MonitorService::class.java)
        if (prefs.enabled) startForegroundService(i) else stopService(i)
    }

    private fun hasUsage(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    // ---------- UI ----------
    private fun render() {
        unlocked = true
        root.removeAllViews()
        if (prefs.reading.isEmpty()) {
            val found = launchables().filter {
                it.second.lowercase().contains("glorify") || it.second.lowercase() == "bible" ||
                    it.first.startsWith("com.sirma.mobile.bible")
            }.map { it.first }.toSet()
            if (found.isNotEmpty()) prefs.reading = found
        }

        root.addView(TextView(this).apply { text = "Bible Lock"; textSize = 28f })
        root.addView(Switch(this).apply {
            text = "Blocking enabled"; textSize = 18f; isChecked = prefs.enabled
            setOnCheckedChangeListener { _, c -> prefs.enabled = c; syncService() }
        })

        head("Permissions")
        btn(if (hasUsage()) "✔ Usage access" else "Grant usage access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btn(if (Settings.canDrawOverlays(this)) "✔ Display over other apps" else "Grant display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val ignoring = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        btn(if (ignoring) "✔ Battery optimization off" else "Disable battery optimization") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        head("Apps")
        btn("Apps to block (${prefs.blocked.size})") { pick("Apps to block", prefs.blocked) { prefs.blocked = it } }
        btn("Reading apps: YouVersion, Glorify (${prefs.reading.size})") {
            pick("Reading apps", prefs.reading) { prefs.reading = it }
        }

        head("Unlock goal")
        btn("Minutes per reading app: ${prefs.minutes}") { number("Minutes", prefs.minutes) { prefs.minutes = it } }
        root.addView(Switch(this).apply {
            text = "Require ALL reading apps (off = any one)"; isChecked = prefs.requireAll
            setOnCheckedChangeListener { _, c -> prefs.requireAll = c }
        })

        head("Schedule")
        btn("Start blocking: ${fmt(prefs.startMin)}") { time(prefs.startMin) { prefs.startMin = it } }
        btn("Stop blocking: ${if (prefs.endMin >= 1440) "24:00" else fmt(prefs.endMin)}") {
            time(prefs.endMin % 1440) { prefs.endMin = it }
        }
        btn("Days: " + dayNames.filterIndexed { i, _ -> ((prefs.days shr i) and 1) == 1 }.joinToString(", ")) {
            val sel = BooleanArray(7) { ((prefs.days shr it) and 1) == 1 }
            AlertDialog.Builder(this).setTitle("Blocking days")
                .setMultiChoiceItems(dayNames, sel) { _, w, c -> sel[w] = c }
                .setPositiveButton("Save") { _, _ ->
                    prefs.days = sel.indices.fold(0) { a, i -> if (sel[i]) a or (1 shl i) else a }; render()
                }.setNegativeButton("Cancel", null).show()
        }
        btn("Daily progress resets at: ${fmt(prefs.resetMin)}") { time(prefs.resetMin) { prefs.resetMin = it } }

        head("Emergency unlock")
        btn("Allowed per day: ${prefs.emergencyCount}") { number("Per day", prefs.emergencyCount) { prefs.emergencyCount = it } }
        btn("Duration (min): ${prefs.emergencyMinutes}") { number("Minutes", prefs.emergencyMinutes) { prefs.emergencyMinutes = it } }

        head("Settings PIN")
        btn(if (prefs.pin.isEmpty()) "Set PIN" else "Change / clear PIN") {
            val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "Leave empty to clear" }
            AlertDialog.Builder(this).setTitle("PIN for this screen").setView(et)
                .setPositiveButton("Save") { _, _ -> prefs.pin = et.text.toString(); render() }
                .setNegativeButton("Cancel", null).show()
        }

        head("Today's progress")
        val need = prefs.minutes * 60L
        root.addView(TextView(this).apply {
            text = prefs.reading.joinToString("\n") {
                "${label(it)}: ${minOf(prefs.progress(it), need) / 60} / ${prefs.minutes} min"
            }.ifEmpty { "No reading apps selected" } + if (prefs.goalMet()) "\n✔ Goal met" else ""
        })
    }
}
