package org.autojs.autojs.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.Pref
import org.autojs.autojs.ui.splash.SplashActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (
            action == Intent.ACTION_BOOT_COMPLETED
            || action == "android.intent.action.QUICKBOOT_POWERON"
            || action == Intent.ACTION_USER_PRESENT
            || action == "android.intent.action.MEDIA_MOUNTED"
            || action == Intent.ACTION_USER_UNLOCKED
        ) {
            if (Pref.isBootAutoStartEnabled()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val i = Intent(context, SplashActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(i)
                }, 5000)
            }
        }
    }
}