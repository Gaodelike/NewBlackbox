package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;


public class ProxyPendingRecord {
    public int mUserId;
    public Intent mTarget;
    public boolean mForegroundService;

    public ProxyPendingRecord(Intent target, int userId) {
        this(target, userId, false);
    }

    public ProxyPendingRecord(Intent target, int userId, boolean foregroundService) {
        mUserId = userId;
        mTarget = target;
        mForegroundService = foregroundService;
    }

    public static void saveStub(Intent shadow, Intent target, int userId) {
        saveStub(shadow, target, userId, false);
    }

    public static void saveStub(Intent shadow, Intent target, int userId,
                                boolean foregroundService) {
        shadow.putExtra("_B_|_P_user_id_", userId);
        shadow.putExtra("_B_|_P_target_", target);
        shadow.putExtra("_B_|_P_foreground_service_", foregroundService);
    }

    public static ProxyPendingRecord create(Intent intent) {
        int userId = intent.getIntExtra("_B_|_P_user_id_", 0);
        Intent target = intent.getParcelableExtra("_B_|_P_target_");
        boolean foregroundService = intent.getBooleanExtra(
                "_B_|_P_foreground_service_", false);
        return new ProxyPendingRecord(target, userId, foregroundService);
    }

    @Override
    public String toString() {
        return "ProxyPendingActivityRecord{" +
                "mUserId=" + mUserId +
                ", mTarget=" + mTarget +
                ", mForegroundService=" + mForegroundService +
                '}';
    }
}
