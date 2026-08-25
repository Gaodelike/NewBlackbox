package top.niunaijun.blackbox.utils;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;


public class AttributionSourceUtils {
    private static final String TAG = "AttributionSourceUtils";

    
    public static void fixAttributionSourceInArgs(Object[] args) {
        if (args == null) return;
        
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                try {
                    fixAttributionSourceUid(arg);
                    Slog.d(TAG, "Fixed AttributionSource UID in method arguments");
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to fix AttributionSource in args: " + e.getMessage());
                }
            }
        }
        
        
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && arg.getClass().getName().contains("Bundle")) {
                try {
                    fixAttributionSourceInBundle(arg);
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to fix AttributionSource in Bundle: " + e.getMessage());
                }
            }
        }
    }

    
    public static void fixAttributionSourceUid(Object attributionSource) {
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        fixAttributionSourceUid(attributionSource, visited, 0);
    }

    private static void fixAttributionSourceUid(Object attributionSource, java.util.Set<Object> visited, int depth) {
        try {
            if (attributionSource == null) return;
            if (depth > 8 || !visited.add(attributionSource)) return;

            try {
                ContextCompat.fixAttributionSourceState(attributionSource, BlackBoxCore.getHostUid());
            } catch (Throwable ignored) {
            }
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            
            
            String[] uidFieldNames = {"mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"};
            
            for (String fieldName : uidFieldNames) {
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField(fieldName);
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, BlackBoxCore.getHostUid());
                    Slog.d(TAG, "Fixed AttributionSource UID via field: " + fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Try the next field name.
                }
            }
            
            
            try {
                java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                setUidMethod.setAccessible(true);
                setUidMethod.invoke(attributionSource, BlackBoxCore.getHostUid());
                Slog.d(TAG, "Fixed AttributionSource UID via setter method");
            } catch (Exception e) {
                // The setter is not available on this Android version.
            }

            String[] pidFieldNames = {"mPid", "pid", "mCallingPid", "callingPid", "mSourcePid", "sourcePid"};

            for (String fieldName : pidFieldNames) {
                try {
                    java.lang.reflect.Field pidField = attributionSourceClass.getDeclaredField(fieldName);
                    pidField.setAccessible(true);
                    pidField.set(attributionSource, android.os.Process.myPid());
                    Slog.d(TAG, "Fixed AttributionSource PID via field: " + fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Try the next field name.
                }
            }

            try {
                java.lang.reflect.Method setPidMethod = attributionSourceClass.getDeclaredMethod("setPid", int.class);
                setPidMethod.setAccessible(true);
                setPidMethod.invoke(attributionSource, android.os.Process.myPid());
                Slog.d(TAG, "Fixed AttributionSource PID via setter method");
            } catch (Exception e) {
                // The setter is not available on this Android version.
            }

            fixNestedAttributionSources(attributionSource, visited, depth + 1);

            String[] packageFieldNames = {"mPackageName", "packageName", "mSourcePackage", "sourcePackage"};
            
            for (String fieldName : packageFieldNames) {
                try {
                    java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField(fieldName);
                    packageField.setAccessible(true);
                    packageField.set(attributionSource, BlackBoxCore.getHostPkg());
                    Slog.d(TAG, "Fixed AttributionSource package name via field: " + fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    // Try the next field name.
                }
            }
            
        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    private static void fixNestedAttributionSources(Object attributionSource, java.util.Set<Object> visited, int depth) {
        try {
            java.lang.reflect.Field[] fields = attributionSource.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(attributionSource);
                    if (value == null) {
                        continue;
                    }
                    if (value.getClass().isArray()) {
                        int length = java.lang.reflect.Array.getLength(value);
                        for (int i = 0; i < length; i++) {
                            Object item = java.lang.reflect.Array.get(value, i);
                            if (item != null && item.getClass().getName().contains("AttributionSource")) {
                                fixAttributionSourceUid(item, visited, depth);
                            }
                        }
                        continue;
                    }
                    if (value.getClass().getName().contains("AttributionSource")) {
                        fixAttributionSourceUid(value, visited, depth);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    
    public static void fixAttributionSourceInBundle(Object bundle) {
        try {
            if (bundle == null) return;
            
            
            java.lang.reflect.Method keySetMethod = bundle.getClass().getMethod("keySet");
            java.util.Set<String> keys = (java.util.Set<String>) keySetMethod.invoke(bundle);
            
            for (String key : keys) {
                try {
                    java.lang.reflect.Method getMethod = bundle.getClass().getMethod("get", String.class);
                    Object value = getMethod.invoke(bundle, key);
                    
                    if (value != null && value.getClass().getName().contains("AttributionSource")) {
                        fixAttributionSourceUid(value);
                        Slog.d(TAG, "Fixed AttributionSource UID in Bundle key: " + key);
                    }
                } catch (Exception e) {
                    
                }
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource in Bundle: " + e.getMessage());
        }
    }

    
    public static Object createSafeAttributionSource() {
        return createSafeAttributionSource(BlackBoxCore.getHostUid(), BlackBoxCore.getHostPkg());
    }

    public static Object createSafeAttributionSource(int uid, String packageName) {
        try {
            Class<?> attributionSourceClass = Class.forName("android.content.AttributionSource");

            Object attributionSource = createAttributionSourceWithBuilder(uid, packageName);
            if (attributionSource != null) {
                fixAttributionSourceUid(attributionSource);
                return attributionSource;
            }

            try {
                java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor(
                        int.class, int.class, String.class, String.class);
                constructor.setAccessible(true);
                attributionSource = constructor.newInstance(uid, android.os.Process.myPid(), packageName, null);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor(
                            int.class, String.class, String.class);
                    constructor.setAccessible(true);
                    attributionSource = constructor.newInstance(uid, packageName, null);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor(
                                int.class, String.class);
                        constructor.setAccessible(true);
                        attributionSource = constructor.newInstance(uid, packageName);
                    } catch (Exception e3) {
                        try {
                            java.lang.reflect.Constructor<?> constructor = attributionSourceClass.getDeclaredConstructor();
                            constructor.setAccessible(true);
                            attributionSource = constructor.newInstance();
                        } catch (Exception e4) {
                            Slog.w(TAG, "Could not create safe AttributionSource: " + e4.getMessage());
                            return null;
                        }
                    }
                }
            }

            fixAttributionSourceUid(attributionSource);
            return attributionSource;
        } catch (Exception e) {
            Slog.w(TAG, "Error creating safe AttributionSource: " + e.getMessage());
            return null;
        }
    }

    private static Object createAttributionSourceWithBuilder(int uid, String packageName) {
        try {
            Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
            java.lang.reflect.Constructor<?> constructor = builderClass.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            Object builder = constructor.newInstance(uid);

            invokeBuilderSetter(builder, "setPid", int.class, android.os.Process.myPid());
            invokeBuilderSetter(builder, "setPackageName", String.class, packageName);
            invokeBuilderSetter(builder, "setAttributionTag", String.class, null);

            java.lang.reflect.Method build = builderClass.getDeclaredMethod("build");
            build.setAccessible(true);
            return build.invoke(builder);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void invokeBuilderSetter(Object builder, String methodName, Class<?> parameterType, Object value) {
        try {
            java.lang.reflect.Method method = builder.getClass().getDeclaredMethod(methodName, parameterType);
            method.setAccessible(true);
            method.invoke(builder, value);
        } catch (Throwable ignored) {
        }
    }

    
    public static boolean validateAttributionSource(Object attributionSource) {
        try {
            if (attributionSource == null) return false;
            
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            String[] uidFieldNames = {"mUid", "uid", "mCallingUid", "callingUid", "mSourceUid", "sourceUid"};
            
            for (String fieldName : uidFieldNames) {
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField(fieldName);
                    uidField.setAccessible(true);
                    Object uidValue = uidField.get(attributionSource);
                    if (uidValue instanceof Integer) {
                        int uid = (Integer) uidValue;
                        if (uid > 0) {
                            Slog.d(TAG, "AttributionSource UID validation passed: " + uid);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    
                }
            }
            
            
            Slog.w(TAG, "AttributionSource validation failed, attempting to fix");
            fixAttributionSourceUid(attributionSource);
            return true;
            
        } catch (Exception e) {
            Slog.w(TAG, "Error validating AttributionSource: " + e.getMessage());
            return false;
        }
    }
}
