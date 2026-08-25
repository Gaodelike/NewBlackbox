package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Collections;

import black.android.net.wifi.BRIWifiManagerStub;
import black.android.net.wifi.BRWifiInfo;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class IWifiManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IWifiManagerProxy";
    private static final String UNKNOWN_SSID = "<unknown ssid>";
    private static final String UNKNOWN_BSSID = "02:00:00:00:00:00";

    public IWifiManagerProxy() {
        super(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIWifiManagerStub.get().asInterface(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        MethodParameterUtils.replaceLastUid(args);
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("getConnectionInfo")
    public static class GetConnectionInfo extends MethodHook {
        
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            WifiInfo wifiInfo = (WifiInfo) method.invoke(who, args);
            return buildSafeWifiInfo(wifiInfo);
        }

        public static String intIP2StringIP(int ip) {
            return (ip & 0xFF) + "." +
                    ((ip >> 8) & 0xFF) + "." +
                    ((ip >> 16) & 0xFF) + "." +
                    (ip >> 24 & 0xFF);
        }

        public static int ip2Int(String ipString) {
            
            String[] ipSlices = ipString.split("\\.");
            int rs = 0;
            for (int i = 0; i < ipSlices.length; i++) {
                
                int intSlice = Integer.parseInt(ipSlices[i]) << 8 * i;
                
                rs = rs | intSlice;
            }
            return rs;
        }
    }

    @ProxyMethod("getScanResults")
    public static class GetScanResults extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Log.d(TAG, "Hiding Wi-Fi scan results while fake location is enabled");
                return Collections.emptyList();
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("startScan")
    public static class StartScan extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isWifiEnabled")
    public static class IsWifiEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getWifiEnabledState")
    public static class GetWifiEnabledState extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return WifiManager.WIFI_STATE_ENABLED;
            }
            return method.invoke(who, args);
        }
    }

    private static WifiInfo buildSafeWifiInfo(WifiInfo wifiInfo) {
        try {
            if (wifiInfo == null) {
                wifiInfo = (WifiInfo) BRWifiInfo.get()._new();
            }
            if (BLocationManager.isFakeLocationEnable()) {
                BRWifiInfo.get(wifiInfo)._set_mBSSID(UNKNOWN_BSSID);
                BRWifiInfo.get(wifiInfo)._set_mMacAddress(UNKNOWN_BSSID);
                BRWifiInfo.get(wifiInfo)._set_mSSID(UNKNOWN_SSID);
                BRWifiInfo.get(wifiInfo)._set_mWifiSsid(null);
                BRWifiInfo.get(wifiInfo)._set_mNetworkId(-1);
                BRWifiInfo.get(wifiInfo)._set_mRssi(-127);
                BRWifiInfo.get(wifiInfo)._set_mLinkSpeed(-1);
                BRWifiInfo.get(wifiInfo)._set_mFrequency(0);
                BRWifiInfo.get(wifiInfo)._set_mIpAddress(InetAddress.getByName("0.0.0.0"));
                return wifiInfo;
            }
            BRWifiInfo.get(wifiInfo)._set_mBSSID(UNKNOWN_BSSID);
            BRWifiInfo.get(wifiInfo)._set_mMacAddress(UNKNOWN_BSSID);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to build safe WifiInfo", e);
        }
        return wifiInfo;
    }
}
