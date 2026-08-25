package top.niunaijun.blackbox.core.system.location;

import android.os.IBinder;


public class LocationRecord {
    public String packageName;
    public int userId;
    public IBinder.DeathRecipient deathRecipient;

    public LocationRecord(String packageName, int userId) {
        this.packageName = packageName;
        this.userId = userId;
    }
}
