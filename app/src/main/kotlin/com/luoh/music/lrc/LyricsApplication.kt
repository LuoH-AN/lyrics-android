package com.luoh.music.lrc

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper

class LyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageAnalytics.initialize(this)
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> UsageAnalytics.setInteractive(true)
                    Intent.ACTION_SCREEN_OFF -> UsageAnalytics.setInteractive(false)
                }
            }
        }
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // These protected broadcasts can only be sent by the system; no app permission is needed.
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, screenFilter)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            private val handler = Handler(Looper.getMainLooper())
            private val stopped = Runnable { UsageAnalytics.setActive(UsageAnalytics.Surface.APP, false) }

            override fun onActivityStarted(activity: Activity) {
                started++
                handler.removeCallbacks(stopped)
                UsageAnalytics.setActive(UsageAnalytics.Surface.APP, true)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                // Rotation and moving between the app's activities are still the same foreground visit.
                if (started == 0) handler.postDelayed(stopped, 700L)
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
