package top.niunaijun.blackbox.core.system;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.IBActivityThread;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.notification.BNotificationManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ApplicationThreadCompat;
import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.provider.ProviderCall;


public class BProcessManagerService implements ISystemService {
    public static final String TAG = "BProcessManager";
    private static final long PROCESS_INIT_TIMEOUT_MS = 3000L;
    private static final int PROCESS_INIT_ATTEMPTS = 2;

    public static BProcessManagerService sBProcessManagerService = new BProcessManagerService();
    private final Map<Integer, Map<String, ProcessRecord>> mProcessMap = new HashMap<>();
    private final List<ProcessRecord> mPidsSelfLocked = new ArrayList<>();
    private final Object mProcessLock = new Object();

    public static BProcessManagerService get() {
        return sBProcessManagerService;
    }

    public ProcessRecord startProcessLocked(String packageName, String processName, int userId, int bpid, int callingPid) {
        ApplicationInfo info = BPackageManagerService.get().getApplicationInfo(packageName, 0, userId);
        if (info == null) {
            return null;
        }
        final int appId = BPackageManagerService.get().getAppId(packageName);
        final int buid = BUserHandle.getUid(userId, appId);
        final int callingBUid = getBUidByPidOrPackageName(callingPid, packageName);
        final int requestedBPid = bpid;

        for (int attempt = 0; attempt < PROCESS_INIT_ATTEMPTS; attempt++) {
            Set<Integer> runningBPids = requestedBPid == -1 ? getRunningBPids() : new HashSet<>();
            ProcessRecord app;
            ProcessRecord pendingApp = null;

            synchronized (mProcessLock) {
                Map<String, ProcessRecord> processMap = mProcessMap.get(buid);
                if (processMap == null) {
                    processMap = new HashMap<>();
                    mProcessMap.put(buid, processMap);
                }

                app = processMap.get(processName);
                if (hasInitializedClient(app)) {
                    return app;
                }
                if (app != null && hasLiveBinder(app)) {
                    pendingApp = app;
                } else if (app != null) {
                    removeProcessRecordLocked(app);
                }

                if (pendingApp == null) {
                    int resolvedBPid = requestedBPid;
                    if (resolvedBPid == -1) {
                        resolvedBPid = getUsingBPidL(runningBPids);
                        Slog.d(TAG, "init bUid = " + buid + ", bPid = " + resolvedBPid);
                    }
                    if (resolvedBPid == -1 || isBPidOwnedByAliveProcessLocked(resolvedBPid)) {
                        throw new RuntimeException("No processes available");
                    }

                    app = new ProcessRecord(info, processName);
                    app.uid = Process.myUid();
                    app.bpid = resolvedBPid;
                    app.buid = appId;
                    app.callingBUid = callingBUid;
                    app.userId = userId;
                    processMap.put(processName, app);
                    mPidsSelfLocked.add(app);
                }
            }

            if (pendingApp != null) {
                boolean initialized = pendingApp.initLock.block(PROCESS_INIT_TIMEOUT_MS);
                if (initialized && isProcessAlive(pendingApp)) {
                    return pendingApp;
                }
                Slog.w(TAG, "Timed out waiting for process initialization: " + processName);
                cleanupFailedProcess(pendingApp, false);
                continue;
            }

            boolean initialized = false;
            try {
                initialized = initAppProcessL(app);
            } catch (Throwable e) {
                Slog.e(TAG, "Unable to initialize process: " + processName, e);
            } finally {
                app.pid = getPid(BlackBoxCore.getContext(), ProxyManifest.getProcessName(app.bpid));
                if (!initialized) {
                    app.initLock.open();
                }
            }

            if (!initialized || !isCurrentProcessRecord(app)) {
                cleanupFailedProcess(app, true);
                continue;
            }
            return app;
        }
        return null;
    }

