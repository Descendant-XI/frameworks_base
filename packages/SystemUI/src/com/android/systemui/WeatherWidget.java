package com.android.systemui;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.TranslateAnimation;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.DescendantSystemUIUtils;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.WeatherHttpClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeatherWidget implements LocationListener {

    private static int ANIMATION_DURATION = 350;
    private static int TWO_HOURS = 7200000;
    private static int INFO_LENGTH = 13;
    private static String TAG = "WeatherWidget";
    private Dialog mDialog;
    private boolean mCanUseStored;
    private Context mContext;
    private Handler mWeatherLongUpdateHandler = new Handler();
    private Handler mWeatherSetHandler = new Handler();
    private ImageView mWeatherIcon;
    private RelativeLayout mWeatherWidget;
    private String mCity;
    private String mCityName;
    private String mCloudiness;
    private String mCountry;
    private String mDescription;
    private String mHumidity;
    private String mMaxTemp;
    private String mMinTemp;
    private String mTemperature;
    private String mTemperatureFeel;
    private String mVisibility;
    private String mWeatherIconRes;
    private String mWindDegrees;
    private String mWindSpeed;
    private TextView mWeatherCity;
    private TextView mWeatherDegrees;
    private View mWeatherWidgetExLayout;
    private WeatherHttpClient mWeatherHttpClient;

    public WeatherWidget(Context context, RelativeLayout rl, TextView degrees, ImageView icon, TextView city) {
        Log.d(TAG, "new WeatherWidget created");
        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        IntentFilter filterBootComplete = new IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        mContext = context;
        mWeatherCity = city;
        mWeatherDegrees = degrees;
        mWeatherIcon = icon;
        mWeatherWidget = rl;
        mContext.registerReceiver(mBroadcastReceiver, filter);
        mContext.registerReceiver(mBroadcastReceiver, filterBootComplete);
        mWeatherWidgetExLayout = LayoutInflater.from(mContext).inflate(R.layout.weather_diag, null);
        scheduleWeatherDataUpdate();
    }

    public void scheduleLongUpdate() {
        Log.d(TAG, "scheduleLongUpdate");
        mWeatherLongUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scheduleWeatherDataUpdate();
                Log.d(TAG, "runnable ran in Schedule long");
            }
        }, TWO_HOURS);
    }

    public void scheduleWeatherDataUpdate() {
        Log.d(TAG, "scheduleWeatherDataUpdate");
        init();
        scheduleLongUpdate();
    }

    private void init() {
        Log.d(TAG, "init joined");
        if (mWeatherHttpClient == null) {
            Log.d(TAG, "init: mWeatherHTTPClient was null");
            mWeatherHttpClient = new WeatherHttpClient(mContext);
        }
        mWeatherHttpClient.init(null);
        mWeatherHttpClient.setLocationListener(this);
        storeWeatherData();
        setWeatherData();
    }

    public void setWeatherData() {
        Log.d(TAG, "setWeatherData");
        getStoredWeatherData();
        if (!mCanUseStored) {
            Log.d(TAG, "setWeatherData: we can't use stored values, view gone");
            mWeatherWidget.animate().alpha(0f).setDuration(ANIMATION_DURATION).start();
        } else {
            Log.d(TAG, "setWeatherData: view is going to be visible, setting data");
            Log.d(TAG, "alpha val: " + mWeatherWidget.getAlpha());
            if (mWeatherWidget.getAlpha() != 1.0) {
                mWeatherWidget.animate().alpha(1f).setDuration(ANIMATION_DURATION).withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        mWeatherSetHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mWeatherCity.setText(mDescription);
                                mWeatherDegrees.setText(mTemperature);
                                mWeatherIcon.setImageResource(Integer.valueOf(mWeatherIconRes));
                            }
                        }, 250);
                    }
                }).start();
            } else {
                mWeatherWidget.animate().alpha(0f).setDuration(175).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mWeatherWidget.animate().alpha(1f).setDuration(ANIMATION_DURATION).withStartAction(new Runnable() {
                            @Override
                            public void run() {
                                mWeatherSetHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        mWeatherCity.setText(mDescription);
                                        mWeatherDegrees.setText(mTemperature);
                                        mWeatherIcon.setImageResource(Integer.valueOf(mWeatherIconRes));
                                    }
                                }, 250);
                            }
                        }).start();
                    }
                }).start();
            }
        }
    }

    private void getWeatherDetails() {
        Log.d(TAG, "Description:" + String.valueOf(mWeatherHttpClient.getDescription()));
        Log.d(TAG, "Humidity:" + String.valueOf(mWeatherHttpClient.getHumidity()));
        Log.d(TAG, "Max temp:" + String.valueOf(mWeatherHttpClient.getMaxTemp()));
        Log.d(TAG, "Min temp:" + String.valueOf(mWeatherHttpClient.getMinTemp()));
        Log.d(TAG, "Temperature feels like:" + String.valueOf(mWeatherHttpClient.getTemperatureFeel()));
        Log.d(TAG, "Wind speed:" + String.valueOf(mWeatherHttpClient.getWindSpeed()));
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "location has changed");
        mWeatherLongUpdateHandler.removeCallbacksAndMessages(null);
        mWeatherHttpClient.init(location);
        mWeatherHttpClient.setLocationListener(this);
        storeWeatherData();
        setWeatherData();
        scheduleLongUpdate();
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "provider has been enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "provider has been disabled: " + provider);
    }

    public static String capitalizeString(String str) {
        String retStr = str;
        try { // We can face index out of bound exception if the string is null
            retStr = str.substring(0, 1).toUpperCase() + str.substring(1);
        } catch (Exception e) {}
        return retStr;
    }

    private void storeWeatherData() {
        String s = mWeatherHttpClient.getCity() + "," + mWeatherHttpClient.getCountry() + "," + capitalizeString(mWeatherHttpClient.getDescription()) + "," + String.valueOf(mWeatherHttpClient.getTemperature()) + "," + String.valueOf(mWeatherHttpClient.getWeatherIcon()) + "," +
                   String.valueOf(mWeatherHttpClient.getHumidity()) + "," + String.valueOf(mWeatherHttpClient.getWindSpeed()) + "," + String.valueOf(mWeatherHttpClient.getTemperatureFeel()) + "," +
                   String.valueOf(mWeatherHttpClient.getMaxTemp()) + "," + String.valueOf(mWeatherHttpClient.getMinTemp()) + "," +  String.valueOf(mWeatherHttpClient.getVisibility()) + "," +
                   String.valueOf(mWeatherHttpClient.getCloudiness()) + "," + String.valueOf(mWeatherHttpClient.getWindDegrees());
        DescendantSystemUIUtils.setSystemSettingString("weather_data", mContext, s);
    }

    private void getStoredWeatherData() {
        Log.d(TAG, "getStoredWeatherData");
        String yourString = DescendantSystemUIUtils.getSystemSettingString("weather_data", mContext);
        String[] array = yourString.split(",");
        Log.d(TAG, "getStoredWeatherData: array length:" + String.valueOf(array.length));
        if (array.length != INFO_LENGTH) {
            mCanUseStored = false;
            return;
        }
        mCanUseStored = true;
        mCity = array[0];
        mCountry = array[1];
        mDescription = array[2];
        mTemperature = array[3];
        mWeatherIconRes = array[4];
        mHumidity = array[5];
        mWindSpeed = array[6];
        mTemperatureFeel = array[7];
        mMaxTemp = array[8];
        mMinTemp = array[9];
        mVisibility = array[10];
        mCloudiness = array[11];
        mWindDegrees = array[12];
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "intent: " + intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED));
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED) || intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED))
                scheduleWeatherDataUpdate();
        }
    };

    public void createDialog() {
        if (mCanUseStored) {
            mDialog = new Dialog(mContext);
            mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mDialog.setCanceledOnTouchOutside(true);
            if(mWeatherWidgetExLayout.getParent()!=null) {
                ((ViewGroup)mWeatherWidgetExLayout.getParent()).removeView(mWeatherWidgetExLayout);

            }
            mDialog.setContentView(mWeatherWidgetExLayout);
            TextView degrees_max = mWeatherWidgetExLayout.findViewById(R.id.degrees_max);
            degrees_max.setText(mMaxTemp);
            TextView degrees_min = mWeatherWidgetExLayout.findViewById(R.id.degrees_min);
            degrees_min.setText(mMinTemp);
            TextView wind_speed = mWeatherWidgetExLayout.findViewById(R.id.wind_speed);
            wind_speed.setText(mWindSpeed);
            TextView wind_degrees = mWeatherWidgetExLayout.findViewById(R.id.wind_degrees);
            wind_degrees.setText(mWindDegrees);
            TextView feels_like = mWeatherWidgetExLayout.findViewById(R.id.feels_like);
            feels_like.setText(mTemperatureFeel);
            TextView humidity = mWeatherWidgetExLayout.findViewById(R.id.humidity);
            humidity.setText(mHumidity);
            TextView visibility = mWeatherWidgetExLayout.findViewById(R.id.visibility);
            visibility.setText(mVisibility);
            TextView cloudiness = mWeatherWidgetExLayout.findViewById(R.id.cloudiness);
            cloudiness.setText(mCloudiness);
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
            mDialog.getWindow().setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
            mDialog.getWindow().setLayout(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
            mDialog.show();
        }
    }

    public void dismissDialog() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }
}