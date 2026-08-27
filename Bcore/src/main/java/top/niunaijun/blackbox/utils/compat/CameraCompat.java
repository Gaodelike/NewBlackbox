package top.niunaijun.blackbox.utils.compat;

import android.app.Activity;
import android.app.Application;
import android.os.IBinder;

import java.util.Locale;

import black.android.app.BRActivity;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.NativeCore;
import top.niunaijun.blackbox.fake.service.libcore.OsStub;
import top.niunaijun.blackbox.utils.Slog;

public class CameraCompat {
    private static final String TAG = "CameraCompat";
    private static final String WEWORK_PACKAGE = "com.tencent.wework";
    private static final String WEWORK_SCANNER_ACTIVITY =
            "com.tencent.wework.login.controller.LoginScannerActivity";
    private static final String WEWORK_CUSTOM_CAMERA_ACTIVITY =
            "com.tencent.wework.msg.controller.CustomCameraActivity";

    private static IBinder sCameraActivityToken;
    private static String sCameraActivityName;
    private static boolean sUsingHostCameraPackage;

    public static boolean needsHostCameraPackage(Activity activity) {
        return activity != null && needsHostCameraPackage(activity.getClass().getName());
    }

    public static boolean needsHostCameraPackage(String className) {
        if (className == null || !className.startsWith(WEWORK_PACKAGE + ".")) {
            return false;
        }
        if (WEWORK_SCANNER_ACTIVITY.equals(className)
                || WEWORK_CUSTOM_CAMERA_ACTIVITY.equals(className)) {
            return true;
        }

        String lowerName = className.toLowerCase(Locale.US);
        return lowerName.contains("camera")
                || lowerName.contains("scanner")
                || lowerName.contains("scan");
    }

    public static void enterHostCameraPackage(Activity activity) {
        if (!needsHostCameraPackage(activity)) {
            return;
        }

        fixActivityContext(activity);
        enterHostCameraPackage(activity.getClass().getName(), getActivityToken(activity));
    }

    public static void enterHostCameraPackage(String className) {
        enterHostCameraPackage(className, null);
    }

    public static void enterHostCameraPackage(String className, IBinder token) {
        if (!needsHostCameraPackage(className)) {
            return;
        }

        try {
            fixApplicationContext();

            if (token != null) {
                sCameraActivityToken = token;
            }
            sCameraActivityName = className;
            sUsingHostCameraPackage = true;
            OsStub.setUseHostUidForCamera(true);
            NativeCore.setUseHostCallingUidForCamera(true);

            Slog.d(TAG, "Using scoped host UID for legacy camera: activity=" + className);
        } catch (Throwable e) {
            sCameraActivityToken = null;
            sCameraActivityName = null;
            sUsingHostCameraPackage = false;
            OsStub.setUseHostUidForCamera(false);
            NativeCore.setUseHostCallingUidForCamera(false);
            Slog.w(TAG, "Failed to enable scoped legacy camera identity: " + e.getMessage());
        }
    }

    public static void restoreHostCameraPackage(Activity activity) {
        if (activity == null) {
            restoreHostCameraPackage();
            return;
        }
        restoreHostCameraPackage(getActivityToken(activity));
    }

    public static void restoreHostCameraPackage(IBinder token) {
        if (token != null && sCameraActivityToken != null && !isSameToken(token, sCameraActivityToken)) {
            return;
        }
        restoreHostCameraPackage();
    }

    public static void restoreHostCameraPackage() {
        if (!sUsingHostCameraPackage) {
            return;
        }

        try {
            Slog.d(TAG, "Restored scoped legacy camera identity: activity="
                    + sCameraActivityName);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to restore scoped legacy camera identity: " + e.getMessage());
        } finally {
            sCameraActivityToken = null;
            sCameraActivityName = null;
            sUsingHostCameraPackage = false;
            OsStub.setUseHostUidForCamera(false);
            NativeCore.setUseHostCallingUidForCamera(false);
        }
    }

    private static IBinder getActivityToken(Activity activity) {
        try {
            return BRActivity.get(activity).mToken();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSameToken(IBinder left, IBinder right) {
        return left == right || left.equals(right);
    }

    private static void fixActivityContext(Activity activity) {
        try {
            ContextCompat.fix(activity);
        } catch (Throwable ignored) {
        }
        try {
            if (activity.getApplicationContext() != null) {
                ContextCompat.fix(activity.getApplicationContext());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fixApplicationContext() {
        try {
            Application application = BActivityThread.getApplication();
            if (application != null) {
                ContextCompat.fix(application);
            }
        } catch (Throwable ignored) {
        }
    }

}
