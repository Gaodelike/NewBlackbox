package top.niunaijun.blackbox.fake.delegate;

import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import black.android.content.BRIIntentReceiver;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.proxy.record.ProxyBroadcastRecord;
import top.niunaijun.blackbox.utils.Slog;


public class InnerReceiverDelegate extends IIntentReceiver.Stub {
    public static final String TAG = "InnerReceiverDelegate";

    private static final Map<IBinder, InnerReceiverDelegate> sInnerReceiverDelegate = new HashMap<>();
    private final WeakReference<IIntentReceiver> mIntentReceiver;

    private InnerReceiverDelegate(IIntentReceiver iIntentReceiver) {
        this.mIntentReceiver = new WeakReference<>(iIntentReceiver);
    }

    public static InnerReceiverDelegate getDelegate(IBinder iBinder) {
        return sInnerReceiverDelegate.get(iBinder);
    }

    public static IIntentReceiver createProxy(IIntentReceiver base) {
        if (base instanceof InnerReceiverDelegate) {
            return base;
        }
        final IBinder iBinder = base.asBinder();
        InnerReceiverDelegate delegate = sInnerReceiverDelegate.get(iBinder);
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sInnerReceiverDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new InnerReceiverDelegate(base);
            sInnerReceiverDelegate.put(iBinder, delegate);
        }
        return delegate;
    }

    @Override
    public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered, boolean sticky, int sendingUser) throws RemoteException {
        intent.setExtrasClassLoader(BlackBoxCore.getApplication().getClassLoader());
        ProxyBroadcastRecord proxyBroadcastRecord = ProxyBroadcastRecord.create(intent);
        Intent perIntent;
        if (proxyBroadcastRecord.mIntent != null) {
            proxyBroadcastRecord.mIntent.setExtrasClassLoader(BlackBoxCore.getApplication().getClassLoader());
            perIntent = proxyBroadcastRecord.mIntent;
        } else {
            perIntent = intent;
        }
        IIntentReceiver iIntentReceiver = mIntentReceiver.get();
        if (iIntentReceiver != null) {
            if (!performReceiveWithDeliveryMetadata(iIntentReceiver, perIntent, resultCode,
                    data, extras, ordered, sticky, sendingUser)) {
                BRIIntentReceiver.get(iIntentReceiver).performReceive(perIntent, resultCode,
                        data, extras, ordered, sticky, sendingUser);
            }
        }
    }

    private boolean performReceiveWithDeliveryMetadata(IIntentReceiver receiver, Intent intent,
                                                       int resultCode, String data, Bundle extras,
                                                       boolean ordered, boolean sticky,
                                                       int sendingUser) throws RemoteException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        try {
            Method method = receiver.getClass().getMethod("performReceive",
                    Intent.class, int.class, String.class, Bundle.class,
                    boolean.class, boolean.class, boolean.class, int.class,
                    int.class, String.class);
            method.setAccessible(true);

            boolean assumeDelivered = !ordered;
            String sendingPackage = intent == null ? null : intent.getPackage();
            int sendingUid = sendingPackage == null ? -1 : Process.myUid();
            method.invoke(receiver, intent, resultCode, data, extras, ordered, sticky,
                    assumeDelivered, sendingUser, sendingUid, sendingPackage);

            if (intent != null && "com.tencent.xweb.update".equals(intent.getAction())) {
                Slog.i(TAG, "Delivered XWeb update with modern receiver metadata");
            }
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            Slog.w(TAG, "Modern receiver delivery failed", cause);
            return false;
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to use modern receiver delivery", e);
            return false;
        }
    }
}
