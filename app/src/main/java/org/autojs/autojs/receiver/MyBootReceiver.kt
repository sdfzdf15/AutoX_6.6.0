package org.autojs.autojs.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.Pref
import org.autojs.autojs.ui.splash.SplashActivity

class MyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action

       // 只保留：标准开机 + 厂商快速开机
        if (action != Intent.ACTION_BOOT_COMPLETED
            && action != "android.intent.action.QUICKBOOT_POWERON"
            && action != "com.htc.intent.action.QUICKBOOT_POWERON" // 补HTC全
        ) {
            return // 其他一律不处理
        }
        // 必须延迟！让 SP 加载完成！SharedPreferences 加载是异步的，不能保证在 BroadcastReceiver 里可用
        Handler(Looper.getMainLooper()).postDelayed({
            val shouldBoot = Pref.isBootAutoStartEnabled()
            if (shouldBoot) {
                val i = Intent(context, SplashActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }, 5000) // 延迟5秒，100%可靠
    }
}