package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Keeps track of how long each download took, without touching the Room schema.
 * Values live in their own SharedPreferences file so nothing else is affected.
 *
 * Keys:
 *  s<downloadId> -> start timestamp (millis)
 *  e<downloadId> -> elapsed duration (millis)
 */
object DownloadTimeTracker {

    private const val FILE = "zetta_download_times"
    private const val MAX_ENTRIES = 1000

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun markStart(context: Context, downloadId: Long) {
        runCatching {
            prefs(context).edit().putLong("s$downloadId", System.currentTimeMillis()).apply()
        }
    }

    fun markFinished(context: Context, downloadId: Long) {
        runCatching {
            val p = prefs(context)
            val start = p.getLong("s$downloadId", 0L)
            if (start <= 0L) return
            val elapsed = System.currentTimeMillis() - start
            p.edit()
                .remove("s$downloadId")
                .putLong("e$downloadId", if (elapsed < 0) 0 else elapsed)
                .apply()
            trim(p)
        }
    }

    fun getElapsed(context: Context, downloadId: Long): Long {
        return runCatching { prefs(context).getLong("e$downloadId", 0L) }.getOrDefault(0L)
    }

    fun getAllElapsed(context: Context): Map<Long, Long> {
        return runCatching {
            prefs(context).all
                .filter { it.key.startsWith("e") && it.value is Long }
                .mapNotNull { entry ->
                    val id = entry.key.removePrefix("e").toLongOrNull() ?: return@mapNotNull null
                    id to (entry.value as Long)
                }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** Stops the file from growing forever. */
    private fun trim(p: SharedPreferences) {
        runCatching {
            val all = p.all
            if (all.size <= MAX_ENTRIES) return
            val editor = p.edit()
            all.keys.take(all.size - MAX_ENTRIES).forEach { editor.remove(it) }
            editor.apply()
        }
    }
}
