package top.niunaijun.blackbox.proxy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.proxy.record.ProxyPendingRecord;


public class ProxyPendingService extends Service {
    public static final String TAG = "ProxyPendingService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                intent.setExtrasClassLoader(getClassLoader());
                ProxyPendingRecord record = ProxyPendingRecord.create(intent);
                if (record.mTarget != null) {
                    BlackBoxCore.getBActivityManager().startService(record.mTarget, null,
                            record.mForegroundService, record.mUserId);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Unable to dispatch pending virtual service", e);
        } finally {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