    private Set<Integer> getRunningBPids() {
        ActivityManager manager = (ActivityManager) BlackBoxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
        Set<Integer> usingPs = new HashSet<>();
        if (runningAppProcesses != null) {
            for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
                int i = parseBPid(runningAppProcess.processName);
                if (i >= 0) {
                    usingPs.add(i);
                }
            }
        }
        return usingPs;
    }

    private int getUsingBPidL(Set<Integer> usingPs) {
        for (ProcessRecord record : mPidsSelfLocked) {
            usingPs.add(record.bpid);
        }
        for (int i = 0; i < ProxyManifest.FREE_COUNT; i++) {
            if (usingPs.contains(i)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    public ProcessRecord restartAppProcess(String packageName, String processName, int userId,
                                           IBinder client, int callingPid) {
        if (client == null || !client.isBinderAlive()) {
            return null;
        }

        ProcessRecord callingProcess = findProcessByPid(callingPid);
        if (isProcessAlive(callingProcess)
                && callingProcess.userId == userId
                && packageName.equals(callingProcess.getPackageName())
                && processName.equals(callingProcess.processName)) {
            return callingProcess;
        }

        String stubProcessName;
        try {
            stubProcessName = getProcessName(BlackBoxCore.getContext(), callingPid);
        } catch (Throwable e) {
            Slog.e(TAG, "Unable to resolve recovering process", e);
            return null;
        }
        int bpid = parseBPid(stubProcessName);
        if (bpid < 0) {
            return null;
        }

        ApplicationInfo info = BPackageManagerService.get().getApplicationInfo(packageName, 0, userId);
        if (info == null) {
            return null;
        }
        int appId = BPackageManagerService.get().getAppId(packageName);
        int buid = BUserHandle.getUid(userId, appId);
        ProcessRecord app = new ProcessRecord(info, processName);
        app.uid = Process.myUid();
        app.pid = callingPid;
        app.bpid = bpid;
        app.buid = appId;
        app.callingBUid = appId;
        app.userId = userId;

        ProcessRecord staleRecord = null;
        synchronized (mProcessLock) {
            Map<String, ProcessRecord> processMap = mProcessMap.get(buid);
            if (processMap == null) {
                processMap = new HashMap<>();
                mProcessMap.put(buid, processMap);
            }
            ProcessRecord existing = processMap.get(processName);
            if (hasInitializedClient(existing)) {
                return existing.pid == callingPid ? existing : null;
            }
            if (hasLiveBinder(existing)) {
                return null;
            }
            if (existing != null) {
                staleRecord = existing;
                removeProcessRecordLocked(existing);
                processMap = mProcessMap.get(buid);
                if (processMap == null) {
                    processMap = new HashMap<>();
                    mProcessMap.put(buid, processMap);
                }
            }
            if (isBPidOwnedByAliveProcessLocked(bpid)) {
                return null;
            }
            processMap.put(processName, app);
            mPidsSelfLocked.add(app);
        }

        if (staleRecord != null) {
            staleRecord.initLock.open();
            removeProc(staleRecord);
        }

        if (!attachClientL(app, client)) {
            cleanupFailedProcess(app, false);
            return null;
        }
        createProc(app);
        return isCurrentProcessRecord(app) ? app : null;
    }

    private int parseBPid(String stubProcessName) {
        String prefix;
        if (stubProcessName == null) {
            return -1;
        } else {
            prefix = BlackBoxCore.getHostPkg() + ":p";
        }
        if (stubProcessName.startsWith(prefix)) {
            try {
                return Integer.parseInt(stubProcessName.substring(prefix.length()));
            } catch (NumberFormatException e) {
                
            }
        }
        return -1;
    }

    private boolean initAppProcessL(ProcessRecord record) {
        Log.d(TAG, "initProcess: " + record.processName);
        AppConfig appConfig = record.getClientConfig();
        Bundle bundle = new Bundle();
        bundle.putParcelable(AppConfig.KEY, appConfig);
        Bundle init = ProviderCall.callSafely(record.getProviderAuthority(), "_Black_|_init_process_", null, bundle);
        if (init == null) {
            return false;
        }
        IBinder appThread = BundleCompat.getBinder(init, "_Black_|_client_");
        if (appThread == null || !appThread.isBinderAlive()) {
            return false;
        }
        if (!attachClientL(record, appThread)) {
            return false;
        }

        createProc(record);
        return true;
    }

    private boolean attachClientL(final ProcessRecord app, final IBinder appThread) {
        IBActivityThread activityThread = IBActivityThread.Stub.asInterface(appThread);
        if (activityThread == null) {
            return false;
        }
        try {
            appThread.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    Log.d(TAG, "App Died: " + app.processName);
                    appThread.unlinkToDeath(this, 0);
                    onProcessDie(app);
                }
            }, 0);
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to monitor process: " + app.processName, e);
            return false;
        }
        app.bActivityThread = activityThread;
        try {
            app.appThread = ApplicationThreadCompat.asInterface(activityThread.getActivityThread());
        } catch (RemoteException e) {
            app.bActivityThread = null;
            Slog.e(TAG, "Unable to attach process: " + app.processName, e);
            return false;
        }
        if (app.appThread == null) {
            app.bActivityThread = null;
            return false;
        }
        app.initLock.open();
        return true;
    }

    public boolean isProcessAlive(ProcessRecord record) {
        if (!hasInitializedClient(record)) {
            return false;
        }
        IBinder binder = record.bActivityThread.asBinder();
        return binder.pingBinder();
    }

    private boolean hasInitializedClient(ProcessRecord record) {
        return hasLiveBinder(record) && record.appThread != null;
    }

    private boolean hasLiveBinder(ProcessRecord record) {
        if (record == null || record.bActivityThread == null) {
            return false;
        }
        IBinder binder = record.bActivityThread.asBinder();
        return binder != null && binder.isBinderAlive();
    }

    private boolean isBPidOwnedByAliveProcessLocked(int bpid) {
        for (ProcessRecord record : mPidsSelfLocked) {
            if (record.bpid == bpid && hasLiveBinder(record)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCurrentProcessRecord(ProcessRecord record) {
        synchronized (mProcessLock) {
            Map<String, ProcessRecord> processMap = mProcessMap.get(getProcessMapKey(record));
            return processMap != null && processMap.get(record.processName) == record;
        }
    }

    private int getProcessMapKey(ProcessRecord record) {
        return BUserHandle.getUid(record.userId, record.buid);
    }

    private boolean removeProcessRecordLocked(ProcessRecord record) {
        int processMapKey = getProcessMapKey(record);
        Map<String, ProcessRecord> processMap = mProcessMap.get(processMapKey);
        boolean removed = false;
        if (processMap != null && processMap.get(record.processName) == record) {
            processMap.remove(record.processName);
            if (processMap.isEmpty()) {
                mProcessMap.remove(processMapKey);
            }
            removed = true;
        }
        mPidsSelfLocked.remove(record);
        return removed;
    }

    private void cleanupFailedProcess(ProcessRecord record, boolean killProcess) {
        boolean removed = false;
        boolean slotInUse = false;
        synchronized (mProcessLock) {
            Map<String, ProcessRecord> processMap = mProcessMap.get(getProcessMapKey(record));
            if (processMap != null && processMap.get(record.processName) == record) {
                removeProcessRecordLocked(record);
                removed = true;
            }
            for (ProcessRecord current : mPidsSelfLocked) {
                if (current != record && current.bpid == record.bpid) {
                    slotInUse = true;
                    break;
                }
            }
        }
        record.initLock.open();
        if (killProcess) {
            record.kill();
        }
        if ((removed || killProcess) && !slotInUse) {
            removeProc(record);
        }
    }

    public void onProcessDie(ProcessRecord record) {
        boolean removed;
        synchronized (mProcessLock) {
            removed = removeProcessRecordLocked(record);
        }
        record.initLock.open();
        if (removed) {
            removeProc(record);
        }
    }

    public ProcessRecord findProcessRecord(String packageName, String processName, int userId) {
        synchronized (mProcessLock) {
            int appId = BPackageManagerService.get().getAppId(packageName);
            int buid = BUserHandle.getUid(userId, appId);
            Map<String, ProcessRecord> processRecordMap = mProcessMap.get(buid);
            if (processRecordMap == null)
                return null;
            return processRecordMap.get(processName);
        }
    }

    public void killAllByPackageName(String packageName) {
        List<ProcessRecord> processesToKill = new ArrayList<>();
        synchronized (mProcessLock) {
            for (ProcessRecord processRecord : new ArrayList<>(mPidsSelfLocked)) {
                if (packageName.equals(processRecord.getPackageName())) {
                    removeProcessRecordLocked(processRecord);
                    processesToKill.add(processRecord);
                }
            }
        }
        for (ProcessRecord processRecord : processesToKill) {
            processRecord.initLock.open();
            processRecord.kill();
            removeProc(processRecord);
        }
    }

    public void killPackageAsUser(String packageName, int userId) {
        List<ProcessRecord> processesToKill = new ArrayList<>();
        synchronized (mProcessLock) {
            int buid = BUserHandle.getUid(userId, BPackageManagerService.get().getAppId(packageName));
            Map<String, ProcessRecord> process = mProcessMap.get(buid);
            if (process == null)
                return;
            for (ProcessRecord value : new ArrayList<>(process.values())) {
                removeProcessRecordLocked(value);
                processesToKill.add(value);
            }
        }
        for (ProcessRecord processRecord : processesToKill) {
            processRecord.initLock.open();
            processRecord.kill();
            removeProc(processRecord);
        }
    }

    public List<ProcessRecord> getPackageProcessAsUser(String packageName, int userId) {
        synchronized (mProcessLock) {
            int buid = BUserHandle.getUid(userId, BPackageManagerService.get().getAppId(packageName));
            Map<String, ProcessRecord> process = mProcessMap.get(buid);
            if (process == null)
                return new ArrayList<>();
            return new ArrayList<>(process.values());
        }
    }

    public int getBUidByPidOrPackageName(int pid, String packageName) {
        ProcessRecord callingProcess = findProcessByPid(pid);
        if (callingProcess == null) {
            return BPackageManagerService.get().getAppId(packageName);
        }
        return BUserHandle.getAppId(callingProcess.buid);
    }

    public int getUserIdByCallingPid(int callingPid) {
        ProcessRecord callingProcess = findProcessByPid(callingPid);
        if (callingProcess == null) {
            return 0;
        }
        return callingProcess.userId;
    }

    public ProcessRecord findProcessByPid(int pid) {
        synchronized (mProcessLock) {
            for (ProcessRecord processRecord : mPidsSelfLocked) {
                if (processRecord.pid == pid)
                    return processRecord;
            }
            return null;
        }
    }

    private static String getProcessName(Context context, int pid) {
        String processName = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == pid) {
                processName = info.processName;
                break;
            }
        }
        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    public static int getPid(Context context, String processName) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
                if (runningAppProcess.processName.equals(processName)) {
                    return runningAppProcess.pid;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void createProc(ProcessRecord record) {
        File cmdline = new File(BEnvironment.getProcDir(record.bpid), "cmdline");
        try {
            FileUtils.writeToFile(record.processName.getBytes(), cmdline);
        } catch (IOException ignored) {
        }
    }

    private static void removeProc(ProcessRecord record) {
        FileUtils.deleteDir(BEnvironment.getProcDir(record.bpid));
    }

    @Override
    public void systemReady() {
        FileUtils.deleteDir(BEnvironment.getProcDir());
    }
}
