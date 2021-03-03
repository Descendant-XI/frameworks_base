package com.android.systemui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.DescendantSystemUIUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.InterruptedException;
import java.lang.StringBuffer;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.json.JSONObject;
import org.json.JSONException;

public class COVID19Helper {

    private static String BASE_URL = "https://corona.lmao.ninja/v2/countries/";
    private static String ERROR = "-255";
    private static int ERROR_INT = -255;
    private static String TAG = "COVID19Helper";
    private static final int CORRECT_LENGTH = 7;

    private Context mContext;

    private Handler mHandler;

    private JSONObject mCovidJSONDataToday;
    private JSONObject mCovidJSONDataYesterday;

    //private String mDayPref = "today";

    private TelephonyManager mTelephonyManager;

    public COVID19Helper (Context context) {
        mContext = context;
        mHandler = new Handler();
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void init() {
        connectAndGather("today");
        connectAndGather("yesterday");
        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();
    }

    /* Data gathering */
    private void connectAndGather(String day) {
        Thread h = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                        HttpURLConnection con = null ;
                        InputStream is = null;
                        if(countryISO() == null || countryISO() == ERROR) {
                            mCovidJSONDataToday = null;
                            mCovidJSONDataYesterday = null;
                            return;
                        }
                        con = (HttpURLConnection) ( new URL(BASE_URL + countryISO() + "?" + day + "=true" + "&query" )).openConnection();
                        con.setRequestMethod("GET");
                        con.setRequestProperty("Accept","*/*");
                        con.setDoOutput(false);
                        con.connect();
                        StringBuffer buffer = new StringBuffer();
                        is = con.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        StringBuffer json = new StringBuffer(1024);
                        String line = null;
                        while ( (line = br.readLine()) != null )
                        json.append(line).append("\n");
                        is.close();
                        con.disconnect();
                        JSONObject data = new JSONObject(json.toString());
                        if (day == "today") {
                            mCovidJSONDataToday = data;
                        } else {
                            mCovidJSONDataYesterday = data;
                        }
                        Log.d(TAG, "data value " + data.toString());
                    } catch(Throwable t) {
                        Log.d(TAG, "throwable");
                        if (day == "today") {
                            mCovidJSONDataToday = null;
                        } else {
                            mCovidJSONDataYesterday = null;
                        }
                        return;
                    }
                }
            });
            h.start();
            try {
                h.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "INTERRUPTED");
            }
            if (day != "today") fillSettingWithData();
    }

    /* Data processing  */
    private String todayCases(boolean isToday) {
        try{
            Log.d(TAG, "todaycases " + "isJsonnull:" + String.valueOf(isJSONNull()) + " " + "isToday:" + String.valueOf(isToday) + " "
            + "todaycases:" + String.valueOf(mCovidJSONDataToday.getInt("todayCases")) + " "
            + "yesterdaycases:" + String.valueOf(mCovidJSONDataToday.getInt("todayCases")));
            return !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("todayCases") : mCovidJSONDataYesterday.getInt("todayCases")) : ERROR;
        } catch (JSONException e) {
        }
        return ERROR;
    }

    private String totalCases(boolean isToday) {
        try{
            return !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("cases") : mCovidJSONDataYesterday.getInt("cases")) : ERROR;
        } catch (JSONException e) {
        }
        Log.d(TAG, "totalcases returned error");
        return ERROR;
    }

    private String todayDeaths(boolean isToday) {
        try {
            return  !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("todayDeaths") : mCovidJSONDataYesterday.getInt("todayDeaths")) : ERROR;
        } catch (JSONException e) {
        }
        Log.d(TAG, "todaydeaths returned error");
        return ERROR;
    }

    private String todayRecovered(boolean isToday) {
        try{
            return  !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("todayRecovered") : mCovidJSONDataYesterday.getInt("todayRecovered")) : ERROR;
            } catch (JSONException e) {
        }
        Log.d(TAG, "todayrecovered returned error");
        return ERROR;
    }

    private String criticalCases(boolean isToday) {
        try{
            return !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("critical") : mCovidJSONDataYesterday.getInt("critical")) : ERROR;
        } catch (JSONException e) {
        }
        Log.d(TAG, "criticalcases returned error");
        return ERROR;
    }

    private String testNumber(boolean isToday) {
        try {
            return  !isJSONNull() ? formatInteger(isToday ? mCovidJSONDataToday.getInt("tests") : mCovidJSONDataYesterday.getInt("tests")) : ERROR;
        } catch (JSONException e) {
        }
        Log.d(TAG, "testnumber returned error");
        return ERROR;
    }

    /* Data comparison */
    private String diffRetrieve(String identity) {
        int yesterdayData;
        int todayData;
        int diff;
        switch(identity) {
            case "tests":
                try {
                    yesterdayData = !isJSONNull() ? mCovidJSONDataYesterday.getInt("tests") : 0;
                    todayData = !isJSONNull() ? mCovidJSONDataToday.getInt("tests") : 0;
                } catch (JSONException e ) {
                    yesterdayData = 0;
                    todayData = 0;
                }
                if (yesterdayData == 0 || todayData == 0) return ERROR;
                diff = todayData - yesterdayData;
                return formatInteger(diff);

            case "deaths":
                try {
                    yesterdayData = !isJSONNull() ? mCovidJSONDataYesterday.getInt("todayDeaths") : 0;
                    todayData = !isJSONNull() ? mCovidJSONDataToday.getInt("todayDeaths") : 0;
                } catch (JSONException e ) {
                    yesterdayData = 0;
                    todayData = 0;
                }
                if (yesterdayData == 0 || todayData == 0) return ERROR;
                diff = todayData - yesterdayData;
                return formatInteger(diff);

            case "cases":
                try {
                    yesterdayData = !isJSONNull() ? mCovidJSONDataYesterday.getInt("cases") : 0;
                    todayData = !isJSONNull() ? mCovidJSONDataToday.getInt("cases") : 0;
                } catch (JSONException e ) {
                    yesterdayData = 0;
                    todayData = 0;
                }
                if (yesterdayData == 0 || todayData == 0) return ERROR;
                diff = todayData - yesterdayData;
                return formatInteger(diff);

            default:
                return ERROR;
        }
    }

    /* Data storage */
    private void fillSettingWithData() {
        if (!dataSameAsStored()) {
            Log.d(TAG, "fillsettingwithdata data isn't same as stored");
            if (isJSONNull()) {
                Log.d(TAG, "fillsettingwithdata some json is null");
                DescendantSystemUIUtils.setSystemSettingString("covid_data", mContext, "-255");
                return;
            }
            Log.d(TAG, "fillsettingwithdata some json isn't null");
            DescendantSystemUIUtils.setSystemSettingString("covid_data", mContext, stringFormattedData());
        } else {
            //Log.d(TAG, "fillsettingwithdata data is same as stored");
            //DescendantSystemUIUtils.setSystemSettingString("covid_data", mContext, stringFormattedDataYesterday());
        }
    }

    /* Data coherence */
    private boolean isJSONNull() {
        Log.d(TAG, "isJSONNull " + "full: " + String.valueOf(mCovidJSONDataToday == null || mCovidJSONDataYesterday == null) + " " +
        "today: " + String.valueOf(mCovidJSONDataToday == null) + " " + "yest: " + String.valueOf(mCovidJSONDataYesterday == null));
        return mCovidJSONDataToday == null || mCovidJSONDataYesterday == null;
    }

    private boolean dataSameAsStored() {
        String data = DescendantSystemUIUtils.getSystemSettingString("covid_data", mContext);
        if (data != null) {
            String[] array = data.split("]");
            if (array.length != CORRECT_LENGTH) return false;
            Log.d(TAG, "dataSameAsStored: returning " + String.valueOf(array[5].equals(testNumber(true))));
            return array[5].equals(testNumber(true));
        } else {
            return false;
        }
    }

    /* Format and utils */
    private String formatInteger(int toFormat) {
        DecimalFormat df = new DecimalFormat("###,###,###");
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return df.format(toFormat);
    }

    private String currentDate(boolean isToday) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM");
        LocalDateTime now = LocalDateTime.now();
        int day = now.getDayOfMonth();
        if (!isToday) day = day - 1;
        String time = day + " " + String.valueOf(dtf.format(now));
        return time;
    }

    private String stringFormattedData() {
        Log.d(TAG, "stringformattedData " + String.valueOf(todayCases(true) + "]"  + todayDeaths(true) + "]" + totalCases(true) + "]" + criticalCases(true) + "]" + countryName() + "]" + testNumber(true) + "]" + currentDate(true)));
        return todayCases(true) + "]"  + todayDeaths(true) + "]" + totalCases(true) + "]" + criticalCases(true) + "]" + countryName() + "]" + testNumber(true) + "]" + currentDate(true); /*+ "]" +
                   diffRetrieve("tests") + "]" + diffRetrieve("deaths") + "]" + diffRetrieve("cases") + "]"  + todayRecovered();*/
    }

    private String stringFormattedDataYesterday() {
        return todayCases(false) + "]"  + todayDeaths(false) + "]" + totalCases(false) + "]" + criticalCases(false) + "]" + countryName() + "]" + testNumber(false) + "]" + currentDate(false); /*+ "]" +
                   diffRetrieve("tests") + "]" + diffRetrieve("deaths") + "]" + diffRetrieve("cases") + "]"  + todayRecovered();*/
    }

    private String countryISO() {
        /* TODO: failsafe in case of null*/
        String cISO = mTelephonyManager.getNetworkCountryIso();
        if (cISO == null || cISO.length() == 0) {
            String data = DescendantSystemUIUtils.getSystemSettingString("weather_data", mContext);
            String[] array = data.split(",");
            if (array.length > 0 && array[1] != ERROR) {
                return array[1];
            } else {
                return ERROR;
            }
        }
        return cISO;
    }

    private String countryName() {
        if (countryISO() != "-255") {
            Locale loc = new Locale("",countryISO());
            return loc.getDisplayCountry();
        } else {
            return ERROR;
        }
    }

    /* Observer */
    private CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
    private class CustomSettingsObserver extends ContentObserver {

        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.COVID_SIGNALED_UPDATE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(Settings.System.COVID_SIGNALED_UPDATE))) {
                signaledChange();
            }
        }

        public void update() {
            signaledChange();
        }

        private void signaledChange() {
            connectAndGather("today");
            connectAndGather("yesterday");
        }
    }
}
