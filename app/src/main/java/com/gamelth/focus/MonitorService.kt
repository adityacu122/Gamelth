package com.gamelth.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastPoll = 0L
    private var overlay: View? = null
    private var overlayPkg: String? = null
    private var checkedPkg: String? = null
    private var checkedAt = 0L
    private var cachedUsed = 0L

    private val tick = object : Runnable {
        override fun run() {
            try {
                poll()
            } catch (e: Exception) {
                // keep running even if one poll fails
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (lastPoll == 0L) {
            current = UsageRepo.lastForeground(this)
            lastPoll = System.currentTimeMillis()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeOverlay()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("gamelth", "Gamelth protection", NotificationManager.IMPORTANCE_LOW)
        )
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "gamelth")
            .setContentTitle("Gamelth is guarding your time")
            .setContentText("Watching your app limits")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun poll() {
        val now = System.currentTimeMillis()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(lastPoll - 1000, now)
        lastPoll = now
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageRepo.RESUMED) {
                current = e.packageName
            } else if (e.eventType == UsageRepo.PAUSED && e.packageName == current) {
                current = null
            }
        }

        val pkg = current
        if (overlayPkg != null && pkg != overlayPkg) removeOverlay()
        if (pkg == null || pkg == packageName || overlay != null) return

        val limit = Prefs.limitMin(this, pkg)
        if (limit <= 0 || now < Prefs.snoozeUntil(this, pkg)) return

        if (pkg != checkedPkg || now - checkedAt >= 5000) {
            cachedUsed = UsageRepo.todayUsage(this, pkg)[pkg] ?: 0L
            checkedPkg = pkg
            checkedAt = now
        }
        if (cachedUsed >= limit * 60_000L) showOverlay(pkg, cachedUsed, limit)
    }

    private fun showOverlay(pkg: String, used: Long, limit: Int) {
        if (!Settings.canDrawOverlays(this)) return
        val d = resources.displayMetrics.density
        val pad = (32 * d).toInt()
        val gap = (16 * d).toInt()
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad, pad, pad, pad)
        root.setBackgroundColor(Color.parseColor("#F5141110"))

        val title = TextView(this)
        title.text = "Time's up"
        title.textSize = 34f
        title.gravity = Gravity.CENTER
        title.setTextColor(Color.parseColor("#E8A55D"))

        val body = TextView(this)
        body.text = "$label is frozen for today.\nYou've used ${fmt(used)} of your ${fmtMin(limit)} limit."
        body.textSize = 16f
        body.gravity = Gravity.CENTER
        body.setTextColor(Color.parseColor("#F3ECE6"))

        val home = Button(this)
        home.text = "Go home"
        home.isAllCaps = false
        home.setOnClickListener {
            goHome()
            removeOverlay()
        }

        val more = Button(this)
        more.text = "I need 5 more minutes"
        more.isAllCaps = false
        more.setOnClickListener {
            Prefs.snooze(this, pkg, 5 * 60_000L)
            removeOverlay()
        }

        fun lp(): LinearLayout.LayoutParams {
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = gap
            return p
        }
        root.addView(title, lp())
        root.addView(body, lp())
        root.addView(home, lp())
        root.addView(more, lp())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(root, params)
        overlay = root
        overlayPkg = pkg
    }

    private fun removeOverlay() {
        val v = overlay
        if (v != null) {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (e: Exception) {
                // already removed
            }
        }
        overlay = null
        overlayPkg = null
    }

    private fun goHome() {
        current = null
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
