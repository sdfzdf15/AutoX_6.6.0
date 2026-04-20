package org.autojs.autojs.KeepAliveService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;

public class NotificationCloseReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (KeepAliveForegroundService.ACTION_CLOSE.equals(intent.getAction())) {
            // 停止服务
            Intent serviceIntent = new Intent(context, KeepAliveForegroundService.class);
            context.stopService(serviceIntent);

            // 关闭通知
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(KeepAliveForegroundService.NOTIFICATION_ID);
            }
        }
    }
}