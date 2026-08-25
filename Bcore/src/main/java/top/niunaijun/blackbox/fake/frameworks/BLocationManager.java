package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BLocation;


public class BLocationManager extends BlackManager<IBLocationManagerService> {
    private static final String TAG = "BLocationManager";
    private static final BLocationManager sLocationManager = new BLocationManager();
    private final Map<String, Integer> mPatternCache = new ConcurrentHashMap<>();
    private final Map<String, BLocation> mLocationCache = new ConcurrentHashMap<>();

    public static final int CLOSE_MODE = 0;
    public static final int GLOBAL_MODE = 1;
    public static final int OWN_MODE = 2;

    public static BLocationManager get() {
        return sLocationManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.LOCATION_MANAGER;
    }

    public static boolean isFakeLocationEnable() {
        return get().getPattern(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) != CLOSE_MODE;
    }

    public static void disableFakeLocation(int userId,String pkg){
        get().setPattern(userId,pkg,CLOSE_MODE);
    }

    public void setPattern(int userId, String pkg, int pattern) {
        String cacheKey = cacheKey(userId, pkg);
        mPatternCache.put(cacheKey, pattern);
        if (pattern == CLOSE_MODE) {
            mLocationCache.remove(cacheKey);
        }
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                service.setPattern(userId, pkg, pattern);
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to update location pattern", e);
        }
    }

    public int getPattern(int userId, String pkg) {
        String cacheKey = cacheKey(userId, pkg);
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                int pattern = service.getPattern(userId, pkg);
                mPatternCache.put(cacheKey, pattern);
                if (pattern == CLOSE_MODE) {
                    mLocationCache.remove(cacheKey);
                }
                return pattern;
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to read location pattern, using cached value", e);
        }
        Integer cachedPattern = mPatternCache.get(cacheKey);
        return cachedPattern == null ? CLOSE_MODE : cachedPattern;
    }

    public void setCell(int userId, String pkg, BCell cell) {
        try {
            getService().setCell(userId, pkg, cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        try {
            getService().setAllCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<BCell> getNeighboringCell(int userId, String pkg) {
        try {
            return getService().getNeighboringCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BCell> getGlobalNeighboringCell() {
        try {
            return getService().getGlobalNeighboringCell();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        try {
            getService().setNeighboringCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalCell(BCell cell) {
        try {
            getService().setGlobalCell(cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalAllCell(List<BCell> cells) {
        try {
            getService().setGlobalAllCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalNeighboringCell(List<BCell> cells) {
        try {
            getService().setGlobalNeighboringCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BCell getCell(int userId, String pkg) {
        try {
            return getService().getCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BCell> getAllCell(int userId, String pkg) {
        try {
            return getService().getAllCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void setLocation(int userId, String pkg, BLocation location) {
        String cacheKey = cacheKey(userId, pkg);
        if (location == null) {
            mLocationCache.remove(cacheKey);
        } else {
            mLocationCache.put(cacheKey, location);
        }
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                service.setLocation(userId, pkg, location);
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to update fake location", e);
        }
    }

    public BLocation getLocation(int userId, String pkg) {
        String cacheKey = cacheKey(userId, pkg);
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                BLocation location = service.getLocation(userId, pkg);
                if (location == null) {
                    mLocationCache.remove(cacheKey);
                } else {
                    mLocationCache.put(cacheKey, location);
                }
                return location;
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to read fake location, using cached value", e);
        }
        return mLocationCache.get(cacheKey);
    }

    public void setGlobalLocation(BLocation location) {
        try {
            getService().setGlobalLocation(location);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BLocation getGlobalLocation() {
        try {
            return getService().getGlobalLocation();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void requestLocationUpdates(IBinder listener) {
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                service.requestLocationUpdates(listener, BActivityThread.getAppPackageName(), BActivityThread.getUserId());
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to register fake location listener", e);
        }
    }

    public void removeUpdates(IBinder listener) {
        try {
            IBLocationManagerService service = getService();
            if (service != null) {
                service.removeUpdates(listener);
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Unable to unregister fake location listener", e);
        }
    }

    private String cacheKey(int userId, String pkg) {
        return userId + ":" + String.valueOf(pkg);
    }
}
