package com.gamelth.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppInfo(val pkg: String, val label: String, val category: String)

fun fmt(ms: Long): String {
    val totalMin = ms / 60_000
    if (ms > 0 && totalMin == 0L) return "<1m"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun fmtMin(m: Int): String = when {
    m % 60 == 0 -> "${m / 60}h"
    m > 60 -> "${m / 60}h ${m % 60}m"
    else -> "${m}m"
}

object UsageRepo {
    const val RESUMED = 1
    const val PAUSED = 2

    @Suppress("DEPRECATION")
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Real foreground time per app since midnight, in milliseconds. */
    fun todayUsage(c: Context, only: String? = null): Map<String, Long> {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfDay(), now)
        val e = UsageEvents.Event()
        val start = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val p = e.packageName
            if (only != null && p != only) continue
            if (e.eventType == RESUMED) {
                start[p] = e.timeStamp
            } else if (e.eventType == PAUSED) {
                val s = start.remove(p)
                if (s != null) total[p] = (total[p] ?: 0L) + (e.timeStamp - s)
            }
        }
        for ((p, s) in start) total[p] = (total[p] ?: 0L) + (now - s)
        return total
    }

    /** Which app is on screen right now (best effort). */
    fun lastForeground(c: Context): String? {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 2 * 60 * 60 * 1000L, now)
        val e = UsageEvents.Event()
        var cur: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == RESUMED) cur = e.packageName
            else if (e.eventType == PAUSED && e.packageName == cur) cur = null
        }
        return cur
    }

    /** Apps with a launcher icon, i.e. the ones a person actually opens. */
    fun installedApps(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != c.packageName }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), categoryOf(it)) }
    }

    private fun categoryOf(ai: ApplicationInfo): String {
        val fromSystem = when (ai.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_AUDIO -> "Music"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            else -> null
        }
        if (fromSystem != null) return fromSystem
        val s = ai.packageName.lowercase()
        val social = listOf("instagram", "facebook", "snapchat", "twitter", "tiktok", "reddit",
            "pinterest", "linkedin", "telegram", "whatsapp", "sharechat", "threads")
        val video = listOf("youtube", "netflix", "hotstar", "primevideo", "avod", "mxtech", "sonyliv", "zee5")
        val music = listOf("spotify", "gaana", "jiosaavn", "wynk")
        return when {
            social.any { s.contains(it) } -> "Social"
            video.any { s.contains(it) } -> "Video"
            music.any { s.contains(it) } -> "Music"
            else -> "Other"
        }
    }
}
