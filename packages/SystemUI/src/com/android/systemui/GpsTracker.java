package com.android.systemui;

import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import com.android.systemui.WeatherHttpClient;

public class GpsTracker extends Service implements LocationListener {
    private final Context mContext;
    private final String TAG = "GpsTracker";
    private final String EMPTY_STRING = "";
    // flag for GPS status
    boolean mIsGPSEnabled = false;

    // flag for network status
    boolean mIsNetworkEnabled = false;

    // flag for GPS status
    boolean mCanGetLocation = false;

    Location mLocation; // location
    double mLatitude; // latitude
    double mLongitude; // longitude

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1000; // 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 120;//1000 * 60 * 1; // 1 minute

    // Declaring a Location Manager
    protected LocationManager mLocationManager;
    // Listener for offloading location callbacks to
    private LocationListener mLocationListener;

    // Geocoder
    private Geocoder mGeocoder;

    public GpsTracker(Context context) {
        this.mContext = context;
        mGeocoder = new Geocoder(context);
    }

    public void setLocationListener(LocationListener locationListener) {
        this.mLocationListener = locationListener;
    }

    public Location getLocation() {
        try {
            mLocationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);

            // getting GPS status
            mIsGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            mIsNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!mIsGPSEnabled && !mIsNetworkEnabled) {
                // no network provider is enabled
            } else {
                this.mCanGetLocation = true;
                // First get location from Network Provider
                if (mIsNetworkEnabled) {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    Log.d(TAG, "Network enabled");
                    if (mLocationManager != null) {
                        mLocation = mLocationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                        if (mLocation != null) {
                            mLatitude = mLocation.getLatitude();
                            mLongitude = mLocation.getLongitude();
                            return mLocation;
                        }
                    }
                }

                // if GPS Enabled get lat/long using GPS Services
                if (mIsGPSEnabled) {
                    if (mLocation == null) {
                        //check the network permission
                        mLocationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                        Log.d(TAG, "GPS Enabled");
                        if (mLocationManager != null) {
                            mLocation = mLocationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                            if (mLocation != null) {
                                mLatitude = mLocation.getLatitude();
                                mLongitude = mLocation.getLongitude();
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return mLocation;
    }

    /**
     * Stop using GPS listener
     * Calling this function will stop using GPS in your app
     * */

    public void stopUsingGPS(){
        if(mLocationManager != null){
            mLocationManager.removeUpdates(GpsTracker.this);
        }
    }

    /**
     * Function to get latitude
     * */

    public double getLatitude(){
        if(mLocation != null){
            mLatitude = mLocation.getLatitude();
        }

        // return latitude
        return mLatitude;
    }

    /**
     * Function to get longitude
     * */

    public double getLongitude(){
        if(mLocation != null){
            mLongitude = mLocation.getLongitude();
        }

        // return longitude
        return mLongitude;
    }

    /**
     * Function to check GPS/wifi enabled
     * @return boolean
     * */

    public boolean canGetLocation() {
        return this.mCanGetLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "value of mLocList: " + String.valueOf(mLocationListener));
        if (mLocationListener == null) {
            return;
        }
        mLocationListener.onLocationChanged(mLocation);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "value of mLocList: " + String.valueOf(mLocationListener));
        if (mLocationListener != null) {
            mLocationListener.onProviderEnabled(provider);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "value of mLocList: " + String.valueOf(mLocationListener));
        if (mLocationListener != null) {
            mLocationListener.onProviderEnabled(provider);
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public String getCity() {
        try {
            return canGetLocation() ? mGeocoder.getFromLocation(getLatitude(), getLongitude(), 1).get(0).getLocality() : EMPTY_STRING;
        } catch (Throwable t) {
            return EMPTY_STRING;
        }
    }

    public String getCountry() {
        try {
            return canGetLocation() ? mGeocoder.getFromLocation(getLatitude(), getLongitude(), 1).get(0).getCountryCode() : EMPTY_STRING;
        } catch (Throwable t) {
            return EMPTY_STRING;
        }
    }

    public boolean areProvidersEnabled() {
        if (mLocationManager != null)
            return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        else
            return false;
    }
}