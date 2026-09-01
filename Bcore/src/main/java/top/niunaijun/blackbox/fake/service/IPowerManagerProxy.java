package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.WorkSource;

import java.lang.reflect.Method;

import black.android.os.BRIPowerManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class IPowerManagerProxy extends BinderInvocationStub {
    public IPowerManagerProxy() {
        super(BRServiceManager.get().getService(Context.POWER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIPowerManagerStub.get().asInterface(BRServiceManager.get().getService(Context.POWER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.POWER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethods({"acquireWakeLock", "acquireWakeLockWithUid"})
    public static class AcquireWakeLock extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            preparePowerCall(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("updateWakeLockWorkSource")
    public static class UpdateWakeLockWorkSource extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            preparePowerCall(args);
            return method.invoke(who, args);
        }
    }

    private static void preparePowerCall(Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof WorkSource) {
                // The host UID is already attributed by Binder. A virtual UID in WorkSource
                // would fail UPDATE_DEVICE_STATS validation in system_server.
                args[i] = null;
            }
        }
    }
}
