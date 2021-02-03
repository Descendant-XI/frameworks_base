package com.android.systemui;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.InterruptedException;
import java.lang.StringBuffer;
import java.util.Date;
import java.util.Locale;

import android.location.Location;
import android.location.LocationListener;

import org.json.JSONObject;
import org.json.JSONException;

import com.android.systemui.DescendantSystemUIUtils;
import com.android.systemui.R;

public class WeatherHttpClient {
    private static String TAG = "WeatherHTTPClient";
    private static String BASE_URL = "https://api.openweathermap.org/data/2.5/weather?";
    private static String ERROR = "??";
    private static String CELSIUS = "°C ";
    private static String FAHRENHEIT = "°F ";
    private static String EMPTY_STRING = "";
    private static String APPID = "&appid=8ad58266c309101d6744d570c25f640b";
    private static String UNITS = "&units=metric";
    private static String LANG = "&lang=";
    private static int ERROR_INT = 7777;
    private Location mLocation;
    private JSONObject mWeatherJSON;
    private JSONObject mWeatherJSONClouds;
    private JSONObject mWeatherJSONDetails;
    private JSONObject mWeatherJSONMain;
    private JSONObject mWeatherJSONWind;
    private Context mContext;
    private GpsTracker mGpsTracker;
    private JSONObject mData;

    private double mLatitude;
    private double mLongitude;

    private String mCountry;
    private String mCity;

    public WeatherHttpClient(Context context) {
        mGpsTracker = new GpsTracker(context);
        mContext = context;
    }

    public void init(Location location) {
        mLocation = location == null ? mGpsTracker.getLocation() : location;
        if (mLocation != null) {
            mLatitude = mGpsTracker.getLatitude();
            mLongitude = mGpsTracker.getLongitude();
            Log.d(TAG, "latitude:" + mLatitude);
            Log.d(TAG, "longitude:" + mLongitude);
            mCity = mGpsTracker.getCity();
            mCountry = mGpsTracker.getCountry();
            mWeatherJSON = getWeatherData();
            if (isJSONnull()) return;
            try {
                mWeatherJSONDetails = mWeatherJSON.getJSONArray("weather").getJSONObject(0);
                mWeatherJSONMain = mWeatherJSON.getJSONObject("main");
                mWeatherJSONWind= mWeatherJSON.getJSONObject("wind");
                mWeatherJSONClouds = mWeatherJSON.getJSONObject("clouds");
            } catch (JSONException e) {
            }
        }
    }

    public void setLocationListener(LocationListener LocationListener) {
        Log.d(TAG, "value of LocationListner: " + String.valueOf(LocationListener));
        Log.d(TAG, "value of mGpsTracker: " + String.valueOf(mGpsTracker));
        mGpsTracker.setLocationListener(LocationListener);
    }

