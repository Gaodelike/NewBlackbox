package top.niunaijun.blackbox.fake.service;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import black.android.app.ActivityThreadActivityClientRecordContext;
import black.android.app.BRActivityClient;
import black.android.app.BRActivityClientActivityClientControllerSingleton;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import black.android.app.BRActivityThreadCreateServiceData;
import black.android.app.BRActivityThreadH;
import black.android.app.BRIActivityManager;
import black.android.app.servertransaction.BRClientTransaction;
import black.android.app.servertransaction.BRLaunchActivityItem;
import black.android.app.servertransaction.LaunchActivityItemContext;
import black.android.os.BRHandler;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.app.LauncherActivity;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.CameraCompat;



public class HCallbackProxy implements IInjectHook, Handler.Callback {
    public static final String TAG = "HCallbackStub";
    private static final int LAUNCH_NOT_HANDLED = 0;
    private static final int LAUNCH_RETRY = 1;
    private static final int LAUNCH_DEFERRED = 2;
    private static final long PROCESS_RECOVERY_TIMEOUT_MS = 4000L;

    private Handler.Callback mOtherCallback;
    private final AtomicBoolean mBeing = new AtomicBoolean(false);
    private final AtomicBoolean mRecoveryInProgress = new AtomicBoolean(false);
    private final AtomicInteger mRecoveryGeneration = new AtomicInteger();
    private final Queue<PendingLaunch> mPendingLaunches = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mOrphanRecoveryInProgress = new AtomicBoolean(false);
    private final AtomicInteger mOrphanRecoveryGeneration = new AtomicInteger();

    private Handler.Callback getHCallback() {
        return BRHandler.get(getH()).mCallback();
    }

    private Handler getH() {
        Object currentActivityThread = BlackBoxCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mH();
    }

    @Override
    public void injectHook() {
        mOtherCallback = getHCallback();
        if (mOtherCallback != null && (mOtherCallback == this || mOtherCallback.getClass().getName().equals(this.getClass().getName()))) {
            mOtherCallback = null;
        }
        BRHandler.get(getH())._set_mCallback(this);
    }

