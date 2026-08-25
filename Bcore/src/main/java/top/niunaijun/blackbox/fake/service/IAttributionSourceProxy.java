package top.niunaijun.blackbox.fake.service;



import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;
import top.niunaijun.blackbox.utils.Slog;


public class IAttributionSourceProxy extends ClassInvocationStub {
    public static final String TAG = "IAttributionSourceProxy";

    public IAttributionSourceProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        
        return null;
    }

    @Override
    protected void inject(Object base, Object proxy) {
        
        Slog.d(TAG, "AttributionSource proxy initialized for UID mismatch prevention");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    
    @ProxyMethod("AttributionSource")
    public static class AttributionSourceConstructor extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                int uid = BlackBoxCore.getHostUid();
                String packageName = BlackBoxCore.getHostPkg();
                
                Slog.d(TAG, "Creating AttributionSource with UID: " + uid + ", package: " + packageName);
                
                
                Object attributionSource = AttributionSourceUtils.createSafeAttributionSource(uid, packageName);
                if (attributionSource != null) {
                    return attributionSource;
                }
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Error creating AttributionSource, using fallback: " + e.getMessage());
                return AttributionSourceUtils.createSafeAttributionSource();
            }
        }
    }

    
    @ProxyMethod("enforceCallingUid")
    public static class EnforceCallingUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                Slog.d(TAG, "Intercepting enforceCallingUid to prevent UID mismatch crashes");
                return null; 
            } catch (Exception e) {
                Slog.w(TAG, "Error in enforceCallingUid hook: " + e.getMessage());
                return null;
            }
        }
    }

    
    @ProxyMethod("enforceCallingUidAndPid")
    public static class EnforceCallingUidAndPid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                Slog.d(TAG, "Intercepting enforceCallingUidAndPid to prevent UID mismatch crashes");
                return null; 
            } catch (Exception e) {
                Slog.w(TAG, "Error in enforceCallingUidAndPid hook: " + e.getMessage());
                return null;
            }
        }
    }

    
    @ProxyMethod("fromParcel")
    public static class FromParcel extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                Object result = method.invoke(who, args);
                if (result != null) {
                    
                    AttributionSourceUtils.fixAttributionSourceUid(result);
                    return result;
                }
                
                
                return AttributionSourceUtils.createSafeAttributionSource();
            } catch (Exception e) {
                Slog.w(TAG, "Error in fromParcel, using fallback: " + e.getMessage());
                return AttributionSourceUtils.createSafeAttributionSource();
            }
        }
    }
}
