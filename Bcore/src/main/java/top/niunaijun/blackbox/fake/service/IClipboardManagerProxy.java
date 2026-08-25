package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

import black.android.content.BRClipboardManager;
import black.android.content.BRClipboardManagerOreo;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class IClipboardManagerProxy extends BinderInvocationStub {
    private static final String TAG = "ClipboardManagerStub";

    public IClipboardManagerProxy() {
        super(BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE);
            Class<?> stubClass = Class.forName("android.content.IClipboard$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to get clipboard service: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            BRClipboardManager.get()._set_sService((IInterface) proxyInvocation);
        } catch (Throwable ignored) {
        }

        try {
            BRClipboardManagerOreo.get()._set_sService((IInterface) proxyInvocation);
        } catch (Throwable ignored) {
        }

        try {
            Object clipboardManager = BlackBoxCore.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null && BRClipboardManagerOreo.get(clipboardManager)._check_mService() != null) {
                BRClipboardManagerOreo.get(clipboardManager)._set_mService((IInterface) proxyInvocation);
            }
        } catch (Throwable ignored) {
        }

        replaceSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            MethodParameterUtils.replaceAllAppPkg(args);
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            String message = e.getMessage();
            if (message != null && message.contains("does not belong to")) {
                Slog.w(TAG, "Clipboard UID/package mismatch in " + method.getName() + ": " + message);
                return defaultValue(method.getReturnType());
            }
            throw e;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
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
