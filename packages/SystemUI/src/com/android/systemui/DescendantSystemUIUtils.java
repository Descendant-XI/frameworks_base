package com.android.systemui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class DescendantSystemUIUtils {

    private static NotificationManager mNotificationManager;
    private static Integer mNotificationId;

    private static final String TAG = "DescendantSystemUIUtils";

    private static String[] mClockFlowThemes = { "org.descendant.clock.flow.descendant",
                                        "org.descendant.clock.flow.swan",
                                        "org.descendant.clock.flow.chameleon",
                                        "org.descendant.clock.flow.venetian.mask",
                                        "org.descendant.clock.flow.birdie",
                                        "org.descendant.clock.flow.cityline",
                                        "org.descendant.clock.flow.earth",
                                        "org.descendant.clock.flow.moon",
                                        "org.descendant.clock.flow.pinwheel"};

    public static boolean settingStatusBoolean(String settingName, Context ctx) {
        try {
            if (android.provider.Settings.System.getIntForUser(ctx.getContentResolver(),settingName,UserHandle.USER_CURRENT) == 1) {
                return true;
            } else {
                return false;
            }
        } catch (android.provider.Settings.SettingNotFoundException e) {
                return false;
        }
    }

    public static void setSecureSetting(String settingName, Context ctx, int value) {
        //try {
            android.provider.Settings.Secure.putIntForUser(ctx.getContentResolver(),settingName,value,UserHandle.USER_CURRENT);
        /*} catch (android.provider.Settings.SettingNotFoundException e) {
            //TODO: handle exception
        }*/
    }

    public static void setSystemSetting(String settingName, Context ctx, int value) {
    //try {
        android.provider.Settings.System.putIntForUser(ctx.getContentResolver(),settingName,value,UserHandle.USER_CURRENT);
    /*} catch (android.provider.Settings.SettingNotFoundException e) {
        //TODO: handle exception
    }*/
    }

    public static void setSystemSettingString(String settingName, Context ctx, String value) {
        //try {
            android.provider.Settings.System.putStringForUser(ctx.getContentResolver(),settingName,value,UserHandle.USER_CURRENT);
        /*} catch (android.provider.Settings.SettingNotFoundException e) {
            //TODO: handle exception
        }*/
    }

    public static String getSystemSettingString(String settingName, Context ctx) {
        //try {
            return android.provider.Settings.System.getStringForUser(ctx.getContentResolver(),settingName,UserHandle.USER_CURRENT);
        //} catch (android.provider.Settings.SettingNotFoundException e) {
        //    return "0";
        //}
    }

    public static int settingStatusInt(String settingName, Context ctx) {
        try {
            return android.provider.Settings.System.getIntForUser(ctx.getContentResolver(),settingName,UserHandle.USER_CURRENT);
        } catch (android.provider.Settings.SettingNotFoundException e) {
            return 0;
        }
    }

    public static void clockFlowSelector(IOverlayManager om, int userId, int setting) {
        for (int i = 1; i <= mClockFlowThemes.length; i++) {
            if (setting != 0) {
                try {
                    om.setEnabled(mClockFlowThemes[i - 1], i != setting ? false : true, userId);
                    } catch (RemoteException e) {
                        Log.e(TAG, "flow RemoteException has been catched");
                    }
            } else {
                try {
                    om.setEnabled(mClockFlowThemes[i - 1], false, userId);
                    } catch (RemoteException e) {
                        Log.e(TAG, "flow RemoteException has been catched");
                    }
            }
        }
    }

    public static void generateNotification(String contentTitle, String summary,
                                            int icon, String contentText,
                                            String appName, String channelDescription,
                                            Context context) {
        mNotificationId = 122;
        String channelId = "default_channel_id";

        Notification.Builder builder = new Notification.Builder(context, channelId);
        NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        int importance = NotificationManager.IMPORTANCE_HIGH; //Set the importance level
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
        mNotificationManager.createNotificationChannel(notificationChannel);
        builder.setContentTitle(contentTitle)
               .setSmallIcon(icon)
               .setContentText(contentText)
               .setDefaults(Notification.DEFAULT_ALL)
               .setColor(Color.RED)
               .setStyle(new Notification.BigTextStyle().bigText(summary))
               .setTicker(appName)
               .setAutoCancel(true);
        SystemUI.overrideNotificationAppName(context, builder, false);
        Notification notification = builder.build();
        mNotificationManager.createNotificationChannel(notificationChannel);
        mNotificationManager.notify(mNotificationId, notification);
    }

    public static void dismissNotification() {
        Log.d("Dilemmino", "Ciao " + String.valueOf(mNotificationId));
        if (mNotificationId != null)
             Log.d("Dilemmino", "Ciao2 " + String.valueOf(mNotificationId));
            mNotificationManager.cancel(mNotificationId);
    }

    public static boolean isDescendantDebug() {
        return SystemProperties.getBoolean("descendant.debug", false);
    }

    public static void copyFile(Context context, String inputPath, String inputFile, String outputPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            //create output directory if it doesn't exist
            File dir = new File (outputPath);
            if (!dir.exists())
            {
                dir.mkdirs();
            }
            in = new FileInputStream(inputPath + inputFile);
            out = new FileOutputStream(outputPath + inputFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            // write the output file
            out.flush();
            out.close();
            out = null;
            MediaScannerConnection.scanFile(
                context,
                new String[]{outputPath + inputFile},
                new String[]{"audio/mpeg"},
                null);
        }  catch (java.io.FileNotFoundException fnfe1) {
            setSystemSetting("feature_copy", context, 1);
            copyFile(context, inputPath, inputFile, outputPath);
            Log.e(TAG, fnfe1.getMessage());
        }  catch (Exception e) {
            setSystemSetting("feature_copy", context, 1);
            copyFile(context, inputPath, inputFile, outputPath);
            Log.e(TAG, e.getMessage());
        }
    }
}
