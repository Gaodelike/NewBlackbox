package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Method;

import black.com.android.internal.view.inputmethod.BRInputMethodManager;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


@ScanClass(IInputMethodManagerProxy.class)
public class IInputMethodManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IInputMethodManagerProxy";

    public IInputMethodManagerProxy() {
        super(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE);
            Class<?> stubClass = Class.forName("com.android.internal.view.IInputMethodManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to get input method service: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            Object inputMethodManager = BlackBoxCore.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null && BRInputMethodManager.get(inputMethodManager)._check_mService() != null) {
                BRInputMethodManager.get(inputMethodManager)._set_mService((IInterface) proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            fixInputMethodArgs(args);
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            String message = e.getMessage();
            if (message != null && message.contains("does not belong to")) {
                Slog.w(TAG, "Input method UID/package mismatch in " + method.getName() + ": " + message);
                return defaultValue(method.getReturnType());
            }
            throw e;
        }
    }

    @ProxyMethod("startInputOrWindowGainedFocus")
    public static class StartInputOrWindowGainedFocus extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixInputMethodArgs(args);
            return method.invoke(who, args);
        }
    }

    private static void fixInputMethodArgs(Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
        fixEditorInfo(args);
    }

    private static void fixEditorInfo(Object[] args) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            if (arg instanceof EditorInfo) {
                ((EditorInfo) arg).packageName = BlackBoxCore.getHostPkg();
            }
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0f;
        }
        if (returnType == Double.TYPE) {
            return 0d;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }
}
