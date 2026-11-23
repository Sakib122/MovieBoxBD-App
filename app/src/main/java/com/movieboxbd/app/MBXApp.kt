package com.movieboxbd.app

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 💥 ক্র্যাশ-লগার (v3.8): অ্যাপ কোথায় ভেঙে পড়ল সেটা ফাইলে জমা রাখে
 *  (files/crash_log.txt) — "ক্র্যাশ হচ্ছে" বললে এই ফাইলই আসল কারণ দেখিয়ে দেয়।
 *  ক্র্যাশের পর আগের মতোই সিস্টেম স্বাভাবিক কাজ করে। */
class MBXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(filesDir, "crash_log.txt").appendText(
                    "\n═══ $ts (thread: ${t.name}) ═══\n" + Log.getStackTraceString(e) + "\n"
                )
            } catch (_: Exception) {
            }
            def?.uncaughtException(t, e)
        }
    }
}