    @Override
    public boolean isBadEnv() {
        Handler.Callback hCallback = getHCallback();
        return hCallback != null && hCallback != this;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (!mBeing.getAndSet(true)) {
            try {
                if (BuildCompat.isPie()) {
                    if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()) {
                        int launchResult = handleLaunchActivity(msg.obj, msg);
                        if (launchResult == LAUNCH_RETRY) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                        if (launchResult == LAUNCH_DEFERRED) {
                            return true;
                        }
                    }
                } else {
                    if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY()) {
                        int launchResult = handleLaunchActivity(msg.obj, msg);
                        if (launchResult == LAUNCH_RETRY) {
                            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                            return true;
                        }
                        if (launchResult == LAUNCH_DEFERRED) {
                            return true;
                        }
                    }
                }
                if (msg.what == BRActivityThreadH.get().CREATE_SERVICE()) {
                    return handleCreateService(msg.obj);
                }
                if (mOtherCallback != null) {
                    return mOtherCallback.handleMessage(msg);
                }
                return false;
            } finally {
                mBeing.set(false);
            }
        }
        return false;
    }

    private Object getLaunchActivityItem(Object clientTransaction) {
        List<Object> mActivityCallbacks = BRClientTransaction.get(clientTransaction).mActivityCallbacks();

        if (mActivityCallbacks == null) {
            Slog.e(TAG, "mActivityCallbacks is null for clientTransaction: " + clientTransaction);
            return null;
        }

        for (Object obj : mActivityCallbacks) {
            if (BRLaunchActivityItem.getRealClass().getName().equals(obj.getClass().getCanonicalName())) {
                return obj;
            }
        }
        return null;
    }

    private int handleLaunchActivity(Object client, Message originalMessage) {
        Object r;
        if (BuildCompat.isPie()) {
            
            r = getLaunchActivityItem(client);
        } else {
            
            r = client;
        }
        if (r == null) {
            if (BuildCompat.isPie() && BActivityThread.getAppConfig() == null) {
                IBinder token = BRClientTransaction.get(client).mActivityToken();
                if (token != null) {
                    deferOrphanedTaskRecovery(token);
                    return LAUNCH_DEFERRED;
                }
            }
            return LAUNCH_NOT_HANDLED;
        }

        Intent intent;
        IBinder token;
        if (BuildCompat.isPie()) {
            intent = BRLaunchActivityItem.get(r).mIntent();
            token = BRClientTransaction.get(client).mActivityToken();
        } else {
            ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(r);
            intent = clientRecordContext.intent();
            token = clientRecordContext.token();
        }

        if (intent == null)
            return LAUNCH_NOT_HANDLED;

        ProxyActivityRecord stubRecord = ProxyActivityRecord.create(intent);
        ActivityInfo activityInfo = stubRecord.mActivityInfo;
        if (activityInfo != null) {
            CameraCompat.enterHostCameraPackage(activityInfo.name, token);

            if (BActivityThread.getAppConfig() == null) {
                deferProcessRecovery(originalMessage, token, activityInfo, stubRecord.mUserId);
                return LAUNCH_DEFERRED;
            }
            
            if (!BActivityThread.currentActivityThread().isInit()) {
                BActivityThread.currentActivityThread().bindApplication(activityInfo.packageName,
                        activityInfo.processName);
                return LAUNCH_RETRY;
            }

            int taskId = BRIActivityManager.get(BRActivityManagerNative.get().getDefault()).getTaskForActivity(token, false);
            BlackBoxCore.getBActivityManager().onActivityCreated(taskId, token, stubRecord.mActivityRecord);

            if (stubRecord.mTarget != null && BActivityThread.getApplication() != null) {
                stubRecord.mTarget.setExtrasClassLoader(BActivityThread.getApplication().getClassLoader());
            }

            if(BuildCompat.isTiramisu()){
                LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                launchActivityItemContext._set_mInfo(activityInfo);
            } else if (BuildCompat.isS()) {
                Object record = BRActivityThread.get(BlackBoxCore.mainThread()).getLaunchingActivity(token);
                ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(record);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
                clientRecordContext._set_packageInfo(BActivityThread.currentActivityThread().getPackageInfo());

                checkActivityClient();
            } else if (BuildCompat.isPie()) {
                LaunchActivityItemContext launchActivityItemContext = BRLaunchActivityItem.get(r);
                launchActivityItemContext._set_mIntent(stubRecord.mTarget);
                launchActivityItemContext._set_mInfo(activityInfo);
            } else {
                ActivityThreadActivityClientRecordContext clientRecordContext = BRActivityThreadActivityClientRecord.get(r);
                clientRecordContext._set_intent(stubRecord.mTarget);
                clientRecordContext._set_activityInfo(activityInfo);
            }
        }
        return LAUNCH_NOT_HANDLED;
    }

    private void deferProcessRecovery(Message message, IBinder token, ActivityInfo activityInfo, int userId) {
        PendingLaunch pendingLaunch = new PendingLaunch(
                Message.obtain(message), token, activityInfo.packageName, activityInfo.processName, userId);
        mPendingLaunches.offer(pendingLaunch);
        if (!mRecoveryInProgress.compareAndSet(false, true)) {
            return;
        }

        final int generation = mRecoveryGeneration.incrementAndGet();
        final Handler handler = getH();
        final Runnable timeout = () -> completeProcessRecovery(generation, null);
        Slog.d(TAG, "Deferring launch while recovering process: " + pendingLaunch.processName);
        handler.postDelayed(timeout, PROCESS_RECOVERY_TIMEOUT_MS);

        Thread recoveryThread = new Thread(() -> {
            AppConfig appConfig = null;
            try {
                appConfig = BlackBoxCore.getBActivityManager().restartProcess(
                        pendingLaunch.packageName,
                        pendingLaunch.processName,
                        pendingLaunch.userId,
                        BActivityThread.currentActivityThread().asBinder());
            } catch (Throwable e) {
                Slog.e(TAG, "Unable to recover process: " + pendingLaunch.processName, e);
            }
            final AppConfig recoveredConfig = appConfig;
            handler.post(() -> {
                handler.removeCallbacks(timeout);
                completeProcessRecovery(generation, recoveredConfig);
            });
        }, "BlackBoxProcessRecovery");
        recoveryThread.start();
    }

    private void completeProcessRecovery(int generation, AppConfig appConfig) {
        if (generation != mRecoveryGeneration.get()
                || !mRecoveryInProgress.compareAndSet(true, false)) {
            return;
        }

        List<PendingLaunch> pendingLaunches = new ArrayList<>();
        PendingLaunch pendingLaunch;
        while ((pendingLaunch = mPendingLaunches.poll()) != null) {
            pendingLaunches.add(pendingLaunch);
        }

        if (appConfig != null) {
            try {
                BActivityThread.currentActivityThread().initProcess(appConfig);
                Slog.d(TAG, "Process recovery completed: " + appConfig.processName);
                for (int i = pendingLaunches.size() - 1; i >= 0; i--) {
                    getH().sendMessageAtFrontOfQueue(pendingLaunches.get(i).message);
                }
                return;
            } catch (Throwable e) {
                Slog.e(TAG, "Unable to apply recovered process config", e);
            }
        }

        Slog.w(TAG, "Process recovery timed out or failed; starting a fresh task");
        PendingLaunch fallback = pendingLaunches.isEmpty() ? null : pendingLaunches.get(0);
        for (PendingLaunch launch : pendingLaunches) {
            if (launch.token != null) {
                try {
                    ActivityManagerCompat.finishActivity(launch.token, Activity.RESULT_CANCELED, null);
                } catch (Throwable e) {
                    Slog.w(TAG, "Unable to finish stale proxy activity: " + e.getMessage());
                }
            }
        }
        if (fallback != null) {
            Intent launchIntent = BlackBoxCore.getBPackageManager()
                    .getLaunchIntentForPackage(fallback.packageName, fallback.userId);
            if (launchIntent != null) {
                if (launchIntent.getPackage() == null) {
                    launchIntent.setPackage(fallback.packageName);
                }
                LauncherActivity.launch(launchIntent, fallback.userId);
            }
        }
    }

    private static final class PendingLaunch {
        final Message message;
        final IBinder token;
        final String packageName;
        final String processName;
        final int userId;

        PendingLaunch(Message message, IBinder token, String packageName, String processName, int userId) {
            this.message = message;
            this.token = token;
            this.packageName = packageName;
            this.processName = processName;
            this.userId = userId;
        }
    }

    private void deferOrphanedTaskRecovery(IBinder token) {
        if (!mOrphanRecoveryInProgress.compareAndSet(false, true)) {
            return;
        }

        final int generation = mOrphanRecoveryGeneration.incrementAndGet();
        final Handler handler = getH();
        final Runnable timeout = () -> completeOrphanedTaskRecovery(generation, token, null);
        Slog.w(TAG, "Deferring orphaned activity transaction until a fresh task is ready");
        handler.postDelayed(timeout, PROCESS_RECOVERY_TIMEOUT_MS);

        Thread recoveryThread = new Thread(() -> {
            Bundle recoveryInfo = null;
            try {
                recoveryInfo = BlackBoxCore.getBActivityManager().getActivityRecoveryInfo(token);
            } catch (Throwable e) {
                Slog.e(TAG, "Unable to query orphaned activity recovery info", e);
            }
            final Bundle result = recoveryInfo;
            handler.post(() -> {
                handler.removeCallbacks(timeout);
                completeOrphanedTaskRecovery(generation, token, result);
            });
        }, "BlackBoxTaskRecovery");
        recoveryThread.start();
    }

    private void completeOrphanedTaskRecovery(int generation, IBinder token, Bundle recoveryInfo) {
        if (generation != mOrphanRecoveryGeneration.get()
                || !mOrphanRecoveryInProgress.compareAndSet(true, false)) {
            return;
        }

        if (recoveryInfo != null) {
            Intent target = recoveryInfo.getParcelable("activity_recovery_intent");
            int userId = recoveryInfo.getInt("activity_recovery_user_id", 0);
            if (target != null) {
                if (target.getPackage() == null && target.getComponent() != null) {
                    target.setPackage(target.getComponent().getPackageName());
                }
                Slog.d(TAG, "Starting a fresh task after orphaned transaction recovery");
                finishOrphanedActivityAsync(token);
                LauncherActivity.launch(target, userId);
                return;
            }
        }

        finishOrphanedActivityAsync(token);
    }

    private void finishOrphanedActivityAsync(IBinder token) {
        Thread cleanupThread = new Thread(() -> {
            try {
                ActivityManagerCompat.finishActivity(token, Activity.RESULT_CANCELED, null);
            } catch (Throwable e) {
                Slog.w(TAG, "Unable to finish orphaned activity: " + e.getMessage());
            }
        }, "BlackBoxTaskCleanup");
        cleanupThread.start();
    }

    private boolean handleCreateService(Object data) {
        if (BActivityThread.getAppConfig() != null) {
            String appPackageName = BActivityThread.getAppPackageName();
            assert appPackageName != null;

            ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
            if (!serviceInfo.name.equals(ProxyManifest.getProxyService(BActivityThread.getAppPid()))
                    && !serviceInfo.name.equals(ProxyManifest.getProxyJobService(BActivityThread.getAppPid()))) {
                Slog.d(TAG, "handleCreateService: " + data);
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
                BlackBoxCore.getBActivityManager().startService(intent, null, false, BActivityThread.getUserId());
                return true;
            }
        }
        return false;
    }

    private void checkActivityClient() {
        try {
            Object activityClientController = BRActivityClient.get().getActivityClientController();
            if (!(activityClientController instanceof Proxy)) {
                IActivityClientProxy iActivityClientProxy = new IActivityClientProxy(activityClientController);
                iActivityClientProxy.onlyProxy(true);
                iActivityClientProxy.injectHook();
                Object instance = BRActivityClient.get().getInstance();
                Object o = BRActivityClient.get(instance).INTERFACE_SINGLETON();
                BRActivityClientActivityClientControllerSingleton.get(o)._set_mKnownInstance(iActivityClientProxy.getProxyInvocation());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
