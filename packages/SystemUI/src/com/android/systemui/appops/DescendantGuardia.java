/*
 * Copyright (C) 2019 Descendant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.appops;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import com.android.systemui.DescendantSystemUIUtils;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.SystemUI;
import com.android.systemui.R;

import java.util.List;

public class DescendantGuardia {
    static String TAG = "DescendantGuardia ";
    static NotificationManager mNotificationManager;
    static final String[] LOG_MSGS = { "warnNotif joined",
                                       "warnNotif exited due to: GApps",
                                       "warnNotif exited due to: System",
                                       "warnAlert joined",
                                       "warnNotif type: notification",
                                       "warnNotif type: toast",
                                       "warnToast joined",
                                       "dismissGuardia joined" };

    static final String NOT_IMPORTANT = "ignore";
    static final String GOOGLE_APPS = "google";
    static Integer mNotificationId;
    static boolean mDismissGuardia = true;
    static String opCodeString;
    static boolean mIsAlerting;

    public static void warnNotif (Context mContext, String packageName, int opCode, boolean isRemoving) {
        if (DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_exclude_gapps", mContext)) {
            guardiaLog(TAG + LOG_MSGS[1]);
            if (packageName.contains(GOOGLE_APPS)) return;
        }
        if (DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_exclude_system_apps", mContext)) {
            guardiaLog(TAG + LOG_MSGS[2]);
            if (isSystemApp(mContext,packageName) && !packageName.contains(GOOGLE_APPS)) return;
        }
        opCodeString = evaluateOpCode(mContext, opCode, false);
        if (opCodeString == NOT_IMPORTANT) {
            return;
        }
        if (DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_alert_me", mContext)) {
            warnAlert(mContext, packageName, opCode);
        } else {
            mIsAlerting = false;
        }
        if (DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_type_notif", mContext)) {
            guardiaLog(TAG + LOG_MSGS[4]);
            mNotificationId = 0;
            String channelId = "default_channel_id";
            String channelDescription = mContext.getString(R.string.descendant_guardia_appname);
            String contentText;
            String contentTitle;
            if (!isRemoving) {
                contentTitle = mContext.getString(R.string.descendant_guardia_monitoring);
                contentText = mContext.getString(R.string.descendant_guardia_application_with_package_name) + " '" + getAppName(mContext, packageName) + "'" +
                              " " + mContext.getString(R.string.descendant_guardia_is_accessing) + " " + opCodeString;
            } else {
                contentTitle = mContext.getString(R.string.descendant_guardia_detection);
                contentText = mContext.getString(R.string.descendant_guardia_listening_state);
            }
            final Bundle extras = new Bundle();
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, mContext.getString(R.string.descendant_guardia_appname));
            Notification.Builder builder = new Notification.Builder(mContext, channelId);
            NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager = notificationManager;
            int importance = isRemoving ? NotificationManager.IMPORTANCE_LOW : NotificationManager.IMPORTANCE_HIGH; //Set the importance level
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
            notificationManager.createNotificationChannel(notificationChannel);
            builder.setContentTitle(contentTitle)
                   .setSmallIcon(R.drawable.descendant_guardia)
                   .setContentText(contentText)
                   .setDefaults(Notification.DEFAULT_ALL)
                   .setColor(Color.BLUE)
                   .setStyle(new Notification.BigTextStyle().bigText(contentText))
                   .setTicker(channelDescription)
                   .setOngoing(true)
                   .setPriority(isRemoving ? Notification.PRIORITY_LOW : Notification.PRIORITY_HIGH)
                   .addExtras(extras)
                   .setAutoCancel(true);
            Notification notification = builder.build();
            notificationManager.createNotificationChannel(notificationChannel);
            notificationManager.notify(mNotificationId, notification);

        } else {
            guardiaLog(TAG + LOG_MSGS[5]);
            warnToast(mContext, packageName, opCode);
        }
    }

    public static void warnToast(Context mContext, String packageName, int opCode) {
        guardiaLog(TAG + LOG_MSGS[6]);
        if (mIsAlerting) return;
        //R deprecated custom toasts
        /*LayoutInflater inflater = LayoutInflater.from(mContext);
        View layout = inflater.inflate(com.android.systemui.R.layout.descendant_guardia_toast, null);
        TextView text = (TextView) layout.findViewById(R.id.descendant_guardia_toast_content);
        text.setText(nonnt);
        text.setSelected(true);*/
        CharSequence nonnt = getAppName(mContext, packageName) + " " + mContext.getString(R.string.descendant_guardia_is_accessing) + " " + opCodeString;
        Toast toast = new Toast(mContext);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0,0);
        //R deprecated custom toasts
        //toast.setView(layout);
        toast.setText(nonnt);
        toast.show();
    }

    public static void warnAlert(Context mContext, String packageName, int opCode) {
        guardiaLog(TAG + LOG_MSGS[3]);

        boolean isWorthy = Boolean.valueOf(evaluateOpCode(mContext,opCode,true));
        mIsAlerting = isWorthy;
        if (!isWorthy) {
            return;
        }
        AlertDialog mAlertDialog = new AlertDialog.Builder(mContext).create();
        mAlertDialog.setTitle(mContext.getString(R.string.descendant_guardia_appname));
        mAlertDialog.setMessage(getAppName(mContext, packageName) + " " + mContext.getString(R.string.descendant_guardia_is_accessing) + " " + opCodeString);
        mAlertDialog.setIcon(R.drawable.descendant_guardia_toast);
        mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, mContext.getString(R.string.descendant_guardia_acknowledge),
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mAlertDialog.getWindow().setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        mAlertDialog.show();
    }

    private static void guardiaLog(String msg) {
        if (SystemProperties.getBoolean("descendant.debug", false))
            Log.d(TAG, msg);
    }


    private static String evaluateOpCode(Context mContext, int opCode, boolean isAlert) {
        boolean isGPSAlert = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_gps_alert", mContext);
        boolean isCameraAlert = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_camera_alert", mContext);
        boolean isRecordAudio = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_record_audio_alert", mContext);
        boolean isPhoneAlert = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_phone_alert", mContext);
        boolean isClipboardReadAlert = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_clipboard_read_alert", mContext);
        boolean isClipboardWriteAlert = DescendantSystemUIUtils.settingStatusBoolean("descendant_guardia_is_clipboard_write_alert", mContext);
        switch (opCode) {
            case 0:
            case 1:
            case 2:
            case 41:
            case 42:
                if (isAlert) {
                    if (isGPSAlert) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
                return mContext.getString(R.string.descendant_guardia_gps_perm);
            case 26:
                if (isAlert) {
                    if (isCameraAlert) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
                return mContext.getString(R.string.descendant_guardia_camera_perm);
            case 27:
                if (isAlert) {
                    if (isRecordAudio) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
            return mContext.getString(R.string.descendant_guardia_record_audio_perm);
            case 13:
            case 51:
            case 65:
            case 69:
                if (isAlert) {
                    if (isPhoneAlert) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
                return mContext.getString(R.string.descendant_guardia_phone_perm);
            case 29:
                if (isAlert) {
                    if (isClipboardReadAlert) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
                return mContext.getString(R.string.descendant_guardia_clipboard_read);
            case 30:
                if (isAlert) {
                    if (isClipboardWriteAlert) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
                return mContext.getString(R.string.descendant_guardia_clipboard_write);
            default:
                if (isAlert) return "false";
                return NOT_IMPORTANT;
        }
    }

    private static String getAppName(Context context, String packageName) {
        ApplicationInfo ai;
        try {
            ai = context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            ai = null;
        }
        return (String) (ai != null ? context.getPackageManager().getApplicationLabel(ai) : "(unknown)");
    }

    private static boolean isSystemApp(Context context, String packageName) {
        ApplicationInfo ai;
        List<PackageInfo> list = context.getPackageManager().getInstalledPackages(0);
        for (PackageInfo pi : list) {
            try {
                ai = context.getPackageManager().getApplicationInfo(packageName, 0);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                ai = null;
            }
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        }
        return false;
    }

    public static void dismissGuardia(boolean state) {
        guardiaLog(TAG + LOG_MSGS[7]);
        mDismissGuardia = state;
        if (!state && mNotificationId != null)
            mNotificationManager.cancel(mNotificationId);
    }
}
