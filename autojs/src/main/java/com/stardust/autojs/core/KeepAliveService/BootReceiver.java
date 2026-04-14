package com.stardust.autojs.core.KeepAliveService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// 类名：BootReceiver（不变）
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 监听手机开机完成广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 开机后自动启动当前APP
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }
        }
    }
}