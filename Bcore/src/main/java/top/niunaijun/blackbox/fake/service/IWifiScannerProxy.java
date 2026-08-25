package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


public class IWifiScannerProxy extends BinderInvocationStub {
    public static final String TAG = "IWifiScannerProxy";
    private static final String WIFI_SCANNER_SERVICE = "wifiscanner";

    public IWifiScannerProxy() {
        super(BRServiceManager.get().getService(WIFI_SCANNER_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(WIFI_SCANNER_SERVICE);
            if (binder == null) {
                return null;
            }
            Class<?> stubClass = Class.forName("android.net.wifi.IWifiScanner$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to get wifi scanner service: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(WIFI_SCANNER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        MethodParameterUtils.replaceLastUid(args);
        if (BLocationManager.isFakeLocationEnable() && isScanMethod(method.getName())) {
            Slog.d(TAG, "Blocking real Wi-Fi scanner method: " + method.getName());
            return defaultValue(method.getReturnType());
        }
        return super.invoke(proxy, method, args);
    }

    private static boolean isScanMethod(String methodName) {
        if (methodName == null) {
            return false;
        }
        return methodName.contains("Scan") || methodName.contains("scan");
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return true;
        }
        if (List.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        if (Bundle.class.isAssignableFrom(type)) {
            return new Bundle();
        }
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }
}
