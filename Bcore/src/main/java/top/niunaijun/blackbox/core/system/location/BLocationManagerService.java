package top.niunaijun.blackbox.core.system.location;

import android.location.Location;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import black.android.location.BRILocationListenerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BLocationConfig;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;


public class BLocationManagerService extends IBLocationManagerService.Stub implements ISystemService {
    public static final String TAG = "BLocationManagerService";

    private static final BLocationManagerService sService = new BLocationManagerService();
    private final SparseArray<HashMap<String, BLocationConfig>> mLocationConfigs = new SparseArray<>();
    private final BLocationConfig mGlobalConfig = new BLocationConfig();
    private final Map<IBinder, LocationRecord> mLocationListeners = new ConcurrentHashMap<>();
    private final Executor mThreadPool = Executors.newCachedThreadPool();

    public static BLocationManagerService get() {
        return sService;
    }

    private BLocationConfig getOrCreateConfig(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            HashMap<String, BLocationConfig> pkgs = mLocationConfigs.get(userId);
            if (pkgs == null) {
                pkgs = new HashMap<>();
                mLocationConfigs.put(userId, pkgs);
            }
            BLocationConfig config = pkgs.get(pkg);
            if (config == null) {
                config = new BLocationConfig();
                config.pattern = BLocationManager.CLOSE_MODE;
                pkgs.put(pkg, config);
            }
            return config;
        }
    }

    public int getPattern(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            BLocationConfig config = getOrCreateConfig(userId, pkg);
            return config.pattern;
        }
    }

    @Override
    public void setPattern(int userId, String pkg, int pattern) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).pattern = pattern;
            save();
        }
    }

    @Override
    public void setCell(int userId, String pkg, BCell cell) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).cell = cell;
            save();
        }
    }

    @Override
    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public List<BCell> getNeighboringCell(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            return getOrCreateConfig(userId, pkg).allCell;
        }
    }

    @Override
    public void setGlobalCell(BCell cell) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.cell = cell;
            save();
        }
    }

    @Override
    public void setGlobalAllCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.allCell = cells;
            save();
        }
    }

    @Override
    public void setGlobalNeighboringCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.neighboringCellInfo = cells;
            save();
        }
    }

    @Override
    public List<BCell> getGlobalNeighboringCell() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.neighboringCellInfo;
        }
    }

    @Override
    public BCell getCell(int userId, String pkg) {
        BLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config.cell;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig.cell;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public List<BCell> getAllCell(int userId, String pkg) {
        BLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config.allCell;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig.allCell;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public void setLocation(int userId, String pkg, BLocation location) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).location = location;
            save();
        }
    }

    @Override
    public BLocation getLocation(int userId, String pkg) {
        BLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config.location;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig.location;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public void setGlobalLocation(BLocation location) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.location = location;
            save();
        }
    }

    @Override
    public BLocation getGlobalLocation() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.location;
        }
    }

    @Override
    public void requestLocationUpdates(IBinder listener, String packageName, int userId) throws RemoteException {
        if (listener == null || !listener.pingBinder()) {
            return;
        }
        LocationRecord record = new LocationRecord(packageName, userId);
        if (mLocationListeners.putIfAbsent(listener, record) != null) {
            return;
        }
        record.deathRecipient = new DeathRecipient() {
            @Override
            public void binderDied() {
                mLocationListeners.remove(listener, record);
            }
        };
        try {
            listener.linkToDeath(record.deathRecipient, 0);
        } catch (RemoteException e) {
            mLocationListeners.remove(listener, record);
            throw e;
        }
        Slog.d(TAG, "Registered location listener for " + packageName + ", userId=" + userId);
        addTask(listener);
    }

    @Override
    public void removeUpdates(IBinder listener) throws RemoteException {
        if (listener == null) {
            return;
        }
        LocationRecord record = mLocationListeners.remove(listener);
        if (record != null && record.deathRecipient != null && listener.isBinderAlive()) {
            try {
                listener.unlinkToDeath(record.deathRecipient, 0);
            } catch (RuntimeException e) {
                Slog.w(TAG, "Unable to unlink location listener death recipient", e);
            }
        }
        if (record != null) {
            Slog.d(TAG, "Unregistered location listener for " + record.packageName
                    + ", userId=" + record.userId);
        }
    }

    private void addTask(IBinder locationListener) {
        mThreadPool.execute(() -> {
            BLocation lastLocation = null;
            long l = System.currentTimeMillis();
            while (locationListener.pingBinder()) {
                IInterface iInterface = BRILocationListenerStub.get().asInterface(locationListener);
                LocationRecord locationRecord = mLocationListeners.get(locationListener);
                if (locationRecord == null || iInterface == null) {
                    break;
                }
                BLocation location = getLocation(locationRecord.userId, locationRecord.packageName);
                if (location == null) {
                    if (!sleepForNextDispatch()) {
                        break;
                    }
                    continue;
                }
                if (location.equals(lastLocation) && (System.currentTimeMillis() - l) < 3000) {
                    if (!sleepForNextDispatch()) {
                        break;
                    }
                    continue;
                }
                lastLocation = location;
                l = System.currentTimeMillis();
                Location systemLocation = location.convert2SystemLocation();
                BlackBoxCore.get().getHandler().post(() -> dispatchLocationChanged(iInterface, systemLocation));
            }
        });
    }

    private boolean sleepForNextDispatch() {
        try {
            Thread.sleep(1000);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void dispatchLocationChanged(IInterface listener, Location location) {
        if (listener == null || location == null) {
            return;
        }
        try {
            if (invokeLocationChanged(listener, location, true)) {
                return;
            }
            invokeLocationChanged(listener, location, false);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to dispatch fake location: " + e.getMessage(), e);
        }
    }

    private boolean invokeLocationChanged(IInterface listener, Location location, boolean preferListSignature) throws Exception {
        Method fallback = null;
        for (Method method : listener.getClass().getMethods()) {
            if (!"onLocationChanged".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && Location.class.isAssignableFrom(parameterTypes[0])) {
                if (!preferListSignature) {
                    method.invoke(listener, location);
                    return true;
                }
                fallback = method;
            } else if (parameterTypes.length >= 1 && List.class.isAssignableFrom(parameterTypes[0])) {
                if (preferListSignature) {
                    Object[] args = buildLocationListArgs(parameterTypes, location);
                    method.invoke(listener, args);
                    return true;
                }
                fallback = method;
            }
        }
        if (fallback != null) {
            Class<?>[] parameterTypes = fallback.getParameterTypes();
            if (parameterTypes.length == 1) {
                fallback.invoke(listener, location);
            } else {
                fallback.invoke(listener, buildLocationListArgs(parameterTypes, location));
            }
            return true;
        }
        return false;
    }

    private Object[] buildLocationListArgs(Class<?>[] parameterTypes, Location location) {
        Object[] args = new Object[parameterTypes.length];
        args[0] = Collections.singletonList(location);
        for (int i = 1; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
        }
        return args;
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
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

    public void save() {
        synchronized (mGlobalConfig) {
            synchronized (mLocationConfigs) {
                Parcel parcel = Parcel.obtain();
                AtomicFile atomicFile = new AtomicFile(BEnvironment.getFakeLocationConf());
                FileOutputStream fileOutputStream = null;
                try {
                    mGlobalConfig.writeToParcel(parcel, 0);

                    parcel.writeInt(mLocationConfigs.size());
                    for (int i = 0; i < mLocationConfigs.size(); i++) {
                        int tmpUserId = mLocationConfigs.keyAt(i);
                        HashMap<String, BLocationConfig> configArrayMap = mLocationConfigs.valueAt(i);
                        parcel.writeInt(tmpUserId);
                        parcel.writeMap(configArrayMap);
                    }
                    parcel.setDataPosition(0);
                    fileOutputStream = atomicFile.startWrite();
                    FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                    atomicFile.finishWrite(fileOutputStream);
                } catch (Throwable e) {
                    e.printStackTrace();
                    atomicFile.failWrite(fileOutputStream);
                } finally {
                    parcel.recycle();
                    CloseUtils.close(fileOutputStream);
                }
            }
        }
    }

    public void loadConfig() {
        Parcel parcel = Parcel.obtain();
        InputStream is = null;
        try {
            File fakeLocationConf = BEnvironment.getFakeLocationConf();
            if (!fakeLocationConf.exists()) {
                return;
            }
            is = new FileInputStream(BEnvironment.getFakeLocationConf());
            byte[] bytes = FileUtils.toByteArray(is);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            synchronized (mGlobalConfig) {
                mGlobalConfig.refresh(parcel);
            }

            synchronized (mLocationConfigs) {
                mLocationConfigs.clear();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    int userId = parcel.readInt();
                    HashMap<String, BLocationConfig> configArrayMap = parcel.readHashMap(BLocationConfig.class.getClassLoader());
                    mLocationConfigs.put(userId, configArrayMap);
                    Slog.d(TAG, "load userId: " + userId + ", config: " + configArrayMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Slog.d(TAG, "bad config");
            FileUtils.deleteDir(BEnvironment.getFakeLocationConf());
        } finally {
            parcel.recycle();
            CloseUtils.close(is);
        }
    }

    @Override
    public void systemReady() {
        loadConfig();
        for (IBinder iBinder : mLocationListeners.keySet()) {
            addTask(iBinder);
        }
    }
}
