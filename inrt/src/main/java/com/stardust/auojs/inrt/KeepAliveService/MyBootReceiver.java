package com.stardust.auojs.inrt.KeepAliveService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.stardust.auojs.inrt.Pref;
import com.stardust.auojs.inrt.SplashActivity;

public class MyBootReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 空值安全检查
        if (context == null || intent == null || intent.getAction() == null) {
            return;
        }

        // 1. 先读取本地配置：是否允许开机自启
        if (!Pref.isBootAutoStartEnabled()) {
            return; // 不允许，直接退出，不处理任何广播
        }

        // 2. 再判断是否是开机广播
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }

        // 3. 允许 + 开机 → 启动APP
        try {
            Intent i = new Intent(context, SplashActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            Log.d(TAG, "开机自启成功");
        } catch (Exception e) {
            Log.e(TAG, "开机自启失败", e);
        }
    }
}