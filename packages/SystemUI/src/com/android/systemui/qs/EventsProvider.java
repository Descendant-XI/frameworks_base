package com.android.systemui.qs;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.util.Log;

import com.android.systemui.DescendantSystemUIUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class EventsProvider {
    static String TAG = "EventsProvider";

    public static boolean areThereCalendarEvents(Context context) {
        String[] projection = new String[] {
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_LOCATION };

        Calendar startTime = Calendar.getInstance();
        Cursor cursor;

        startTime.set(Calendar.HOUR_OF_DAY,0);
        startTime.set(Calendar.MINUTE,0);
        startTime.set(Calendar.SECOND, 0);

        Calendar endTime= Calendar.getInstance();
        endTime.add(Calendar.DATE, 1);

        String selection = "(( " + CalendarContract.Events.DTSTART + " >= " +
            startTime.getTimeInMillis() + " ) AND ( " + CalendarContract.Events.DTSTART + " <= " + endTime.getTimeInMillis() + " ) AND ( deleted != 1 ))";
        try {
            cursor = context.getContentResolver().query(CalendarContract.Events.CONTENT_URI, projection, selection, null, null);
        } catch (Throwable t) {
            cursor = null;
            if (DescendantSystemUIUtils.isDescendantDebug())
                Log.d(TAG, "An exception has been caught, and cursor is now null");
        }
        List<String> events = new ArrayList<>();

        if (cursor!=null&&cursor.getCount()>0&&cursor.moveToFirst()) {
            do {
                events.add(cursor.getString(1));
                if (DescendantSystemUIUtils.isDescendantDebug())
                    Log.w(TAG, "Title " + cursor.getString(1));
            } while (cursor.moveToNext());
                cursor.close();
                return (true);
        } else {
            if (cursor != null) cursor.close();
            return (false);
        }
    }

    public static boolean areThereAlarms(Context context) {
        String nextAlarm = android.provider.Settings.System.getString(context.getContentResolver(), android.provider.Settings.System.NEXT_ALARM_FORMATTED);
        if (nextAlarm == null) return false;
        return nextAlarm.isEmpty() ? false : true;
    }

    public static int ringerStatus(Context context) {
        AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        /* 0 silent
           1 vibrate
           2 normal */
        return am.getRingerMode();
    }

    public static boolean areThereMissedCalls(Context context) {
        int count = 0;
        String PATH = "content://call_log/calls";
        String[] projection = new String[] { CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE };
        String sortOrder = CallLog.Calls.DATE + " DESC";
        StringBuffer sb = new StringBuffer();
        sb.append(CallLog.Calls.TYPE).append("=?").append(" and ").append(CallLog.Calls.IS_READ).append("=?");
        Cursor cursor;
        try {
            cursor = context.getContentResolver().query(
                            Uri.parse(PATH),
                            projection,
                            sb.toString(),
                            new String[] { String.valueOf(Calls.MISSED_TYPE), "0" },sortOrder);
        } catch (Throwable t) {
            cursor = null;
            cursor.close();
            if (DescendantSystemUIUtils.isDescendantDebug())
                Log.d(TAG, "An exception has been caught, and cursor is now null");
        }
        if (cursor != null) {
            count = cursor.getCount();
            cursor.close();
        }
        return cursor != null && count > 0 ? true : false;
    }

}