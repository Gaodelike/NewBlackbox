package top.niunaijun.blackbox.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class HackAppUtils {
    private static final String TAG = "HackAppUtils";
    private static volatile boolean sWeWorkPhotoBinderReady;

    
    public static void enableQQLogOutput(String packageName, ClassLoader classLoader) {
        if ("com.tencent.mobileqq".equals(packageName)) {
            try {
                Reflector.on("com.tencent.qphone.base.util.QLog", true, classLoader)
                        .field("UIN_REPORTLOG_LEVEL")
                        .set(100);
            } catch (Exception e) {
                e.printStackTrace();
                
            }
        }
    }

    public static void fixWeWorkActivityStartup(String packageName, ClassLoader classLoader) {
        if (!"com.tencent.wework".equals(packageName) || classLoader == null) {
            return;
        }

        if (sWeWorkPhotoBinderReady && isWeWorkPhotoBinderReady(classLoader)) {
            return;
        }

        // Enterprise WeChat expects InitComponentTask to install this image binder before
        // login activities inflate TopBarView. In the sandbox this task can run too late.
        if (instantiate(classLoader, "k100") && isWeWorkPhotoBinderReady(classLoader)) {
            sWeWorkPhotoBinderReady = true;
            Slog.d(TAG, "Enterprise WeChat PhotoImageView binder installed");
            return;
        }

        if (invokeNoArgMethodOnStaticInstance(classLoader, "j2f0", "a", "a")
                && isWeWorkPhotoBinderReady(classLoader)) {
            sWeWorkPhotoBinderReady = true;
            Slog.d(TAG, "Enterprise WeChat component injectors installed");
        }
    }

    private static boolean instantiate(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = Class.forName(className, true, classLoader);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            return true;
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to instantiate " + className + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean invokeNoArgMethodOnStaticInstance(ClassLoader classLoader,
                                                            String className,
                                                            String fieldName,
                                                            String methodName) {
        try {
            Class<?> clazz = Class.forName(className, true, classLoader);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object instance = field.get(null);
            if (instance == null) {
                return false;
            }
            java.lang.reflect.Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(instance);
            return true;
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to invoke " + className + "." + methodName + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean isWeWorkPhotoBinderReady(ClassLoader classLoader) {
        try {
            Class<?> holderClass = Class.forName(
                    "com.tencent.wework.common.views.PhotoImageView$e$b", true, classLoader);
            Field field = holderClass.getDeclaredField("a");
            field.setAccessible(true);
            Object binder = field.get(null);
            return binder != null
                    && !"com.tencent.wework.common.views.PhotoImageView$e$b$a"
                    .equals(binder.getClass().getName());
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to inspect Enterprise WeChat PhotoImageView binder: " + e.getMessage());
            return false;
        }
    }
}
