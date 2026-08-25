package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class ICameraServiceProxy extends BinderInvocationStub {
    public static final String TAG = "CameraServiceStub";

    private static final String CAMERA_SERVICE = "media.camera";

    public ICameraServiceProxy() {
        super(BRServiceManager.get().getService(CAMERA_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(CAMERA_SERVICE);
            if (binder == null) {
                Slog.w(TAG, "Camera service binder is null");
                return null;
            }

            Class<?> stubClass = Class.forName("android.hardware.ICameraService$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to get camera service: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(CAMERA_SERVICE);
        replaceCameraManagerGlobal(proxyInvocation);
        replaceLegacyCameraService(proxyInvocation);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            fixCameraArgs(args);
            Slog.d(TAG, "Camera service call: " + method.getName()
                    + " args=" + briefArgs(args));
            Object result = super.invoke(proxy, method, args);
            Slog.d(TAG, "Camera service result: " + method.getName()
                    + " -> " + briefValue(result));
            return result;
        } catch (SecurityException e) {
            if (isUidPackageMismatch(e)) {
                Slog.w(TAG, "Camera UID/package mismatch in " + method.getName() + ": " + e.getMessage());
                fixCameraArgs(args);
                try {
                    Slog.d(TAG, "Camera service retry: " + method.getName()
                            + " args=" + briefArgs(args));
                    Object result = super.invoke(proxy, method, args);
                    Slog.d(TAG, "Camera service retry result: " + method.getName()
                            + " -> " + briefValue(result));
                    return result;
                } catch (SecurityException retry) {
                    if (isUidPackageMismatch(retry)) {
                        Slog.w(TAG, "Camera retry still failed in " + method.getName() + ": " + retry.getMessage());
                        return defaultValue(method.getReturnType());
                    }
                    throw retry;
                }
            }
            throw e;
        } catch (Throwable e) {
            Slog.w(TAG, "Camera service failed: " + method.getName()
                    + " args=" + briefArgs(args)
                    + " error=" + e.getClass().getName() + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static void fixCameraArgs(Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
        replaceCameraUidArgs(args);
        AttributionSourceUtils.fixAttributionSourceInArgs(args);
    }

    private static void replaceCameraManagerGlobal(Object proxyInvocation) {
        try {
            Class<?> globalClass = Class.forName("android.hardware.camera2.CameraManager$CameraManagerGlobal");
            Method get = globalClass.getDeclaredMethod("get");
            get.setAccessible(true);
            Object global = get.invoke(null);
            if (global == null) {
                return;
            }

            int replaced = 0;
            Class<?> current = globalClass;
            while (current != null && current != Object.class) {
                Field[] fields = current.getDeclaredFields();
                for (Field field : fields) {
                    if (!isCameraServiceField(field, proxyInvocation)) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        field.set(global, (IInterface) proxyInvocation);
                        replaced++;
                    } catch (Throwable e) {
                        Slog.w(TAG, "Failed to replace CameraManagerGlobal field "
                                + field.getName() + ": " + e.getMessage());
                    }
                }
                current = current.getSuperclass();
            }

            if (replaced == 0) {
                Slog.w(TAG, "No CameraManagerGlobal ICameraService field was replaced");
            }
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to replace CameraManagerGlobal service: " + e.getMessage());
        }
    }

    private static void replaceLegacyCameraService(Object proxyInvocation) {
        replaceStaticCameraServiceFields("android.hardware.Camera", proxyInvocation);
        replaceStaticCameraServiceFields("android.hardware.Camera$CameraManagerGlobal", proxyInvocation);
    }

    private static void replaceStaticCameraServiceFields(String className, Object proxyInvocation) {
        try {
            Class<?> cameraClass = Class.forName(className);
            int replaced = 0;
            Field[] fields = cameraClass.getDeclaredFields();
            for (Field field : fields) {
                if (!isStaticCameraServiceField(field, proxyInvocation)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(null, (IInterface) proxyInvocation);
                    replaced++;
                    Slog.d(TAG, "Replaced legacy camera service field "
                            + className + "#" + field.getName());
                } catch (Throwable e) {
                    Slog.w(TAG, "Failed to replace legacy camera service field "
                            + className + "#" + field.getName() + ": " + e.getMessage());
                }
            }

            if (replaced == 0) {
                Slog.w(TAG, "No legacy camera service field was replaced in " + className);
            }
        } catch (ClassNotFoundException e) {
            Slog.d(TAG, "Legacy camera service holder not found: " + className);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to replace legacy camera service holder "
                    + className + ": " + e.getMessage());
        }
    }

    private static void replaceCameraUidArgs(Object[] args) {
        if (args == null) {
            return;
        }
        int hostUid = BlackBoxCore.getHostUid();
        int bUid = BlackBoxCore.getBUid();
        int callingBUid = BlackBoxCore.getCallingBUid();
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof Integer)) {
                continue;
            }
            int uid = (Integer) args[i];
            if ((uid == bUid || uid == callingBUid) && uid > 0 && uid != hostUid) {
                args[i] = hostUid;
            }
        }
    }

    private static boolean isCameraServiceField(Field field, Object proxyInvocation) {
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)) {
            return false;
        }

        Class<?> fieldType = field.getType();
        if (!fieldType.isInstance(proxyInvocation)) {
            return false;
        }

        String fieldName = field.getName();
        String typeName = fieldType.getName();
        return "mCameraService".equals(fieldName)
                || fieldName.toLowerCase().contains("cameraservice")
                || typeName.contains("ICameraService");
    }

    private static boolean isStaticCameraServiceField(Field field, Object proxyInvocation) {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            return false;
        }

        Class<?> fieldType = field.getType();
        if (!fieldType.isInstance(proxyInvocation)) {
            return false;
        }

        String fieldName = field.getName();
        String typeName = fieldType.getName();
        return fieldName.toLowerCase().contains("cameraservice")
                || typeName.contains("ICameraService");
    }

    private static boolean isUidPackageMismatch(Throwable e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("does not belong to")
                || message.contains("doesn't match source uid")
                || message.contains("AttributionSource"));
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

    private static String briefArgs(Object[] args) {
        if (args == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(briefValue(args[i]));
        }
        builder.append("]");
        return truncate(builder.toString());
    }

    private static String briefValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            return truncate(arrayToString(value));
        }
        return truncate(String.valueOf(value));
    }

    private static String arrayToString(Object array) {
        if (array instanceof Object[]) {
            return Arrays.deepToString((Object[]) array);
        }
        int length = Array.getLength(array);
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(Array.get(array, i));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "null";
        }
        if (text.length() > 240) {
            return text.substring(0, 240) + "...";
        }
        return text;
    }
}
