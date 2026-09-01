package top.niunaijun.blackbox.core.system.notification;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.BRNotificationChannel;
import black.android.app.BRNotificationChannelGroup;
import black.android.app.BRNotificationO;
import black.android.app.NotificationChannelContext;
import black.android.app.NotificationChannelGroupContext;
import black.android.app.NotificationOContext;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class BNotificationManagerService extends IBNotificationManagerService.Stub implements ISystemService {
    private static final String TAG = "BNotificationManagerService";
    private final static BNotificationManagerService sService = new BNotificationManagerService();
    public static final String CHANNEL_BLACK = "@black-";
    public static final String GROUP_BLACK = "@black-group-";

    private NotificationChannelManager mNotificationChannelManager;
    private final Map<String, NotificationRecord> mNotificationRecords = new HashMap<>();

    private final NotificationManager mRealNotificationManager =
            (NotificationManager) BlackBoxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

    public static BNotificationManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        mNotificationChannelManager = NotificationChannelManager.get();
    }


    private NotificationRecord getNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            NotificationRecord notificationRecord = mNotificationRecords.get(key);
            if (notificationRecord == null) {
                notificationRecord = new NotificationRecord();
                mNotificationRecords.put(key, notificationRecord);
            }
            return notificationRecord;
        }
    }

    private void removeNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            mNotificationRecords.remove(key);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel getNotificationChannel(String channelId, int userId) throws RemoteException {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return null;
        String packageName = processByPid.getPackageName();
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannels) {
            NotificationChannel notificationChannel = notificationRecord.mNotificationChannels.get(channelId);
            if (notificationChannel != null) {
                return notificationChannel;
            }
        }
        return recoverNotificationChannel(channelId, userId, packageName, notificationRecord);
    }

    @Override
    public List<NotificationChannel> getNotificationChannels(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        recoverPackageNotificationChannels(packageName, userId, notificationRecord);
        synchronized (notificationRecord.mNotificationChannels) {
            return new ArrayList<>(notificationRecord.mNotificationChannels.values());
        }
    }

    @Override
    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            return new ArrayList<>(notificationRecord.mNotificationChannelGroups.values());
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationChannel notificationChannel, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        handleNotificationChannel(notificationChannel, userId, packageName);
        try {
            mRealNotificationManager.createNotificationChannel(notificationChannel);
        } finally {
            resetNotificationChannel(notificationChannel);
        }
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            notificationRecord.mNotificationChannels.put(notificationChannel.getId(), notificationChannel);
        }
        Slog.d(TAG, "Created notification channel for " + packageName + ": "
                + notificationChannel.getId());
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannel(String channelId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            NotificationChannel remove = notificationRecord.mNotificationChannels.remove(channelId);
            if (remove != null) {
                String blackChannelId = getBlackChannelId(remove.getId(), userId,
                        processByPid.getPackageName());
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        handleNotificationGroup(notificationChannelGroup, userId, packageName);
        try {
            mRealNotificationManager.createNotificationChannelGroup(notificationChannelGroup);
        } finally {
            resetNotificationGroup(notificationChannelGroup);
        }
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            notificationRecord.mNotificationChannelGroups.put(notificationChannelGroup.getId(), notificationChannelGroup);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannelGroup(String groupId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            NotificationChannelGroup remove = notificationRecord.mNotificationChannelGroups.remove(groupId);
            if (remove != null) {
                String blackGroupId = getBlackGroupId(remove.getId(), userId,
                        processByPid.getPackageName());
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
        }
    }

    @Override
    public void enqueueNotificationWithTag(int id, String tag, Notification notification, int userId) {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        int notificationId = getNotificationId(userId, id, packageName, tag);

        if (BuildCompat.isOreo()) {
            NotificationOContext notificationOContext = BRNotificationO.get(notification);
            
            if (notificationOContext._check_mChannelId() != null) {
                String channelId = notificationOContext.mChannelId();
                ensureNotificationChannel(channelId, userId, packageName);
                String blackChannelId = getBlackChannelId(channelId, userId, packageName);
                notificationOContext._set_mChannelId(blackChannelId);
            }
            
            if (notificationOContext._check_mGroupKey() != null) {
                String blackGroupId = getBlackGroupId(notificationOContext.mGroupKey(), userId,
                        packageName);
                notificationOContext._set_mGroupKey(blackGroupId);
            }
        }
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.add(notificationId);
        }
        try {
            mRealNotificationManager.notify(notificationId, notification);
            Slog.w(TAG, "Posted notification: package=" + packageName
                    + ", user=" + userId + ", id=" + id + ", tag=" + tag
                    + ", hostId=" + notificationId);
        } catch (Throwable e) {
            Slog.e(TAG, "Unable to post notification for " + packageName
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelNotificationWithTag(int id, String tag, int userId) throws RemoteException {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        int notificationId = getNotificationId(userId, id, packageName, tag);
        mRealNotificationManager.cancel(notificationId);
        Slog.d(TAG, "Cancelled notification: package=" + packageName
                + ", user=" + userId + ", id=" + id + ", tag=" + tag);

        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mIds) {
            notificationRecord.mIds.remove(notificationId);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void handleNotificationChannel(NotificationChannel notificationChannel, int userId,
                                           String packageName) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String blackChannelId = getBlackChannelId(channelId, userId, packageName);
        channelContext._set_mId(blackChannelId);

        notificationChannel.setGroup(getBlackGroupId(notificationChannel.getGroup(), userId,
                packageName));
    }

    private void resetNotificationChannel(NotificationChannel notificationChannel) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String realChannelId = getRealChannelId(channelId);
        channelContext._set_mId(realChannelId);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private NotificationChannel recoverNotificationChannel(String channelId, int userId,
                                                           String packageName,
                                                           NotificationRecord notificationRecord) {
        NotificationChannel notificationChannel = mRealNotificationManager.getNotificationChannel(
                getBlackChannelId(channelId, userId, packageName));
        boolean legacyChannel = false;
        if (notificationChannel == null) {
            notificationChannel = mRealNotificationManager.getNotificationChannel(
                    getLegacyBlackChannelId(channelId, userId));
            legacyChannel = notificationChannel != null;
        }
        if (notificationChannel == null) {
            Slog.w(TAG, "Notification channel is missing: package=" + packageName
                    + ", user=" + userId + ", channel=" + channelId);
            return null;
        }

        resetNotificationChannel(notificationChannel);
        if (legacyChannel) {
            createHostNotificationChannel(notificationChannel, userId, packageName);
            Slog.i(TAG, "Migrated legacy notification channel: package=" + packageName
                    + ", user=" + userId + ", channel=" + channelId);
        }
        synchronized (notificationRecord.mNotificationChannels) {
            notificationRecord.mNotificationChannels.put(channelId, notificationChannel);
        }
        return notificationChannel;
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void recoverPackageNotificationChannels(String packageName, int userId,
                                                    NotificationRecord notificationRecord) {
        String suffix = CHANNEL_BLACK + getNotificationNamespace(packageName, userId);
        for (NotificationChannel notificationChannel
                : mRealNotificationManager.getNotificationChannels()) {
            String channelId = notificationChannel.getId();
            if (channelId == null || !channelId.endsWith(suffix)) {
                continue;
            }
            resetNotificationChannel(notificationChannel);
            synchronized (notificationRecord.mNotificationChannels) {
                notificationRecord.mNotificationChannels.put(notificationChannel.getId(),
                        notificationChannel);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void ensureNotificationChannel(String channelId, int userId, String packageName) {
        if (channelId == null) {
            return;
        }
        String blackChannelId = getBlackChannelId(channelId, userId, packageName);
        if (mRealNotificationManager.getNotificationChannel(blackChannelId) != null) {
            return;
        }
        recoverNotificationChannel(channelId, userId, packageName,
                getNotificationRecord(packageName, userId));
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createHostNotificationChannel(NotificationChannel notificationChannel, int userId,
                                               String packageName) {
        handleNotificationChannel(notificationChannel, userId, packageName);
        try {
            mRealNotificationManager.createNotificationChannel(notificationChannel);
        } finally {
            resetNotificationChannel(notificationChannel);
        }
    }

    private void handleNotificationGroup(NotificationChannelGroup notificationChannelGroup, int userId,
                                         String packageName) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String blackGroupId = getBlackGroupId(groupId, userId, packageName);
        groupContext._set_mId(blackGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                handleNotificationChannel(notificationChannel, userId, packageName);
            }
        }
    }

    private void resetNotificationGroup(NotificationChannelGroup notificationChannelGroup) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String realGroupId = getRealGroupId(groupId);
        groupContext._set_mId(realGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                resetNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("NewApi")
    public void deletePackageNotification(String packageName, int userId) {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        if (BuildCompat.isOreo()) {
            for (NotificationChannelGroup value : notificationRecord.mNotificationChannelGroups.values()) {
                String blackGroupId = getBlackGroupId(value.getId(), userId, packageName);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
            for (NotificationChannel value : notificationRecord.mNotificationChannels.values()) {
                String blackChannelId = getBlackChannelId(value.getId(), userId, packageName);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
            String suffix = CHANNEL_BLACK + getNotificationNamespace(packageName, userId);
            for (NotificationChannel channel : mRealNotificationManager.getNotificationChannels()) {
                if (channel.getId() != null && channel.getId().endsWith(suffix)) {
                    mRealNotificationManager.deleteNotificationChannel(channel.getId());
                }
            }
        }
        for (Integer id : notificationRecord.mIds) {
            mRealNotificationManager.cancel(id);
        }
        removeNotificationRecord(packageName, userId);
    }

    private String getBlackChannelId(String channelId, int userId, String packageName) {
        if (channelId == null || channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId + CHANNEL_BLACK + getNotificationNamespace(packageName, userId);
    }

    private String getLegacyBlackChannelId(String channelId, int userId) {
        if (channelId == null || channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId + CHANNEL_BLACK + userId;
    }

    private String getRealChannelId(String channelId) {
        if (channelId == null || !channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId.split(CHANNEL_BLACK)[0];
    }

    private String getBlackGroupId(String groupId, int userId, String packageName) {
        if (groupId == null || groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId + GROUP_BLACK + getNotificationNamespace(packageName, userId);
    }

    private String getRealGroupId(String groupId) {
        if (groupId == null || !groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId.split(GROUP_BLACK)[0];
    }

    public static int getNotificationId(int userId, int notificationId, String packageName) {
        return getNotificationId(userId, notificationId, packageName, null);
    }

    public static int getNotificationId(int userId, int notificationId, String packageName,
                                        String tag) {
        return (packageName + '|' + userId + '|' + notificationId + '|'
                + String.valueOf(tag)).hashCode();
    }

    private String getNotificationNamespace(String packageName, int userId) {
        return userId + "-" + Integer.toHexString(packageName.hashCode());
    }
}
