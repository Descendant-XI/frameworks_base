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

package com.android.systemui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import com.android.systemui.SystemUI;

public class DescendantHealth {
    static String TAG = "DescendantHealth";
    static NotificationManager mNotificationManager;
    static Intent mInfoIntent;
    static Intent mNightIntent;
    static Intent mDndIntent;
    static Context staticContext;
    static String nullString = null;
    static final String[] LOG_MSGS = { "just ran " };

    public static void wakeTooLong (Context mContext) {
        final int NOTIFY_ID = 0;
        staticContext = mContext;
        String channelId = "default_channel_id";
        String channelDescription = "Descendant Health";
        String TAG_SUBCLASS = "wakeTooLong";
        HealthManLog(TAG_SUBCLASS + LOG_MSGS[0]);

        //info intent
        Intent infoIntent = new Intent(Intent.ACTION_VIEW);
        String url = mContext.getString(R.string.descendant_health_eyestrain_knowledge);
        infoIntent.setData(Uri.parse(url));
        infoIntent.putExtra("INFO_INTENT", nullString);
        mInfoIntent = infoIntent;
        PendingIntent infoIntentPI = PendingIntent.getActivity(mContext, 0, infoIntent, 0);

        //night intent
        Intent nightIntent = new Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS);
        PendingIntent nightIntentPI = PendingIntent.getActivity(mContext, 0, nightIntent, 0);
        nightIntent.putExtra("NIGHT_INTENT", nullString);
        mNightIntent = nightIntent;

        Notification.Builder builder = new Notification.Builder(mContext, channelId);
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager = notificationManager;
        int importance = NotificationManager.IMPORTANCE_HIGH; //Set the importance level
        final Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, mContext.getString(R.string.descendant_health_appname));
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
        notificationManager.createNotificationChannel(notificationChannel);
        builder.setContentTitle(mContext.getString(R.string.descendant_health_eyestrain_title))
               .setSmallIcon(R.drawable.descendant_health)
               .setContentText(mContext.getString(R.string.descendant_health_eyestrain_summary))
               .setDefaults(Notification.DEFAULT_ALL)
               //.setContentIntent(pendingIntent)
               .setColor(Color.RED)
               .setStyle(new Notification.BigTextStyle().bigText(mContext.getString(R.string.descendant_health_eyestrain_summary)))
               .setTicker(mContext.getString(R.string.descendant_health_appname))
               .setAutoCancel(true)
               .addAction(R.drawable.ic_info, mContext.getString(R.string.descendant_health_eyestrain_info),infoIntentPI)
               .addAction(R.drawable.ic_night, mContext.getString(R.string.descendant_health_eyestrain_nightlight),nightIntentPI)
               .addExtras(extras);
        Notification notification = builder.build();
        notificationManager.createNotificationChannel(notificationChannel);
        notificationManager.notify(NOTIFY_ID, notification);
    }

    public static void getSomeSleep (Context mContext) {
        final int NOTIFY_ID = 1;
        staticContext = mContext;
        String channelId = "default_channel_id";
        String channelDescription = "Descendant Health";
        String TAG_SUBCLASS = "getSomeSleep";
        HealthManLog(TAG_SUBCLASS + LOG_MSGS[0]);
        final Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, mContext.getString(R.string.descendant_health_appname));
        //dnd intent
        Intent dndIntent = new Intent(Settings.ACTION_ZEN_MODE_SETTINGS);
        PendingIntent dndIntentPI = PendingIntent.getActivity(mContext, 0, dndIntent, 0);
        dndIntent.putExtra("DND_INTENT", nullString);
        mDndIntent = dndIntent;
        Notification.Builder builder = new Notification.Builder(mContext, channelId);
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager = notificationManager;
        int importance = NotificationManager.IMPORTANCE_HIGH; //Set the importance level
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
        notificationManager.createNotificationChannel(notificationChannel);
        builder.setContentTitle(mContext.getString(R.string.descendant_health_sleep_title))
               .setSmallIcon(R.drawable.descendant_health)
               .setContentText(mContext.getString(R.string.descendant_health_sleep_summary))
               .setDefaults(Notification.DEFAULT_ALL)
               //.setContentIntent(pendingIntent)
               .setColor(Color.RED)
               .setStyle(new Notification.BigTextStyle().bigText(mContext.getString(R.string.descendant_health_sleep_summary)))
               .setTicker(mContext.getString(R.string.descendant_health_appname))
               .setAutoCancel(true)
               .addAction(R.drawable.ic_night, mContext.getString(R.string.descendant_health_sleep_dnd),dndIntentPI)
               .addExtras(extras);
        Notification notification = builder.build();
        notificationManager.createNotificationChannel(notificationChannel);
        notificationManager.notify(NOTIFY_ID, notification);
    }

    private static void HealthManLog(String msg) {
        if (SystemProperties.getBoolean("descendant.debug", false))
            Log.d(TAG, msg);
    }

}
