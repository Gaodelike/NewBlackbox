package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import black.android.app.BRActivityThread;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.service.base.PkgMethodProxy;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
        
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        addMethodHook(new PkgMethodProxy("grantRuntimePermission"));
        addMethodHook(new PkgMethodProxy("revokeRuntimePermission"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                Slog.d(TAG, "PermissionManager checkPermission: granting capture permission");
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("checkUidPermission")
    public static class CheckUidPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                Slog.d(TAG, "PermissionManager checkUidPermission: granting capture permission");
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("shouldShowRequestPermissionRationale")
    public static class ShouldShowRequestPermissionRationale extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                Slog.d(TAG, "PermissionManager shouldShowRequestPermissionRationale: returning false");
                return false;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isPermissionRevokedByPolicy")
    public static class IsPermissionRevokedByPolicy extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                return false;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getPermissionFlags")
    public static class GetPermissionFlags extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                return 0;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getPermissionRequestState")
    public static class GetPermissionRequestState extends MethodHook {
        private static final int PERMISSION_REQUEST_STATE_GRANTED = 0;

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixPermissionArgs(args);
            if (containsCapturePermission(args)) {
                Slog.d(TAG, "PermissionManager getPermissionRequestState: returning granted");
                return PERMISSION_REQUEST_STATE_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    private static void fixPermissionArgs(Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
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
                || permission.equals("android.permission.FOREGROUND_SERVICE_MICROPHONE");
    }
}
