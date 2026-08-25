package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import black.android.location.BRILocationManagerStub;
import black.android.location.provider.BRProviderProperties;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class ILocationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ILocationManagerProxy";
    private static final String FUSED_PROVIDER = "fused";
    private static final List<String> FAKE_PROVIDERS = Arrays.asList(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            FUSED_PROVIDER
    );

    public ILocationManagerProxy() {
        super(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILocationManagerStub.get().asInterface(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return BRServiceManager.get().getService(Context.LOCATION_SERVICE) != this;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        MethodParameterUtils.replaceFirstAppPkg(args);
        
        
        String packageName = BActivityThread.getAppPackageName();
        if (packageName != null && packageName.equals("com.google.android.gms")) {
            
            if (method.getName().equals("getLastLocation") || 
                method.getName().equals("getLastKnownLocation") ||
                method.getName().equals("requestLocationUpdates")) {
                Log.w(TAG, "Blocking location request from Google Play Services to prevent crash");
                return null;
            }
        }
        
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("registerGnssStatusCallback")
    public static class RegisterGnssStatusCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            return true;
        }
    }

    @ProxyMethod("addGpsStatusListener")
    public static class AddGpsStatusListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ProxyMethod("addNmeaListener")
    public static class AddNmeaListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerGnssNmeaCallback")
    public static class RegisterGnssNmeaCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerGnssMeasurementsCallback")
    public static class RegisterGnssMeasurementsCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerGnssNavigationMessageCallback")
    public static class RegisterGnssNavigationMessageCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getLastLocation")
    public static class GetLastLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Location fakeLocation = getFakeSystemLocation(args);
            if (fakeLocation != null) {
                Log.d(TAG, "getLastLocation returns fake location for " + BActivityThread.getAppPackageName());
                return fakeLocation;
            }
            
            
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied, returning null for getLastLocation");
                    return null;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getLastKnownLocation")
    public static class GetLastKnownLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Location fakeLocation = getFakeSystemLocation(args);
            if (fakeLocation != null) {
                Log.d(TAG, "getLastKnownLocation returns fake location for " + BActivityThread.getAppPackageName());
                return fakeLocation;
            }
            
            
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied, returning null for getLastKnownLocation");
                    return null;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("requestLocationUpdates")
    public static class RequestLocationUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Location fakeLocation = getFakeSystemLocation(args);
            if (fakeLocation != null) {
                IInterface listener = findIInterface(args, "ILocationListener");
                if (listener != null) {
                    Log.d(TAG, "requestLocationUpdates hooked for " + BActivityThread.getAppPackageName());
                    BLocationManager.get().requestLocationUpdates(listener.asBinder());
                    dispatchLocationCallback(listener, fakeLocation);
                    return defaultReturn(method);
                }
            }
            
            
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied for requestLocationUpdates, returning 0");
                    return 0;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("registerLocationListener")
    public static class RegisterLocationListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Location fakeLocation = getFakeSystemLocation(args);
            if (fakeLocation != null) {
                IInterface listener = findIInterface(args, "ILocationListener");
                if (listener != null) {
                    Log.d(TAG, "registerLocationListener hooked for " + BActivityThread.getAppPackageName());
                    BLocationManager.get().requestLocationUpdates(listener.asBinder());
                    dispatchLocationCallback(listener, fakeLocation);
                    return defaultReturn(method);
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCurrentLocation")
    public static class GetCurrentLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Location fakeLocation = getFakeSystemLocation(args);
            if (fakeLocation != null) {
                IInterface callback = findIInterface(args, "ILocationCallback");
                if (callback != null) {
                    Log.d(TAG, "getCurrentLocation hooked for " + BActivityThread.getAppPackageName());
                    dispatchLocationCallback(callback, fakeLocation);
                }
                return defaultReturn(method);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeUpdates")
    public static class RemoveUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface listener = findIInterface(args, "ILocationListener");
            if (listener != null) {
                BLocationManager.get().removeUpdates(listener.asBinder());
                return defaultReturn(method);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("unregisterLocationListener")
    public static class UnregisterLocationListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface listener = findIInterface(args, "ILocationListener");
            if (listener != null) {
                BLocationManager.get().removeUpdates(listener.asBinder());
                return defaultReturn(method);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getProviderProperties")
    public static class GetProviderProperties extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object providerProperties = method.invoke(who, args);
            if (providerProperties != null && getFakeSystemLocation(args) != null) {
                BRProviderProperties.get(providerProperties)._set_mHasNetworkRequirement(false);
                if (BLocationManager.get().getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) == null) {
                    BRProviderProperties.get(providerProperties)._set_mHasCellRequirement(false);
                }
            }
            return providerProperties;
        }
    }

    @ProxyMethod("removeGpsStatusListener")
    public static class RemoveGpsStatusListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            return 0;
        }
    }

    @ProxyMethod("getBestProvider")
    public static class GetBestProvider extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return LocationManager.GPS_PROVIDER;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAllProviders")
    public static class GetAllProviders extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return FAKE_PROVIDERS;
        }
    }

    @ProxyMethod("getProviders")
    public static class GetProviders extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return FAKE_PROVIDERS;
        }
    }

    @ProxyMethod("hasProvider")
    public static class HasProvider extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return FAKE_PROVIDERS.contains(args[0]);
            }
            return true;
        }
    }

    @ProxyMethod("isProviderEnabledForUser")
    public static class isProviderEnabledForUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String provider = (String) args[0];
            return FAKE_PROVIDERS.contains(provider);
        }
    }

    @ProxyMethod("isProviderEnabled")
    public static class IsProviderEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String provider = (String) args[0];
            return FAKE_PROVIDERS.contains(provider);
        }
    }

    @ProxyMethod("isLocationEnabledForUser")
    public static class IsLocationEnabledForUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ProxyMethod("isLocationEnabled")
    public static class IsLocationEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ProxyMethod("setExtraLocationControllerPackageEnabled")
    public static class setExtraLocationControllerPackageEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("sendExtraCommand")
    public static class SendExtraCommand extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getGnssHardwareModelName")
    public static class GetGnssHardwareModelName extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return "BlackBox GNSS";
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getGnssYearOfHardware")
    public static class GetGnssYearOfHardware extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (getFakeSystemLocation(args) != null) {
                return 2024;
            }
            return method.invoke(who, args);
        }
    }

    private static Location getFakeSystemLocation(Object[] args) {
        BLocation location = getFakeLocation();
        if (location == null || location.isEmpty()) {
            return null;
        }
        return location.convert2SystemLocation(findProvider(args));
    }

    private static BLocation getFakeLocation() {
        return BLocationManager.get().getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
    }

    private static String findProvider(Object[] args) {
        if (args == null) {
            return LocationManager.GPS_PROVIDER;
        }
        for (Object arg : args) {
            if (arg instanceof String && FAKE_PROVIDERS.contains(arg)) {
                return (String) arg;
            }
        }
        return LocationManager.GPS_PROVIDER;
    }

    private static IInterface findIInterface(Object[] args, String namePart) {
        if (args == null) {
            return null;
        }
        IInterface fallback = null;
        for (Object arg : args) {
            if (arg instanceof IInterface) {
                IInterface iInterface = (IInterface) arg;
                if (fallback == null) {
                    fallback = iInterface;
                }
                if (matchesName(iInterface, namePart)) {
                    return iInterface;
                }
            } else if (arg instanceof IBinder) {
                IInterface localInterface = ((IBinder) arg).queryLocalInterface(null);
                if (localInterface != null) {
                    if (fallback == null) {
                        fallback = localInterface;
                    }
                    if (matchesName(localInterface, namePart)) {
                        return localInterface;
                    }
                }
            }
        }
        return fallback;
    }

    private static boolean matchesName(IInterface iInterface, String namePart) {
        if (iInterface == null || namePart == null) {
            return false;
        }
        Class<?> clazz = iInterface.getClass();
        if (clazz.getName().contains(namePart)) {
            return true;
        }
        for (Class<?> anInterface : clazz.getInterfaces()) {
            if (anInterface.getName().contains(namePart)) {
                return true;
            }
        }
        return false;
    }

    private static void dispatchLocationCallback(IInterface callback, Location location) {
        try {
            if (invokeLocationMethod(callback, "onLocation", location)) {
                return;
            }
            invokeLocationMethod(callback, "onLocationChanged", location);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to dispatch current fake location", e);
        }
    }

    private static boolean invokeLocationMethod(IInterface receiver, String methodName, Location location) throws Exception {
        for (Method method : receiver.getClass().getMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && Location.class.isAssignableFrom(parameterTypes[0])) {
                method.invoke(receiver, location);
                return true;
            }
            if (parameterTypes.length >= 1 && List.class.isAssignableFrom(parameterTypes[0])) {
                Object[] args = new Object[parameterTypes.length];
                args[0] = Collections.singletonList(location);
                for (int i = 1; i < parameterTypes.length; i++) {
                    args[i] = defaultValue(parameterTypes[i]);
                }
                method.invoke(receiver, args);
                return true;
            }
        }
        return false;
    }

    private static Object defaultReturn(Method method) {
        return defaultValue(method.getReturnType());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
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
