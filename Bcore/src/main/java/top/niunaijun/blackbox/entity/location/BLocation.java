
package top.niunaijun.blackbox.entity.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;


public class BLocation implements Parcelable {

    private double mLatitude = 0.0;
    private double mLongitude = 0.0;
    private double mAltitude = 0.0f;
    private float mSpeed = 0.0f;
    private float mBearing = 0.0f;
    private float mAccuracy = 0.0f;





    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(this.mLatitude);
        dest.writeDouble(this.mLongitude);
        dest.writeDouble(this.mAltitude);
        dest.writeFloat(this.mSpeed);
        dest.writeFloat(this.mBearing);
        dest.writeFloat(this.mAccuracy);
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public BLocation() {
    }

    public BLocation(double latitude, double mLongitude) {
        this.mLatitude = latitude;
        this.mLongitude = mLongitude;
    }

    public BLocation(Parcel in) {
        this.mLatitude = in.readDouble();
        this.mLongitude = in.readDouble();
        this.mAltitude = in.readDouble();
        this.mSpeed = in.readFloat();
        this.mBearing = in.readFloat();
        this.mAccuracy = in.readFloat();
    }

    public boolean isEmpty() {
        return mLatitude == 0 && mLongitude == 0;
    }

    public static final Parcelable.Creator<BLocation> CREATOR = new Parcelable.Creator<BLocation>() {
        @Override
        public BLocation createFromParcel(Parcel source) {
            return new BLocation(source);
        }

        @Override
        public BLocation[] newArray(int size) {
            return new BLocation[size];
        }
    };

    @Override
    public String toString() {
        return "BLocation{" +
                "latitude: " + mLatitude +
                ", longitude: " + mLongitude +
                ", altitude: " + mAltitude +
                ", speed: " + mSpeed +
                ", bearing: " + mBearing +
                ", accuracy: " + mAccuracy +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BLocation)) {
            return false;
        }
        BLocation other = (BLocation) obj;
        return Double.compare(mLatitude, other.mLatitude) == 0
                && Double.compare(mLongitude, other.mLongitude) == 0
                && Double.compare(mAltitude, other.mAltitude) == 0
                && Float.compare(mSpeed, other.mSpeed) == 0
                && Float.compare(mBearing, other.mBearing) == 0
                && Float.compare(mAccuracy, other.mAccuracy) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLatitude, mLongitude, mAltitude, mSpeed, mBearing, mAccuracy);
    }

    public Location convert2SystemLocation() {
        return convert2SystemLocation(LocationManager.GPS_PROVIDER);
    }

    public Location convert2SystemLocation(String provider) {
        if (provider == null || provider.length() == 0) {
            provider = LocationManager.GPS_PROVIDER;
        }
        Location location = new Location(provider);
        location.setLatitude(mLatitude);
        location.setLongitude(mLongitude);
        location.setAltitude(mAltitude);
        location.setSpeed(mSpeed);
        location.setBearing(mBearing);
        location.setAccuracy(mAccuracy > 0 ? mAccuracy : 40f);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            location.setVerticalAccuracyMeters(40f);
            location.setSpeedAccuracyMetersPerSecond(0.1f);
            location.setBearingAccuracyDegrees(1f);
        }
        Bundle extraBundle = new Bundle();
        
        int satelliteCount = 10;
        extraBundle.putInt("satellites", satelliteCount);
        extraBundle.putInt("satellitesvalue", satelliteCount);
        extraBundle.putBoolean("is_mock", false);
        extraBundle.putString("networkLocationType", "wifi");
        location.setExtras(extraBundle);
        return location;
    }
}
