package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.view.inputmethod.BRIInputMethodManagerGlobalInvoker;
import black.com.android.internal.view.inputmethod.BRInputMethodManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


public class IInputMethodManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IInputMethodManagerProxy";
    private static volatile IInterface sProxyService;

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
        sProxyService = (IInterface) proxyInvocation;
        ensureGlobalInvokerInjected(sProxyService);
        ensureInjected(BlackBoxCore.getContext());
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        try {
            Object inputMethodManager = BlackBoxCore.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            return inputMethodManager != null
                    && BRInputMethodManager.get(inputMethodManager).mService() != sProxyService;
        } catch (Throwable e) {
            return true;
        }
    }

    public static void ensureInjected(Context context) {
        IInterface proxyService = sProxyService;
        if (context == null || proxyService == null) {
            return;
        }
        ensureGlobalInvokerInjected(proxyService);
        try {
            Object inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager == null
                    || BRInputMethodManager.get(inputMethodManager)._check_mService() == null) {
                return;
            }
            if (BRInputMethodManager.get(inputMethodManager).mService() != proxyService) {
                BRInputMethodManager.get(inputMethodManager)._set_mService(proxyService);
                Slog.d(TAG, "Injected InputMethodManager for virtual activity context");
            }
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to inject activity InputMethodManager: " + e.getMessage());
        }
    }

    private static void ensureGlobalInvokerInjected(IInterface proxyService) {
        try {
            if (BRIInputMethodManagerGlobalInvoker.get()._check_sServiceCache() == null) {
                return;
            }
            if (BRIInputMethodManagerGlobalInvoker.get().sServiceCache() != proxyService) {
                BRIInputMethodManagerGlobalInvoker.get()._set_sServiceCache(proxyService);
                Slog.d(TAG, "Injected IInputMethodManagerGlobalInvoker service cache");
            }
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to inject global input method service: " + e.getMessage());
        }
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
