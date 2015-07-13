package com.spazedog.guardian.backend;


import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

import com.spazedog.guardian.ActivityLaunch;
import com.spazedog.guardian.Common;
import com.spazedog.guardian.Constants;
import com.spazedog.guardian.R;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.backend.containers.ThresholdItem;
import com.spazedog.guardian.backend.xposed.WakeLockManager;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.backend.xposed.WakeLockService.WakeLockInfo;
import com.spazedog.guardian.db.AlertListDB;
import com.spazedog.guardian.db.WhiteListDB;
import com.spazedog.guardian.scanner.EntityAndroid;
import com.spazedog.guardian.scanner.ProcessScanner;
import com.spazedog.guardian.scanner.ProcessScanner.ScanMode;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcList;
import com.spazedog.lib.rootfw4.Shell;
import com.spazedog.lib.utilsLib.SparseMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MonitorWorker {

    protected Controller mController;
    protected Settings mSettings;
    protected boolean mIsInteractive;
    protected int mThresholdValue;
    protected Bundle mDataBundle;
    protected SparseMap<ThresholdItem> mThresholdData = new SparseMap<ThresholdItem>();
    protected WhiteListDB mWhiteListDatabase;

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public MonitorWorker(Controller controller, Bundle data) {
        PowerManager pm = (PowerManager) controller.getSystemService(Context.POWER_SERVICE);
        AudioManager am = (AudioManager) controller.getSystemService(Context.AUDIO_SERVICE);

        mDataBundle = data;
        mController = controller;
        mSettings = controller.getSettings();
        mIsInteractive = am.getMode() != AudioManager.MODE_NORMAL
                || am.isMusicActive()
                || (android.os.Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn());

        mThresholdValue = mSettings.getServiceThreshold(mIsInteractive);
        mWhiteListDatabase = mSettings.getWhiteListDatabase();
    }

    public void start() {
        SparseMap<ThresholdItem> lastThresholdData = mDataBundle.getParcelable("evaluate");
        SparseMap<ThresholdItem> currentThresholdData = mThresholdData;
        ScanMode scanMode = mSettings.monitorLinux() ? ScanMode.COLLECT_PROCESSES : ScanMode.COLLECT_APPLICATIONS;
        ProcList<?> processList = ProcessScanner.execute(mController, scanMode, (ProcList<?>) mDataBundle.getParcelable("processes"));
        boolean scanWakelocks = !mIsInteractive && mController.getWakeLockManager() != null;

        Common.LOG.Debug(this, "Beginning analizing the scan result, Process Count = " + (processList != null ? processList.getEntitySize() : 0) + ", Evaluation Count = " + (lastThresholdData != null ? lastThresholdData.size() : 0));

        if (processList != null) {
            boolean validThreshold = checkProcessThreshold(processList);
            boolean validWakelocks = !scanWakelocks || checkProcessWakelocks(processList);

            if ((!validThreshold || !validWakelocks) && lastThresholdData != null) {
                Common.LOG.Debug(this, "Checking possible rough processes, Rough Count = " + mThresholdData.size());

                Set<ThresholdItem> roughItemList = new HashSet<ThresholdItem>();

                for (Map.Entry<Integer, ThresholdItem> thresholdEntry : lastThresholdData.entrySet()) {
                    int pid = thresholdEntry.getKey();
                    ThresholdItem lastThresholdItem = thresholdEntry.getValue();
                    ThresholdItem currentThresholdItem = currentThresholdData.remove(pid);
                    ProcEntity<?> lastEntity = lastThresholdItem != null ? lastThresholdItem.getEntity() : null;
                    ProcEntity<?> currentEntity = currentThresholdItem != null ? currentThresholdItem.getEntity() : null;

                    if (currentEntity != null) {
                        int lastFlags = lastThresholdItem.getFlags();
                        int currentFlags = currentThresholdItem.getFlags();
                        int nextCheckCount = lastThresholdItem.getCheckCount()+1;

                        if ((currentFlags & ThresholdItem.FLAG_CPU) == ThresholdItem.FLAG_CPU) {
                            if (lastThresholdItem.getCheckCount() > 0 && (lastFlags & ThresholdItem.FLAG_CPU) == ThresholdItem.FLAG_CPU && currentEntity.getCpuUsage() >= lastEntity.getCpuUsage()) {
                                Common.LOG.Debug(this, "Adding process to the alert list, Check Count = " + lastThresholdItem.getCheckCount() + ", CPU Usage = " + currentEntity.getCpuUsage() + "%, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                                roughItemList.add(currentThresholdItem);

                            } else {
                                if (currentEntity.getCpuUsage() >= lastEntity.getCpuUsage()) {
                                    Common.LOG.Debug(this, "Letting the process calm down until next check, Check Count = " + lastThresholdItem.getCheckCount() + ", CPU Usage = " + currentEntity.getCpuUsage() + "%, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                                } else {
                                    Common.LOG.Debug(this, "The process has calmed down a bit since last check, checking again later, Check Count = " + lastThresholdItem.getCheckCount() + ", CPU Usage = " + currentEntity.getCpuUsage() + "%, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                                }

                                currentThresholdItem.setCheckCount(nextCheckCount);
                                currentThresholdData.put(pid, currentThresholdItem);
                            }

                        } else if ((lastFlags & ThresholdItem.FLAG_CPU) == ThresholdItem.FLAG_CPU) {
                            Common.LOG.Debug(this, "The process has gone down below the threshold, not checking it further, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                        }

                        if ((currentFlags & ThresholdItem.FLAG_WAKELOCK) == ThresholdItem.FLAG_WAKELOCK) {
                            ProcessLockInfo lockInfo = EntityAndroid.cast(currentEntity).getProcessLockInfo();

                            if (lastThresholdItem.getCheckCount() > 0 && (lastFlags & ThresholdItem.FLAG_WAKELOCK) == ThresholdItem.FLAG_WAKELOCK) {
                                Common.LOG.Debug(this, "Adding process to the alert list, Check Count = " + lastThresholdItem.getCheckCount() + ", WakeLock Time = " + lockInfo.getLockTime() + "ms, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                                roughItemList.add(currentThresholdItem);

                            } else {
                                Common.LOG.Debug(this, "Letting the process get a chance to release the lock until next check, Check Count = " + lastThresholdItem.getCheckCount() + ", WakeLock Time = " + lockInfo.getLockTime() + "ms, PID = " + currentEntity.getProcessId() + ", Process Name = " + currentEntity.getProcessName());
                                currentThresholdItem.setCheckCount(nextCheckCount);
                                currentThresholdData.put(pid, currentThresholdItem);
                            }

                        } else if ((lastFlags & ThresholdItem.FLAG_WAKELOCK) == ThresholdItem.FLAG_WAKELOCK) {

                        }
                    }
                }

                if (roughItemList.size() > 0) {
                    Common.LOG.Debug(this, "Alerting user about rough processes, Rough Count = " + roughItemList.size());
                    sendUserAlert(roughItemList);
                }
            }

            /*
			 * Parse everything to the next scan cycle
			 */
            if (mThresholdData.size() > 0) {
                mDataBundle.putParcelable("evaluate", mThresholdData);
                mDataBundle.putInt("timeout", getRecheckTimeout());

            } else {
                mDataBundle.remove("evaluate");
                mDataBundle.remove("timeout");
            }

            mDataBundle.putParcelable("processes", processList);
        }
    }

    protected int getRecheckTimeout() {
        int interval = mSettings.getServiceInterval();
        int timeout = (int) interval >= (5*6000) ? (5*6000) :
                interval > 6000 ? interval/2 : 6000;

        return timeout;
    }

    protected boolean checkProcessThreshold(ProcList<?> processList) {
        double cpuUsage = processList.getCpuUsage();
        boolean valid = true;

        if (processList != null && (cpuUsage > mThresholdValue || (cpuUsage > 0 && Constants.ENABLE_REPORT_TESTING))) {
            Common.LOG.Debug(this, "The CPU is above the threshold, CPU Usage = " + cpuUsage + "%, CPU Threshold = " + mThresholdValue + "%");

            for (ProcEntity<?> entity : processList) {
                boolean important = mIsInteractive && entity.isPerceptible();
                double usage = entity.getCpuUsage();

                if ((usage > mThresholdValue && !important) || (usage > 0 && Constants.ENABLE_REPORT_TESTING)) {
                    if (!mWhiteListDatabase.hasEntity(entity.getProcessName())) {
                        Common.LOG.Debug(this, "Process detected above the threshold, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", CPU Usage = " + usage + "%, CPU Threshold = " + mThresholdValue + "%");

                        int pid = entity.getProcessId();
                        ThresholdItem item = mThresholdData.get(pid);
                        valid = false;

                        if (item == null) {
                            item = new ThresholdItem(entity, ThresholdItem.FLAG_CPU);
                            item.setTimestamp(System.currentTimeMillis());

                        } else {
                            item.setEntity(entity, item.getFlags() | ThresholdItem.FLAG_CPU);
                        }

                        mThresholdData.put(pid, item);

                    } else {
                        Common.LOG.Debug(this, "Process has been white listed, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", CPU Usage = " + usage + "%, CPU Threshold = " + mThresholdValue + "%");
                    }
                }
            }

            if (valid) {
                Common.LOG.Debug(this, " - No processes seams to exceed the threshold");
            }

        } else {
            Common.LOG.Debug(this, "The CPU is beneath the threshold, CPU Usage = " + cpuUsage + "%, CPU Threshold = " + mThresholdValue + "%");
        }

        return valid;
    }

    protected boolean checkProcessWakelocks(ProcList<?> processList) {
        long lockTime = mSettings.getServiceWakeLockTime();
        boolean valid = true;

        for (ProcEntity<?> entity : processList) {
            EntityAndroid androidEntity = EntityAndroid.cast(entity);

            if (androidEntity != null) {
                ProcessLockInfo lockInfo = androidEntity.getProcessLockInfo();

                if (lockInfo != null) {
                    List<WakeLockInfo> wakeLocks = lockInfo.getWakeLocks();

                    for (WakeLockInfo wakeLock : wakeLocks) {
                        if (wakeLock.getTime() > lockTime || (wakeLock.getTime() > 0 && Constants.ENABLE_REPORT_TESTING)) {
                            if (!mWhiteListDatabase.hasEntity(entity.getProcessName())) {
                                Common.LOG.Debug(this, "Active wakelock detected, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", Wakelock Time = " + wakeLock.getTime());

                                int pid = entity.getProcessId();
                                ThresholdItem item = mThresholdData.get(pid);
                                valid = false;

                                if (item == null) {
                                    item = new ThresholdItem(entity, ThresholdItem.FLAG_WAKELOCK);
                                    item.setTimestamp(System.currentTimeMillis());

                                } else {
                                    item.setEntity(entity, item.getFlags()|ThresholdItem.FLAG_WAKELOCK);
                                }

                                mThresholdData.put(pid, item);

                                /*
                                 * We only need to know if one of it's locks has been required to long
                                 */
                                break;

                            } else {
                                Common.LOG.Debug(this, "Process has been white listed, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName() + ", Wakelock Time = " + wakeLock.getTime());
                            }
                        }
                    }
                }
            }
        }

        if (valid) {
            Common.LOG.Debug(this, " - No processes seams to misuse their wakelocks");
        }

        return valid;
    }

    protected void sendUserAlert(Set<ThresholdItem> thresholdItems) {
        setActionFlags(thresholdItems);

        Intent intent = new Intent(mController, ActivityLaunch.class);
        intent.putExtra("tab.id", "tab_process_alerts");

        PendingIntent pendingIntent = PendingIntent.getActivity(mController, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mController)
                .setContentTitle("Rough Process Alert")
                .setContentText("One or more rough processes has been detected")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setNumber(thresholdItems.size());

        Notification notify = builder.build();
        notify.defaults = Notification.DEFAULT_ALL;

        NotificationManager notificationManager = (NotificationManager) mController.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(mController.getPackageName(), MonitorService.NOTIFICATION_ID);
        notificationManager.notify(mController.getPackageName(), MonitorService.NOTIFICATION_ID, notify);

        Shell shell = null;
        for (ThresholdItem thresholdItem : thresholdItems) {
            int flags = thresholdItem.getFlags();
            ProcEntity<?> entity = thresholdItem.getEntity();

            if ((flags & ThresholdItem.FLAG_ACTION_KILLED) == ThresholdItem.FLAG_ACTION_KILLED) {
                Common.LOG.Debug(this, "Force closing process, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());

                if (mSettings.isRootEnabled()) {
                    if (shell == null) {
                        shell = new Shell(true);
                    }

                    shell.getProcess(entity.getProcessId()).kill();

                } else {
                    ActivityManager manager = (ActivityManager) mController.getSystemService(Context.ACTIVITY_SERVICE);
                    manager.killBackgroundProcesses( entity.getDataLoader(mController).getPackageLabel() );
                }

            } else if ((flags & ThresholdItem.FLAG_ACTION_REBOOTED) == ThresholdItem.FLAG_ACTION_REBOOTED) {
                Common.LOG.Debug(this, "Rebooting device, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());

                if (shell == null) {
                    shell = new Shell( mSettings.isRootEnabled() );
                }

                shell.getDevice().reboot();

            } else if ((flags & ThresholdItem.FLAG_ACTION_RELEASED) == ThresholdItem.FLAG_ACTION_RELEASED) {
                Common.LOG.Debug(this, "Releasing process wakelocks, PID = " + entity.getProcessId() + ", Process Name = " + entity.getProcessName());

                WakeLockManager manager = mController.getWakeLockManager();

                if (manager != null) {
                    manager.releaseForPid(entity.getProcessId());
                }
            }
        }

        if (shell != null && shell.isConnected()) {
            shell.destroy();
        }
    }

    protected void setActionFlags(Set<ThresholdItem> thresholdItems) {
        String thresholdAction = mSettings.getServiceAction(mIsInteractive);
        String lockAction = mSettings.getServiceWakeLockAction();
        AlertListDB db = mSettings.getAlertListDatabase();

        for (ThresholdItem thresholdItem : thresholdItems) {
            int flags = thresholdItem.getFlags();
            ProcEntity<?> entity = thresholdItem.getEntity();

            thresholdItem.setFlags( thresholdItem.getFlags()|ThresholdItem.FLAG_ACTION_NOTIFIED );

            if (!"notify".equals(thresholdAction)) {
                if ((flags & ThresholdItem.FLAG_CPU) == ThresholdItem.FLAG_CPU) {
                    String packageName = entity.getDataLoader(mController).getPackageLabel();
                    boolean perceptible = packageName != null && entity.isPerceptible();

                    if (!thresholdAction.equals("reboot") && (!perceptible || mSettings.isRootEnabled())) {
                        thresholdItem.setFlags(thresholdItem.getFlags()|ThresholdItem.FLAG_ACTION_KILLED);

                    } else if (thresholdAction.equals("reboot") || thresholdAction.equals("auto")) {
                        thresholdItem.setFlags(thresholdItem.getFlags()|ThresholdItem.FLAG_ACTION_REBOOTED);
                    }
                }

                if (lockAction.equals("release") && (flags & ThresholdItem.FLAG_WAKELOCK) == ThresholdItem.FLAG_WAKELOCK
                        && (flags & ThresholdItem.FLAG_ACTION_KILLED) != ThresholdItem.FLAG_ACTION_KILLED
                        && (flags & ThresholdItem.FLAG_ACTION_REBOOTED) != ThresholdItem.FLAG_ACTION_REBOOTED) {

                    thresholdItem.setFlags( thresholdItem.getFlags()|ThresholdItem.FLAG_ACTION_RELEASED );
                }
            }

            db.addThresholdItem(thresholdItem);
        }
    }
}
