package org.autojs.autojs.KeepAliveService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.autojs.autoxjs.R;

import androidx.core.app.NotificationCompat;

public class KeepAliveForegroundService extends Service {
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "keep_alive_channel";
    private static final String ACTION_CLOSE = "org.autojs.autoxjs.ACTION_CLOSE_NOTIFICATION";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "后台保活服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        Intent closeIntent = new Intent(ACTION_CLOSE);
        closeIntent.setPackage(getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent closePending = PendingIntent.getBroadcast(this, 0, closeIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("保活服务运行中")
                .setContentText("点击此处关闭通知")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(closePending)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class NotificationCloseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CLOSE.equals(intent.getAction())) {
                KeepAliveForegroundService service = (KeepAliveForegroundService) context;

               /* // 1. 先停止前台服务
                service.stopForeground(true);

                // 2. 再停止服务自身
                service.stopSelf();

                // 3. 最后移除通知
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(NOTIFICATION_ID);*/
                // 停止服务
                Intent serviceIntent = new Intent(context, KeepAliveForegroundService.class);
                context.stopService(serviceIntent);

                // 关闭通知
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.cancel(NOTIFICATION_ID);
                }
            }
        }
    }

}