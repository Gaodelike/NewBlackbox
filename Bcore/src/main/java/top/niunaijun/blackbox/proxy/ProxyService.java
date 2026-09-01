package top.niunaijun.blackbox.proxy;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.app.dispatcher.AppServiceDispatcher;


public class ProxyService extends Service {
    public static final String TAG = "StubService";
    private static final String PUSH_CHANNEL_ID = "blackbox_daemon_channel";
    private boolean mPushProcessForeground;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        IBinder binder = AppServiceDispatcher.get().onBind(intent);
        promotePushProcessIfNeeded();
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int result = AppServiceDispatcher.get().onStartCommand(intent, flags, startId);
        promotePushProcessIfNeeded();
        return result;
    }

    @Override
    public void onDestroy() {
        if (mPushProcessForeground) {
            stopForeground(true);
            mPushProcessForeground = false;
        }
        super.onDestroy();
        AppServiceDispatcher.get().onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppServiceDispatcher.get().onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppServiceDispatcher.get().onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppServiceDispatcher.get().onTrimMemory(level);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        AppServiceDispatcher.get().onUnbind(intent);
        return false;
    }

    private void promotePushProcessIfNeeded() {
        if (mPushProcessForeground || !isSupportedPushProcess()) {
            return;
        }
        try {
            String virtualPackage = BActivityThread.getAppPackageName();
            Notification notification = new NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
                    .setContentTitle("BlackBox 消息服务")
                    .setContentText(getPushNotificationText(virtualPackage))
                    .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            int notificationId = (virtualPackage + '|' + BActivityThread.getUserId()
                    + "|push").hashCode();
            startForeground(notificationId, notification);
            mPushProcessForeground = true;
            Log.i(TAG, "Promoted virtual push process: "
                    + BActivityThread.getAppProcessName());
        } catch (Throwable e) {
            Log.e(TAG, "Unable to promote virtual push process", e);
        }
    }

    private boolean isSupportedPushProcess() {
        String packageName = BActivityThread.getAppPackageName();
        String processName = BActivityThread.getAppProcessName();
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)
                || !processName.endsWith(":push")) {
            return false;
        }
        return "com.tencent.wework".equals(packageName)
                || "com.tencent.mm".equals(packageName);
    }

    private String getPushNotificationText(String packageName) {
        if ("com.tencent.wework".equals(packageName)) {
            return "正在保持企业微信后台连接";
        }
        return "正在保持微信后台连接";
    }

    public static class P0 extends ProxyService {

    }

    public static class P1 extends ProxyService {

    }

    public static class P2 extends ProxyService {

    }

    public static class P3 extends ProxyService {

    }

    public static class P4 extends ProxyService {

    }

    public static class P5 extends ProxyService {

    }

    public static class P6 extends ProxyService {

    }

    public static class P7 extends ProxyService {

    }

    public static class P8 extends ProxyService {

    }

    public static class P9 extends ProxyService {

    }

    public static class P10 extends ProxyService {

    }

    public static class P11 extends ProxyService {

    }

    public static class P12 extends ProxyService {

    }

    public static class P13 extends ProxyService {

    }

    public static class P14 extends ProxyService {

    }

    public static class P15 extends ProxyService {

    }

    public static class P16 extends ProxyService {

    }

    public static class P17 extends ProxyService {

    }

    public static class P18 extends ProxyService {

    }

    public static class P19 extends ProxyService {

    }

    public static class P20 extends ProxyService {

    }

    public static class P21 extends ProxyService {

    }

    public static class P22 extends ProxyService {

    }

    public static class P23 extends ProxyService {

    }

    public static class P24 extends ProxyService {

    }

    public static class P25 extends ProxyService {

    }

    public static class P26 extends ProxyService {

    }

    public static class P27 extends ProxyService {

    }

    public static class P28 extends ProxyService {

    }

    public static class P29 extends ProxyService {

    }

    public static class P30 extends ProxyService {

    }

    public static class P31 extends ProxyService {

    }

    public static class P32 extends ProxyService {

    }

    public static class P33 extends ProxyService {

    }

    public static class P34 extends ProxyService {

    }

    public static class P35 extends ProxyService {

    }

    public static class P36 extends ProxyService {

    }

    public static class P37 extends ProxyService {

    }

    public static class P38 extends ProxyService {

    }

    public static class P39 extends ProxyService {

    }

    public static class P40 extends ProxyService {

    }

    public static class P41 extends ProxyService {

    }

    public static class P42 extends ProxyService {

    }

    public static class P43 extends ProxyService {

    }

    public static class P44 extends ProxyService {

    }

    public static class P45 extends ProxyService {

    }

    public static class P46 extends ProxyService {

    }

    public static class P47 extends ProxyService {

    }

    public static class P48 extends ProxyService {

    }

    public static class P49 extends ProxyService {

    }
}
