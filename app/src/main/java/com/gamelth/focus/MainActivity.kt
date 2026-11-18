package com.gamelth.focus

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val cBg = Color.parseColor("#141110")
    private val cCard = Color.parseColor("#201B19")
    private val cText = Color.parseColor("#F3ECE6")
    private val cMuted = Color.parseColor("#A69C94")
    private val cAccent = Color.parseColor("#E8A55D")

    private lateinit var root: LinearLayout
    private var showAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        val sv = ScrollView(this)
        sv.setBackgroundColor(cBg)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(40), dp(20), dp(40))
        sv.addView(root)
        setContentView(sv)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- small UI helpers ----------
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int = 16): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun btn(s: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.setTextColor(cBg)
        b.background = round(cAccent, 14)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun add(parent: LinearLayout, v: View, top: Int = 12) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(v, lp)
    }

    // ---------- screen ----------
    private fun render() {
        root.removeAllViews()
        add(root, tv("Gamelth", 32f, cText, true), 0)
        add(root, tv("screen time, in balance", 14f, cMuted), 2)

        val hasUsage = UsageRepo.hasUsageAccess(this)
        val hasOverlay = Settings.canDrawOverlays(this)

        if (!hasUsage || !hasOverlay) {
            renderSetup(hasUsage, hasOverlay)
            return
        }

        val usage = UsageRepo.todayUsage(this)
        val apps = UsageRepo.installedApps(this)
        val total = apps.sumOf { usage[it.pkg] ?: 0L }

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.background = round(cCard, 20)
        hero.setPadding(dp(18), dp(16), dp(18), dp(16))
        hero.addView(tv("Today", 13f, cMuted))
        hero.addView(tv(fmt(total), 40f, cAccent, true))
        add(root, hero, 20)

        val on = Prefs.protectionOn(this)
        if (on) startForegroundService(Intent(this, MonitorService::class.java))
        add(root, btn(if (on) "Protection is ON · tap to pause" else "Start protection") {
            if (Prefs.protectionOn(this)) {
                Prefs.setProtection(this, false)
                stopService(Intent(this, MonitorService::class.java))
            } else {
                Prefs.setProtection(this, true)
                startForegroundService(Intent(this, MonitorService::class.java))
            }
            render()
        }, 14)

        add(root, tv("Your apps", 18f, cText, true), 24)
        add(root, tv("Tap an app to set a daily limit.", 12f, cMuted), 2)

        val sorted = apps
            .filter { showAll || (usage[it.pkg] ?: 0L) > 0L || Prefs.limitMin(this, it.pkg) > 0 }
            .sortedWith(compareByDescending<AppInfo> { usage[it.pkg] ?: 0L }.thenBy { it.label.lowercase() })

        if (sorted.isEmpty()) {
            add(root, tv("No usage yet today. Apps you open will show up here.", 14f, cMuted), 16)
        }
        for (a in sorted) {
            add(root, appRow(a, usage[a.pkg] ?: 0L), 10)
        }
        add(root, btn(if (showAll) "Show only used apps" else "Show all apps") {
            showAll = !showAll
            render()
        }, 20)
    }

    private fun renderSetup(hasUsage: Boolean, hasOverlay: Boolean) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = round(cCard, 20)
        card.setPadding(dp(18), dp(16), dp(18), dp(18))
        card.addView(tv("Two quick permissions", 18f, cText, true))
        add(card, tv(
            "Gamelth reads real usage from your phone and shows a block screen when a timer runs out. " +
                "Both are switched on in Android settings, and everything stays on your device.",
            14f, cMuted
        ), 8)
        if (!hasUsage) {
            add(card, btn("1 · Allow usage access") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }, 16)
        }
        if (!hasOverlay) {
            add(card, btn("2 · Allow display over other apps") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }, 12)
        }
        add(root, card, 24)
    }

    private fun appRow(a: AppInfo, used: Long): View {
        val limit = Prefs.limitMin(this, a.pkg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = round(cCard, 16)
        row.setPadding(dp(14), dp(12), dp(14), dp(12))
        row.setOnClickListener { showLimitDialog(a) }

        val icon = ImageView(this)
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(a.pkg))
        } catch (e: Exception) {
            // no icon available
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(a.label, 16f, cText, true))
        col.addView(tv("${a.category} · ${fmt(used)} today", 12f, cMuted))
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.leftMargin = dp(12)
        row.addView(col, colLp)

        val frozen = limit > 0 && used >= limit * 60_000L
        val label = when {
            limit <= 0 -> "Set limit"
            frozen -> "🔒 ${fmtMin(limit)}"
            else -> "⏱ ${fmtMin(limit)}"
        }
        row.addView(tv(label, 13f, if (limit > 0) cAccent else cMuted, limit > 0))
        return row
    }

    private fun showLimitDialog(a: AppInfo) {
        val names = arrayOf("15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours", "2 hours", "Remove limit")
        val mins = intArrayOf(15, 30, 45, 60, 90, 120, 0)
        AlertDialog.Builder(this)
            .setTitle("Daily limit for ${a.label}")
            .setItems(names) { _, i ->
                Prefs.setLimit(this, a.pkg, mins[i])
                if (mins[i] > 0) suggestRelated(a, mins[i]) else render()
            }
            .show()
    }

    private fun suggestRelated(a: AppInfo, minutes: Int) {
        if (a.category == "Other") {
            render()
            return
        }
        val usage = UsageRepo.todayUsage(this)
        val related = UsageRepo.installedApps(this)
            .filter { it.category == a.category && it.pkg != a.pkg && Prefs.limitMin(this, it.pkg) == 0 }
            .sortedByDescending { usage[it.pkg] ?: 0L }
            .take(5)
        if (related.isEmpty()) {
            render()
            return
        }
        val names = related.map { "${it.label} (${fmt(usage[it.pkg] ?: 0L)} today)" }.toTypedArray()
        val checked = BooleanArray(related.size) { true }
        AlertDialog.Builder(this)
            .setTitle("Also freeze similar ${a.category} apps?")
            .setMultiChoiceItems(names, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Apply ${fmtMin(minutes)} limit") { _, _ ->
                for (i in related.indices) {
                    if (checked[i]) Prefs.setLimit(this, related[i].pkg, minutes)
                }
                render()
            }
            .setNegativeButton("No thanks") { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }
}
