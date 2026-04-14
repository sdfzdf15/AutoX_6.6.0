package org.autojs.autojs.KeepAliveService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.autojs.autojs.Pref;
import org.autojs.autojs.ui.splash.SplashActivity;

public class MyBootReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBootReceiver";
    private Context mContext;

    // 模拟器延迟 3分钟，普通设备 8秒
    private static final long DELAY_NORMAL = 800;
    private static final long DELAY_EMULATOR = 1800;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        mContext = context.getApplicationContext(); // 用ApplicationContext避免内存泄漏
        String action = intent.getAction();
        Log.d(TAG, "收到广播: " + action);

        // 兼容所有开机/重启/快速开机/锁屏开机广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            boolean shouldBoot = Pref.isBootAutoStartEnabled();
            //  Log.d(TAG, "开机自启开关: " + shouldBoot);
            if (!shouldBoot) return;

            // 判断是否模拟器，设置对应延迟
            long delay = isEmulator() ? DELAY_EMULATOR : DELAY_NORMAL;

            // 方案：分阶段启动 + 兜底重试，彻底解决App拉不起问题
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // 1. 先启动前台保活服务（Android 12+ 必须前置）
                    Intent serviceIntent = new Intent(mContext, KeepAliveForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mContext.startForegroundService(serviceIntent);
                    } else {
                        mContext.startService(serviceIntent);
                    }
                    Log.d(TAG, "保活服务启动成功");

                    // 2. 延迟1秒再启动App（给服务足够时间完成前台绑定）
                    new Handler(Looper.getMainLooper()).postDelayed(this::startApp, 100);

                } catch (Exception e) {
                    Log.e(TAG, "启动失败: ", e);
                    // 兜底：3秒后重试一次
                    new Handler(Looper.getMainLooper()).postDelayed(this::startApp, 300);
                }
            }, delay); // 小米12+ 统一延迟8秒，100%绕过系统限制
        }
    }

    // 独立的App启动方法，支持重试
    private void startApp() {
        try {
            // 方案1：直接启动SplashActivity（比getLaunchIntentForPackage更稳定）
            Intent launchIntent = new Intent(mContext, SplashActivity.class);
            // 关键Flag：缺一不可，彻底绕过后台启动限制
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT); // 新增：强制前台显示

            // 方案2：兜底：如果直接启动失败，用系统启动Intent重试
            if (mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName()) != null) {
                Intent systemIntent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
                systemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                mContext.startActivity(systemIntent);
            } else {
                mContext.startActivity(launchIntent);
            }
            Log.d(TAG, "App启动成功");
        } catch (Exception e) {
            Log.e(TAG, "App启动失败，重试: ", e);
            // 终极兜底：再延迟5秒重试最后一次，模拟器加倍重试延迟
            boolean isEmulator = isEmulator();
            long finalRetryDelay = isEmulator ? 100 : 500;
            new Handler(Looper.getMainLooper()).postDelayed(this::startApp, finalRetryDelay);
        }
    }

    // 判断是否为模拟器
    private boolean isEmulator() {
        String[] keys = {
                "雷电输入法", "雷电桌面", "Nox", "BlueStacks", "MuMu", "逍遥模拟器", "腾讯手游助手"
        };
        try {
            PackageManager pm = mContext.getPackageManager();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                String label = pm.getApplicationLabel(info).toString();
                for (String k : keys) {
                    if (label.contains(k)) return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

}