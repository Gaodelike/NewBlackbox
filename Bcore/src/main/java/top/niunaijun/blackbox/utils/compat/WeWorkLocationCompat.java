package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;

/**
 * Keeps Tencent Location SDK callbacks consistent with BlackBox location state.
 */
public final class WeWorkLocationCompat {
    private static final String TAG = "WeWorkLocationCompat";
    private static final String WEWORK_PACKAGE = "com.tencent.wework";
    private static final String MANAGER_CLASS =
            "com.tencent.map.geolocation.sapp.TencentLocationManager";
    private static final long[] RETRY_DELAYS = {200L, 500L, 1000L, 2000L, 4000L, 8000L};
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RETRIES_SCHEDULED = new AtomicBoolean();

    private WeWorkLocationCompat() {
    }

    public static void ensure(Context context) {
        if (!isEnabled() || context == null) {
            return;
        }
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            return;
        }
        install(classLoader);
        scheduleRetries(classLoader);
    }

    private static boolean isEnabled() {
        return WEWORK_PACKAGE.equals(BActivityThread.getAppPackageName())
                && BLocationManager.isFakeLocationEnable();
    }

    private static void scheduleRetries(ClassLoader classLoader) {
        if (!RETRIES_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        for (long delay : RETRY_DELAYS) {
            MAIN_HANDLER.postDelayed(() -> {
                if (isEnabled()) {
                    install(classLoader);
                }
            }, delay);
        }
        MAIN_HANDLER.postDelayed(() -> RETRIES_SCHEDULED.set(false),
                RETRY_DELAYS[RETRY_DELAYS.length - 1] + 100L);
    }

    private static void install(ClassLoader classLoader) {
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS, false, classLoader);
            Field proxyField = findField(managerClass, "mProxyObj", null);
            Object managerProxy = proxyField == null ? null : proxyField.get(null);
            if (managerProxy == null) {
                return;
            }

            Field internalManagerField = findField(
                    managerProxy.getClass(), "locationManager", null);
            Object internalManager = internalManagerField == null
                    ? null : internalManagerField.get(managerProxy);
            if (internalManager == null) {
                return;
            }

            disableRequestCache(internalManager);
            wrapLocationListener(internalManager);
        } catch (ClassNotFoundException ignored) {
            // The component is loaded lazily by WeWork.
        } catch (Throwable e) {
            Log.w(TAG, "Unable to install Tencent location compatibility: " + e.getMessage());
        }
    }

    private static void disableRequestCache(Object internalManager) {
        try {
            Field requestField = findField(
                    internalManager.getClass(), "O", "TencentLocationRequest");
            Object request = requestField == null ? null : requestField.get(internalManager);
            if (request == null) {
                return;
            }
            Method setAllowCache = request.getClass()
                    .getMethod("setAllowCache", boolean.class);
            setAllowCache.invoke(request, false);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to disable Tencent location cache: " + e.getMessage());
        }
    }

    private static void wrapLocationListener(Object internalManager) {
        try {
            Field listenerField = findField(
                    internalManager.getClass(), "D", "TencentLocationListener");
            Object listener = listenerField == null ? null : listenerField.get(internalManager);
            if (listener == null || isWrapped(listener)) {
                return;
            }

            Class<?> listenerInterface = listenerField.getType();
            if (!listenerInterface.isInterface()) {
                return;
            }
            Object wrappedListener = Proxy.newProxyInstance(
                    listenerInterface.getClassLoader(),
                    new Class<?>[]{listenerInterface},
                    new LocationListenerHandler(listener));
            listenerField.set(internalManager, wrappedListener);
            Log.d(TAG, "Wrapped Tencent location listener for virtual location");
        } catch (Throwable e) {
            Log.w(TAG, "Unable to wrap Tencent location listener: " + e.getMessage());
        }
    }

    private static boolean isWrapped(Object listener) {
        if (!Proxy.isProxyClass(listener.getClass())) {
            return false;
        }
        try {
            return Proxy.getInvocationHandler(listener) instanceof LocationListenerHandler;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> type, String preferredName, String typeNamePart) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field preferred = current.getDeclaredField(preferredName);
                preferred.setAccessible(true);
                return preferred;
            } catch (NoSuchFieldException ignored) {
            }
            for (Field field : current.getDeclaredFields()) {
                if (typeNamePart != null && field.getType().getName().contains(typeNamePart)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static final class LocationListenerHandler implements InvocationHandler {
        private final Object delegate;

        private LocationListenerHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onLocationChanged".equals(method.getName())
                    && args != null && args.length > 0 && args[0] != null) {
                applyFakeLocation(args[0]);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static void applyFakeLocation(Object tencentLocation) {
        if (!isEnabled()) {
            return;
        }
        BLocation fakeLocation = BLocationManager.get().getLocation(
                BActivityThread.getUserId(), BActivityThread.getAppPackageName());
        if (fakeLocation == null || fakeLocation.isEmpty()) {
            return;
        }

        Location systemLocation = fakeLocation.convert2SystemLocation();
        try {
            Method updateLocation = tencentLocation.getClass()
                    .getMethod("a", Location.class);
            updateLocation.invoke(tencentLocation, systemLocation);
            Method updateProvider = tencentLocation.getClass()
                    .getMethod("a", String.class);
            updateProvider.invoke(tencentLocation, LocationManager.GPS_PROVIDER);
            updateRawLocation(tencentLocation, systemLocation);
            Log.d(TAG, "Normalized Tencent location callback to virtual location");
        } catch (Throwable e) {
            Log.w(TAG, "Unable to normalize Tencent location callback: " + e.getMessage());
        }
    }

    private static void updateRawLocation(Object tencentLocation, Location systemLocation)
            throws IllegalAccessException {
        Class<?> current = tencentLocation.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Location.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    field.set(tencentLocation, new Location(systemLocation));
                }
            }
            current = current.getSuperclass();
        }
    }
}
