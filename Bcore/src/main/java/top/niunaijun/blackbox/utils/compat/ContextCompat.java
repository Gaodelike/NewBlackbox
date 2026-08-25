package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.*;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;
import top.niunaijun.blackbox.utils.Slog;


public class ContextCompat {
    public static final String TAG = "ContextCompat";

    public static void fixAttributionSourceState(Object obj, int uid) {
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        fixAttributionSourceState(obj, uid, visited, 0);
    }

    private static void fixAttributionSourceState(Object obj, int uid, java.util.Set<Object> visited, int depth) {
        Object mAttributionSourceState;
        if (obj == null || depth > 8 || !visited.add(obj)) {
            return;
        }

        try {
            if (BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
                mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();

                fixAttributionSourceStateObject(mAttributionSourceState, uid, visited, depth + 1);
                fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid, visited, depth + 1);
                return;
            }
        } catch (Throwable ignored) {
        }

        if (obj.getClass().getName().contains("AttributionSourceState")) {
            fixAttributionSourceStateObject(obj, uid, visited, depth + 1);
        }
    }

    private static void fixAttributionSourceStateObject(Object attributionSourceState, int uid, java.util.Set<Object> visited, int depth) {
        if (attributionSourceState == null || depth > 8) {
            return;
        }

        try {
            AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(attributionSourceState);
            attributionSourceStateContext._set_packageName(BlackBoxCore.getHostPkg());
            attributionSourceStateContext._set_uid(uid);
        } catch (Throwable ignored) {
        }

        setFieldIfExists(attributionSourceState, "packageName", BlackBoxCore.getHostPkg());
        setFieldIfExists(attributionSourceState, "uid", uid);
        setFieldIfExists(attributionSourceState, "pid", android.os.Process.myPid());

        Object next = getFieldValue(attributionSourceState, "next");
        if (next == null || !next.getClass().isArray()) {
            return;
        }

        int length = java.lang.reflect.Array.getLength(next);
        for (int i = 0; i < length; i++) {
            Object item = java.lang.reflect.Array.get(next, i);
            if (item != null) {
                fixAttributionSourceState(item, uid, visited, depth + 1);
            }
        }
    }

    private static void setFieldIfExists(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void fix(Context context) {
        try {
            
            if (context == null) {
                Slog.w(TAG, "Context is null, skipping ContextCompat.fix");
                return;
            }
            
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }
            
            
            if (context == null) {
                Slog.w(TAG, "Base context is null after unwrapping, skipping ContextCompat.fix");
                return;
            }
            
            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            BRContextImpl.get(context)._set_mBasePackageName(BlackBoxCore.getHostPkg());
            BRContextImplKitkat.get(context)._set_mOpPackageName(BlackBoxCore.getHostPkg());
            
            try {
                BRContentResolver.get(context.getContentResolver())._set_mPackageName(BlackBoxCore.getHostPkg());
            } catch (Exception e) {
                Slog.w(TAG, "Failed to fix content resolver: " + e.getMessage());
            }

            if (BuildCompat.isS()) {
                try {
                    Object safeAttributionSource = AttributionSourceUtils.createSafeAttributionSource();
                    if (safeAttributionSource != null) {
                        fixAttributionSourceState(safeAttributionSource, BlackBoxCore.getHostUid());
                        setFieldIfExists(context, "mAttributionSource", safeAttributionSource);
                    } else {
                        fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), BlackBoxCore.getHostUid());
                    }
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to fix attribution source state: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error in ContextCompat.fix: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