    private JSONObject getWeatherData() {
        if (mLocation == null) {
            Log.d("Weather", "getWeatherData, location is null");
            init(null);
            return null;
        }
        Thread h = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection con = null ;
                    InputStream is = null;
                    String longitude = "&lon=" +  mLongitude;
                    String latitude = "&lat=" + mLatitude;
                    con = (HttpURLConnection) ( new URL(BASE_URL + latitude + longitude + APPID + UNITS + LANG + Locale.getDefault().getLanguage())).openConnection();
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
                    mData = data;
                    Log.d(TAG, "data value " + data.toString());
                    } catch(Throwable t) {
                        Log.d(TAG, "throwable");
                        t.printStackTrace();
                        mData = null;
                    }
                }
            });
            h.start();
            try {
                h.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "INTERRUPTED");
            }
        return mData;
    }

    public String getTemperature() {
        if (isJSONnull()) return ERROR;
        try {
            return getMetrics() == FAHRENHEIT ? String.format("%.0f", reCalcF(mWeatherJSONMain.getDouble("temp"))) + "" + getMetrics() : String.format("%.0f", mWeatherJSONMain.getDouble("temp")) + "" + getMetrics();
        } catch (JSONException e) {
        }
        return ERROR;
    }

    public String getTemperatureFeel() {
        try {
            if (!isJSONnull()) {
                if (getMetrics() == FAHRENHEIT) {
                    return String.format("%.0f", reCalcF(mWeatherJSONMain.getDouble("feels_like"))) + "" + getMetrics();
                } else {
                    return String.format("%.0f", mWeatherJSONMain.getDouble("feels_like")) + "" + getMetrics();
                }
            } else {
                return EMPTY_STRING;
            }
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public String getMaxTemp() {
        try {
            if (!isJSONnull()) {
                if (getMetrics() == FAHRENHEIT) {
                    return String.format("%.0f", reCalcF(mWeatherJSONMain.getDouble("temp_max"))) + "" + getMetrics();
                } else {
                    return String.format("%.0f", mWeatherJSONMain.getDouble("temp_max")) + "" + getMetrics();
                }
            } else {
                return EMPTY_STRING;
            }
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public String getMinTemp() {
        try {
            if (!isJSONnull()) {
                if (getMetrics() == FAHRENHEIT) {
                    return String.format("%.0f", reCalcF(mWeatherJSONMain.getDouble("temp_min"))) + "" + getMetrics();
                } else {
                    return String.format("%.0f", mWeatherJSONMain.getDouble("temp_min")) + "" + getMetrics();
                }
            } else {
                return EMPTY_STRING;
            }
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public String getHumidity() {
        try {
            return isJSONnull() ? EMPTY_STRING : mWeatherJSONMain.getInt("humidity") + "%";
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public String getWindSpeed() {
        String speed = getMetrics() == CELSIUS ? "m/s" : "mph";
        try {
            return isJSONnull() ? EMPTY_STRING : mWeatherJSONWind.getDouble("speed") + "" + speed;
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public int getWeatherIcon() {
        if (isJSONnull()) return R.drawable.weather_error;
        int weatherIcon = R.drawable.weather_error;
        try {
            weatherIcon = mWeatherJSONDetails.getInt("id");
        } catch (JSONException e) {
        }
        if (weatherIcon == 800) {
            return isDaylight() ? R.drawable.weather_sunny_day : R.drawable.weather_sunny_night;
        }
        weatherIcon = weatherIcon / 100;
        switch (weatherIcon) {
            case 2: return R.drawable.weather_thunder;
            case 3: return R.drawable.weather_drizzle;
            case 5: return R.drawable.weather_rainy;
            case 7: return R.drawable.weather_foggy;
            case 8: return R.drawable.weather_cloudy;
        }
        return R.drawable.weather_sunny_day;
    }

    private String getMetrics() {
        /* check for user settings, implement settings change listener, reassign values*/
        return !DescendantSystemUIUtils.settingStatusBoolean("weather_metrics", mContext) ? CELSIUS : FAHRENHEIT;
    }

    public boolean isDaylight() {
        if (isJSONnull()) return true;
        Date date = new Date();
        long sunrise = 0;
        long sunset = 0;
        try {
            sunrise = mWeatherJSON.getJSONObject("sys").getLong("sunrise") * 1000;
            sunset = mWeatherJSON.getJSONObject("sys").getLong("sunset") * 1000;
        } catch (JSONException e) {
        }
        return date.getTime() >= sunrise && date.getTime() < sunset ? true : false;
    }

    private double reCalcF(double c) {
        return c * 1.8 + 32;
    }

    public String getCity() {
        return mCity == null ? EMPTY_STRING : mCity;
    }

    public String getCountry() {
        return mCountry == null ? EMPTY_STRING : mCountry;
    }

    public String getDescription() {
        try {
            return isJSONnull() ? EMPTY_STRING : mWeatherJSONDetails.getString("description");
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public String getVisibility() {
        return isJSONnull() ? EMPTY_STRING : evaluateVisibility();
    }

    public String getCloudiness() {
        try {
            return isJSONnull() ? EMPTY_STRING : mWeatherJSONClouds.getInt("all") + "%";
        } catch (JSONException e) {
            Log.d(TAG, "JsonEx: " + String.valueOf(e));
        }
        return EMPTY_STRING;
    }

    public String getWindDegrees() {
        try {
            return isJSONnull() ? EMPTY_STRING : mWeatherJSONWind.getInt("deg") + "°";
        } catch (JSONException e) {
        }
        return EMPTY_STRING;
    }

    public boolean canGetWeather() {
        return mGpsTracker.canGetLocation();
    }

    public boolean isJSONnull() {
        return mWeatherJSON == null;
    }

    public void stopUsingGPS() {
        mGpsTracker.stopUsingGPS();
    }

    public boolean isItFailing() {
        return getTemperature() == ERROR;
    }

    public boolean areProvidersEnabled() {
        return mGpsTracker != null ? mGpsTracker.areProvidersEnabled() : false;
    }

    private String evaluateVisibility() {
        int meters;
        try {
            meters = mWeatherJSON.getInt("visibility");
        } catch (JSONException e) {
            meters = 0;
        }

        if (getMetrics() == CELSIUS) {
            return meters >= 100 ? (meters / 1000) + "km" : meters + "m";
        } else {
            return String.format("%.0f", meters * 3.28) + "ft";
        }
    }
}
