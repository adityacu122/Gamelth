package com.gamelth.focus

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gamelth", Context.MODE_PRIVATE)

    fun limitMin(c: Context, pkg: String): Int = sp(c).getInt("limit_$pkg", 0)

    fun setLimit(c: Context, pkg: String, min: Int) {
        val e = sp(c).edit()
        if (min <= 0) e.remove("limit_$pkg") else e.putInt("limit_$pkg", min)
        e.apply()
    }

    fun snoozeUntil(c: Context, pkg: String): Long = sp(c).getLong("snooze_$pkg", 0L)

    fun snooze(c: Context, pkg: String, ms: Long) {
        sp(c).edit().putLong("snooze_$pkg", System.currentTimeMillis() + ms).apply()
    }

    fun protectionOn(c: Context): Boolean = sp(c).getBoolean("on", false)

    fun setProtection(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("on", on).apply()
    }
}
