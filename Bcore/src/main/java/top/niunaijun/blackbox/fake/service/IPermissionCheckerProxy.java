package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.app.AppOpsManager;
import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class IPermissionCheckerProxy extends BinderInvocationStub {
    public static final String TAG = "PermissionCheckerStub";

    private static final String PERMISSION_CHECKER_SERVICE = "permission_checker";
    private static final int PERMISSION_GRANTED = 0;

    public IPermissionCheckerProxy() {
        super(BRServiceManager.get().getService(PERMISSION_CHECKER_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(PERMISSION_CHECKER_SERVICE);
            if (binder == null) {
                Slog.w(TAG, "Permission checker service binder is null");
                return null;
            }

            Class<?> stubClass = Class.forName("android.permission.IPermissionChecker$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to get permission checker service: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(PERMISSION_CHECKER_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        fixPermissionCheckerArgs(args);

        if ("checkPermission".equals(methodName) && containsCapturePermission(args)) {
            Slog.d(TAG, "PermissionChecker checkPermission: granting capture permission");
            return PERMISSION_GRANTED;
        }

        if ("checkOp".equals(methodName)) {
            Slog.d(TAG, "PermissionChecker checkOp: allowing app op");
            return PERMISSION_GRANTED;
        }

        if ("finishDataDelivery".equals(methodName)) {
            return null;
        }

        try {
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            if (containsCapturePermission(args)) {
                Slog.w(TAG, "PermissionChecker SecurityException for capture permission: " + e.getMessage());
                return PERMISSION_GRANTED;
            }
            throw e;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static void fixPermissionCheckerArgs(Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
        AttributionSourceUtils.fixAttributionSourceInArgs(args);
    }

    private static boolean containsCapturePermission(Object[] args) {
        if (args == null) {
            return false;
        }
        for (Object arg : args) {
            if (arg instanceof String && isCapturePermission((String) arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCapturePermission(String permission) {
        if (permission == null) {
            return false;
        }
        return permission.equals(Manifest.permission.CAMERA)
                || permission.equals(Manifest.permission.RECORD_AUDIO)
                || permission.equals(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                || permission.equals(Manifest.permission.MODIFY_AUDIO_SETTINGS)
                || permission.equals("android.permission.FOREGROUND_SERVICE_CAMERA")
                || permission.equals("android.permission.FOREGROUND_SERVICE_MICROPHONE")
                || permission.equals(AppOpsManager.permissionToOp(Manifest.permission.CAMERA))
                || permission.equals(AppOpsManager.permissionToOp(Manifest.permission.RECORD_AUDIO));
    }
}
