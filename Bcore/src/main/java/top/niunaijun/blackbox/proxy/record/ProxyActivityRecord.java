package top.niunaijun.blackbox.proxy.record;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.Slog;


public class ProxyActivityRecord {
    private static final String TAG = "ProxyActivityRecord";
    private static final String KEY_USER_ID = "_B_|_user_id_";
    private static final String KEY_ACTIVITY_INFO = "_B_|_activity_info_";
    private static final String KEY_TARGET = "_B_|_target_";
    private static final String KEY_TARGET_BYTES = "_B_|_target_bytes_";
    private static final String KEY_ACTIVITY_RECORD = "_B_|_activity_record_v_";

    public int mUserId;
    public ActivityInfo mActivityInfo;
    public Intent mTarget;
    public IBinder mActivityRecord;

    public ProxyActivityRecord(int userId, ActivityInfo activityInfo, Intent target, IBinder activityRecord) {
        mUserId = userId;
        mActivityInfo = activityInfo;
        mTarget = target;
        mActivityRecord = activityRecord;
    }

    public static void saveStub(Intent shadow, Intent target, ActivityInfo activityInfo, IBinder activityRecord, int userId) {
        shadow.putExtra(KEY_USER_ID, userId);
        shadow.putExtra(KEY_ACTIVITY_INFO, activityInfo);

        byte[] targetBytes = Build.VERSION.SDK_INT >= 36 ? marshallTarget(target) : null;
        if (targetBytes != null) {
            shadow.putExtra(KEY_TARGET_BYTES, targetBytes);
            shadow.removeExtra(KEY_TARGET);
        } else {
            removeLaunchSecurityProtectionForInternalTarget(target, activityInfo);
            shadow.putExtra(KEY_TARGET, target);
            shadow.removeExtra(KEY_TARGET_BYTES);
        }
        BundleCompat.putBinder(shadow, KEY_ACTIVITY_RECORD, activityRecord);
    }

    private static byte[] marshallTarget(Intent target) {
        if (target == null) {
            return null;
        }

        Parcel parcel = Parcel.obtain();
        try {
            target.writeToParcel(parcel, 0);
            if (parcel.hasFileDescriptors()) {
                Slog.w(TAG, "Target intent contains file descriptors; using Parcelable fallback");
                return null;
            }
            return parcel.marshall();
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to marshal target intent: " + e.getMessage());
            return null;
        } finally {
            parcel.recycle();
        }
    }

    private static Intent unmarshallTarget(byte[] targetBytes) {
        if (targetBytes == null || targetBytes.length == 0) {
            return null;
        }

        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(targetBytes, 0, targetBytes.length);
            parcel.setDataPosition(0);
            return Intent.CREATOR.createFromParcel(parcel);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to unmarshal target intent: " + e.getMessage());
            return null;
        } finally {
            parcel.recycle();
        }
    }

    private static void removeLaunchSecurityProtectionForInternalTarget(Intent target, ActivityInfo activityInfo) {
        if (Build.VERSION.SDK_INT < 36 || target == null || activityInfo == null) {
            return;
        }

        ComponentName component = target.getComponent();
        if (component == null || !activityInfo.packageName.equals(component.getPackageName())) {
            return;
        }

        try {
            // Android 16 may otherwise inspect nested app-specific Parcelable extras in system_server.
            Method method = Intent.class.getMethod("removeLaunchSecurityProtection");
            method.invoke(target);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to relax Android 16 launch protection for internal target: " + e.getMessage());
        }
    }

    public static ProxyActivityRecord create(Intent intent) {
        int userId = intent.getIntExtra(KEY_USER_ID, 0);
        ActivityInfo activityInfo = intent.getParcelableExtra(KEY_ACTIVITY_INFO);
        Intent target = unmarshallTarget(intent.getByteArrayExtra(KEY_TARGET_BYTES));
        if (target == null) {
            target = intent.getParcelableExtra(KEY_TARGET);
        }
        IBinder activityRecord = BundleCompat.getBinder(intent, KEY_ACTIVITY_RECORD);
        return new ProxyActivityRecord(userId, activityInfo, target, activityRecord);
    }
}
