/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.Animator;
import android.annotation.ColorInt;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.text.format.DateUtils;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.settingslib.Utils;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.Dependency;
import com.android.systemui.DescendantSystemUIUtils;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.Dependency;
import com.android.systemui.DualToneHandler;
import com.android.systemui.GpsTracker;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.qs.QSDetail.Callback;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.qs.carrier.QSCarrierGroup;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusBarWindowView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.util.RingerModeTracker;
import com.android.systemui.WeatherWidget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View that contains the top-most bits of the screen (primarily the status bar with date, time, and
 * battery) and also contains the {@link QuickQSPanel} along with some of the panel's inner
 * contents.
 */
public class QuickStatusBarHeader extends RelativeLayout implements
        View.OnClickListener, NextAlarmController.NextAlarmChangeCallback,
        ZenModeController.Callback, LifecycleOwner {
    private static final String TAG = "QuickStatusBarHeader";
    private static final boolean DEBUG = false;

    /** Delay for auto fading out the long press tooltip after it's fully visible (in ms). */
    private static final long AUTO_FADE_OUT_DELAY_MS = DateUtils.SECOND_IN_MILLIS * 6;
    private static final int FADE_ANIMATION_DURATION_MS = 300;
    private static final int TOOLTIP_NOT_YET_SHOWN_COUNT = 0;
    public static final int MAX_TOOLTIP_SHOWN_COUNT = 2;

    private final SysuiColorExtractor mColorExtractor = Dependency.get(SysuiColorExtractor.class);
    private SysuiColorExtractor.OnColorsChangedListener mOnColorsChangedListener =
            (colorExtractor, which) -> {
                final boolean useDarkText = mColorExtractor.getNeutralColors().supportsDarkText();
                updateDecorViews(useDarkText);
            };
    private final Handler mHandler = new Handler();
    private final NextAlarmController mAlarmController;
    private final ZenModeController mZenController;
    private final StatusBarIconController mStatusBarIconController;
    private final ActivityStarter mActivityStarter;
    private ScheduledExecutorService mScheduleUpdate;
    private DeviceProvisionedController mDeviceProvisionedController;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mListening;
    private boolean mQsDisabled;
    private boolean mIsLandscape;
    private boolean mUsingLightTheme;

    private QSCarrierGroup mCarrierGroup;
    protected QuickQSPanel mHeaderQsPanel;
    protected QSTileHost mHost;
    private TouchAnimator mAAQuickQsBrightness;
    private TintedIconManager mIconManager;
    //private TouchAnimator mStatusIconsAlphaAnimator;
    private TouchAnimator mHeaderTextContainerAlphaAnimator;
    private TouchAnimator mPrivacyChipAlphaAnimator;
    private DualToneHandler mDualToneHandler;
    private final CommandQueue mCommandQueue;

    private View mSystemIconsView;
    private View mHeaderTextContainerView;
    private StatusIconContainer mIconContainer;

    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private ImageView mNextAlarmIcon;
    private ImageView mEdit;
    private ImageView mSettings;
    private ImageView mWeatherIcon;
    private RelativeLayout mWeatherWidget;
    private String mCityName;
    private WeatherWidget mWeatherWidgetClass;

    /** {@link TextView} containing the actual text indicating when the next alarm will go off. */
    private TextView mNextAlarmTextView;
    private View mNextAlarmContainer;
    private View mStatusSeparator;
    private ImageView mRingerModeIcon;
    private TextView mRingerModeTextView;
    private View mRingerContainer;
    private TextClock mClockView;
    private DateView mDateView;
    private TextView mWeatherDegrees;
    private TextView mWeatherCity;
    private TextView mQSBHEventListener;
    private boolean mAlarmVisibleNow = true;
    private boolean mEventsVisibleNow = false;
    private boolean mEventsCycleStarted = false;
    private BatteryMeterView mBatteryRemainingIcon;
    private RelativeLayout mEventPill;
    private RingerModeTracker mRingerModeTracker;

    private View mQuickQsBrightness;
    private BrightnessController mBrightnessController;
    private boolean mIsQuickQsBrightnessEnabled;
    private boolean mIsQsAutoBrightnessEnabled;

    // Used for RingerModeTracker
    private final LifecycleRegistry mLifecycle = new LifecycleRegistry(this);

    private boolean mHasTopCutout = false;
    private int mStatusBarPaddingTop = 0;
    private int mRoundedCornerPadding = 0;
    private final int BRIGHTNESS_EXP_PORTRAIT = 380;
    private final int BRIGHTNESS_EXP_LANDSCAPE = 0;
    private int mQuickQsBrightnessExpFrac;
    private int mContentMarginStart;
    private int mContentMarginEnd;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mExpandedHeaderAlpha = 1.0f;
    private float mKeyguardExpansionFraction;

    private String m24Clock;
    private String m12Clock;
    private String m24ClockLand;
    private String m12ClockLand;
    private String m24easterClock;
    private String m12easterClock;

    @Inject
    public QuickStatusBarHeader(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            NextAlarmController nextAlarmController, ZenModeController zenModeController,
            StatusBarIconController statusBarIconController,
            ActivityStarter activityStarter,
            CommandQueue commandQueue, RingerModeTracker ringerModeTracker) {
        super(context, attrs);
        mDeviceProvisionedController = Dependency.get(DeviceProvisionedController.class);
        mAlarmController = nextAlarmController;
        mZenController = zenModeController;
        mStatusBarIconController = statusBarIconController;
        mActivityStarter = activityStarter;
        mDualToneHandler = new DualToneHandler(
                new ContextThemeWrapper(context, R.style.QSHeaderTheme));
        mCommandQueue = commandQueue;
        mRingerModeTracker = ringerModeTracker;
        mQuickQsBrightnessExpFrac = BRIGHTNESS_EXP_PORTRAIT;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mSystemIconsView = findViewById(R.id.quick_status_bar_system_icons);
        mIconContainer = findViewById(R.id.statusIcons);
        mIconContainer.setOnClickListener(this);
        // Ignore privacy icons because they show in the space above QQS
        mIconContainer.addIgnoredSlots(getIgnoredIconSlots());
        mIconContainer.setShouldRestrictIcons(false);
        mIconManager = new TintedIconManager(mIconContainer, mCommandQueue);

        mQuickQsBrightness = findViewById(R.id.quick_qs_brightness_bar);
        mBrightnessController = new BrightnessController(getContext(),
                mQuickQsBrightness.findViewById(R.id.brightness_slider),
                Dependency.get(BroadcastDispatcher.class));

        // Views corresponding to the header info section (e.g. ringer and next alarm).
        mHeaderTextContainerView = findViewById(R.id.header_text_container);
        mStatusSeparator = findViewById(R.id.status_separator);
        mNextAlarmIcon = findViewById(R.id.next_alarm_icon);
        mNextAlarmTextView = findViewById(R.id.next_alarm_text);
        mNextAlarmContainer = findViewById(R.id.alarm_container);
        mNextAlarmContainer.setOnClickListener(this::onClick);
        mRingerModeIcon = findViewById(R.id.ringer_mode_icon);
        mRingerModeTextView = findViewById(R.id.ringer_mode_text);
        mRingerContainer = findViewById(R.id.ringer_container);
        mRingerContainer.setOnClickListener(this::onClick);
        mCarrierGroup = findViewById(R.id.carrier_group);
        mEdit = findViewById(R.id.qqs_edit);
        mEdit.setOnClickListener(view ->
                mActivityStarter.postQSRunnableDismissingKeyguard(() ->
                        showQSEdit(view)));
        mSettings = findViewById(R.id.qqs_settings);
        mSettings.setOnClickListener(this);
        mWeatherIcon = findViewById(R.id.weather_icon);
        updateResources();

        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);

        // Set light text on the header icons because they will always be on a black background
        applyDarkness(R.id.clock, tintArea, 0, DarkIconDispatcher.DEFAULT_ICON_TINT);

        // Set the correct tint for the status icons so they contrast
        mIconManager.setTint(fillColor);
        mNextAlarmIcon.setImageTintList(ColorStateList.valueOf(fillColor));
        mRingerModeIcon.setImageTintList(ColorStateList.valueOf(fillColor));

        mClockView = findViewById(R.id.clock);
        m24ClockLand = "<strong>HH</strong>:mm";
        m12ClockLand = "<strong>hh</strong>:mm";
        m24Clock = "<strong>HH</strong><br>mm";
        m12Clock = "<strong>hh</strong><br>mm";
        m24easterClock = "<strong><font color='#0069ba'>HH</font></strong><br>mm";
        m12easterClock = "<strong><font color='#0069ba'>hh</font></strong><br>mm";
        mClockView.setFormat12Hour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 11 ? Html.fromHtml(m12easterClock) : Html.fromHtml(m12Clock));
        mClockView.setFormat24Hour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 11 ? Html.fromHtml(m24easterClock) : Html.fromHtml(m24Clock));
        mClockView.setOnClickListener(this);
        mDateView = findViewById(R.id.date);
        mWeatherCity = findViewById(R.id.weather_city);
        mWeatherDegrees = findViewById(R.id.weather_degrees);
        mWeatherIcon = findViewById(R.id.weather_icon);
        mWeatherWidget = findViewById(R.id.weather_widget);
        mWeatherWidget.setOnClickListener(this);
        mWeatherWidgetClass = new WeatherWidget(mContext, mWeatherWidget, mWeatherDegrees, mWeatherIcon, mWeatherCity);
        mEventPill = findViewById(R.id.event_pill);
        mQSBHEventListener = findViewById(R.id.event_listener);
        mQSBHEventListener.setOnClickListener(this);

        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        mBatteryRemainingIcon.setOnClickListener(this);
        // Don't need to worry about tuner settings for this icon
        mBatteryRemainingIcon.setIgnoreTunerUpdates(true);
        // QS will always show the estimate, and BatteryMeterView handles the case where
        // it's unavailable or charging
        mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        mRingerModeTextView.setSelected(true);
        mNextAlarmTextView.setSelected(true);
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    private List<String> getIgnoredIconSlots() {
        ArrayList<String> ignored = new ArrayList<>();
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_camera));
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_microphone));
        ignored.add("zen");
        ignored.add("volume");
        ignored.add("location");
        ignored.add("bluetooth");
        ignored.add("alarm_clock");
        ignored.add("speakerphone");
        return ignored;
    }

    private void updateStatusText() {
        boolean changed = updateRingerStatus() || updateAlarmStatus();

        if (changed) {
            boolean alarmVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
            boolean ringerVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
            mStatusSeparator.setVisibility(alarmVisible && ringerVisible ? View.VISIBLE
                    : View.GONE);
        }
    }

    private boolean updateRingerStatus() {
        boolean isOriginalVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
        CharSequence originalRingerText = mRingerModeTextView.getText();

        boolean ringerVisible = false;
        if (!ZenModeConfig.isZenOverridingRinger(mZenController.getZen(),
                mZenController.getConsolidatedPolicy()) && mZenController.getZen() != 1 ) {
            if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_vibrate);
                mRingerModeTextView.setText(R.string.qs_status_phone_vibrate);
                ringerVisible = true;
            } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_mute);
                mRingerModeTextView.setText(R.string.qs_status_phone_muted);
                ringerVisible = true;
            }
        } else {
            mRingerModeIcon.setImageResource(com.android.internal.R.drawable.ic_zen_24dp);
            ringerVisible = true;
        }
        mRingerModeIcon.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);
        mRingerModeTextView.setVisibility(View.GONE);
        mRingerContainer.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != ringerVisible ||
                !Objects.equals(originalRingerText, mRingerModeTextView.getText());
    }

    private boolean updateAlarmStatus() {
        boolean isOriginalVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
        CharSequence originalAlarmText = mNextAlarmTextView.getText();

        boolean alarmVisible = false;
        if (mNextAlarm != null) {
            alarmVisible = true;
            mNextAlarmTextView.setText(formatNextAlarm(mNextAlarm));
        }
        mNextAlarmIcon.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmTextView.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmContainer.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != alarmVisible ||
                !Objects.equals(originalAlarmText, mNextAlarmTextView.getText());
    }

    private void applyDarkness(int id, Rect tintArea, float intensity, int color) {
        View v = findViewById(id);
        if (v instanceof DarkReceiver) {
            ((DarkReceiver) v).onDarkChanged(tintArea, intensity, color);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        mIsLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        mQuickQsBrightnessExpFrac =mIsLandscape ? BRIGHTNESS_EXP_LANDSCAPE : BRIGHTNESS_EXP_PORTRAIT;
        if (mIsLandscape)
            mWeatherWidgetClass.dismissDialog();
        updateStyles(mIsLandscape);
        handleEvents();
    }

    private void updateStyles(boolean isLandscape) {
        mClockView.setFormat12Hour(isLandscape ? Html.fromHtml(m12ClockLand) : Html.fromHtml(m12Clock));
        mClockView.setFormat24Hour(isLandscape ? Html.fromHtml(m24ClockLand) : Html.fromHtml(m24Clock));
        mDateView.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        mClockView.setTextAppearance(mContext,isLandscape ? R.style.TextAppearance_ClockQSHeaderLand : R.style.TextAppearance_ClockQSHeader);
        mDateView.setTextAppearance(mContext,isLandscape ? R.style.TextAppearance_DateQSHeaderLand : R.style.TextAppearance_DateQSHeader);
        mWeatherCity.setTextAppearance(mContext,isLandscape ? R.style.TextAppearance_WeatherCityQSHeaderLand : R.style.TextAppearance_WeatherCityQSHeader);
        mWeatherDegrees.setTextAppearance(mContext,isLandscape ? R.style.TextAppearance_WeatherDegreesQSHeaderLand : R.style.TextAppearance_WeatherDegreesQSHeader);
        mWeatherCity.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        mWeatherDegrees.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        mWeatherIcon.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        if (isLandscape) {
            mSettings.setPadding(0,mContext.getResources().getDimensionPixelSize(R.dimen.qqs_edit_padding_bottom_land),0,0);
            mEdit.setPadding(0,mContext.getResources().getDimensionPixelSize(R.dimen.qqs_edit_padding_bottom_land),0,0);
        } else {
            mSettings.setPadding(0,0,0,mContext.getResources().getDimensionPixelSize(R.dimen.qqs_edit_padding_bottom));
            mEdit.setPadding(0,0,0,mContext.getResources().getDimensionPixelSize(R.dimen.qqs_edit_padding_bottom));
        }

    }

    public void updateDecorViews(boolean lightTheme) {
        if (lightTheme == mUsingLightTheme) {
            return;
        }
        mUsingLightTheme = lightTheme;
        Context context = new ContextThemeWrapper(mContext,
                lightTheme ? R.style.Theme_SystemUI_Light : R.style.Theme_SystemUI);
        final @ColorInt int textColor =
                Utils.getColorAttrDefaultColor(context, R.attr.wallpaperTextColor);
        mClockView.setTextColor(textColor);
        mDateView.setTextColor(textColor);
        mWeatherDegrees.setTextColor(textColor);
        mWeatherCity.setTextColor(textColor);
        mEdit.setImageTintList(ColorStateList.valueOf(textColor));
        mSettings.setImageTintList(ColorStateList.valueOf(textColor));
        mWeatherIcon.setImageTintList(ColorStateList.valueOf(textColor));
    }

    private void showQSEdit(View view) {
        mQsPanel.showEdit(view);
    }

    private void startSettingsActivity() {
        if (mQsPanel.isShowingCustomize()) {
            mQsPanel.onCollapse();
        }
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    private void handleEvents() {
        boolean eventsAvailability = EventsProvider.areThereCalendarEvents(mContext);
        boolean alarmAvailability = EventsProvider.areThereAlarms(mContext);
        if (alarmAvailability && eventsAvailability) {
            if (!mListening) {
                eventsCycleStart();
                return;
            } else {
                eventsCycleStop();
            }
        } else if (alarmAvailability) {
            mQSBHEventListener.setCompoundDrawablesWithIntrinsicBounds(R.drawable.alarm_active, 0, 0, 0);
            mQSBHEventListener.setCompoundDrawablePadding(4);
            mQSBHEventListener.setText(formatNextAlarm(mNextAlarm));
            mQSBHEventListener.setTextColor(Utils.getColorAttrDefaultColor(getContext(),android.R.attr.colorForeground));
            eventsCycleStop();
        } else if (eventsAvailability) {
            mQSBHEventListener.setCompoundDrawablesWithIntrinsicBounds(R.drawable.calendar_active, 0, 0, 0);
            mQSBHEventListener.setCompoundDrawablePadding(4);
            mQSBHEventListener.setText(R.string.qsbh_calendar_events);
            mQSBHEventListener.setTextColor(Utils.getColorAttrDefaultColor(getContext(),android.R.attr.colorForeground));
            eventsCycleStop();
        } else if (!alarmAvailability || !eventsAvailability) {
            mQSBHEventListener.setCompoundDrawablesWithIntrinsicBounds(R.drawable.calendar_unactive, 0, 0, 0);
            mQSBHEventListener.setCompoundDrawablePadding(4);
            mQSBHEventListener.setText(R.string.qsbh_calendar_noevents);
            mQSBHEventListener.setTextColor(Utils.getColorAttrDefaultColor(getContext(),android.R.attr.colorForeground));
            eventsCycleStop();
        }
    }

    private Handler eventsCycleHandler = new Handler();
    private Runnable eventsCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (mEventsVisibleNow) {
                mEventsVisibleNow = false;
                mAlarmVisibleNow = true;
                handleEventListenerAnimations(mQSBHEventListener, getResources().getString(R.string.qsbh_alarm_events)  + " " + mNextAlarmTextView.getText(), true);
                if (mEventsCycleStarted)
                    eventsCycleStart();
                } else {
                    mAlarmVisibleNow = false;
                    mEventsVisibleNow = true;
                    handleEventListenerAnimations(mQSBHEventListener, getResources().getString(R.string.qsbh_calendar_events), false);
                    if (mEventsCycleStarted)
                       eventsCycleStart();
               }
        }
    };

    private void eventsCycleStop() {
        mEventsCycleStarted = false;
        mAlarmVisibleNow = false;
        mEventsVisibleNow = true;
        eventsCycleHandler.removeCallbacks(eventsCycleRunnable);
    }

    private void eventsCycleStart() {
        mEventsCycleStarted = true;
        eventsCycleHandler.postDelayed(eventsCycleRunnable, 2500);
    }

    private void handleEventListenerAnimations(TextView textView, String message, boolean state) {
        textView.animate().setDuration(400).setListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                textView.setText(message);
                if (state)
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.alarm_active, 0, 0, 0);
                else
                    textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.calendar_active, 0, 0, 0);
                textView.setCompoundDrawablePadding(4);
                textView.animate().setListener(null).setDuration(400).alpha(1);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        }).alpha(0);
    }

    private void easterCheck() {
        if (mIsLandscape) return;
        mClockView.setFormat12Hour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 11 ? Html.fromHtml(m12easterClock) : Html.fromHtml(m12Clock));
        mClockView.setFormat24Hour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 11 ? Html.fromHtml(m24easterClock) : Html.fromHtml(m24Clock));
    }

    private BroadcastReceiver mTickReceiver = new BroadcastReceiver(){
        @Override
            public void onReceive(Context context, Intent intent) {
                    if(intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0) {
                                easterCheck();
                    }
            }
    };

    public void dismissWeatherEx() {
        mWeatherWidgetClass.dismissDialog();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    /**
     * The height of QQS should always be the status bar height + 128dp. This is normally easy, but
     * when there is a notch involved the status bar can remain a fixed pixel size.
     */
    private void updateMinimumHeight() {
        int sbHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        int qqsHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.qs_quick_header_panel_height);
        qqsHeight += mContext.getResources().getDimensionPixelSize(
                    R.dimen.brightness_mirror_height)
                    + mContext.getResources().getDimensionPixelSize(
                    R.dimen.qs_tile_margin_top);
        setMinimumHeight(sbHeight + qqsHeight);
    }

    private void updateResources() {
        Resources resources = mContext.getResources();
        updateMinimumHeight();

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);
        mStatusBarPaddingTop = resources.getDimensionPixelSize(R.dimen.status_bar_padding_top);

        // Update height for a few views, especially due to landscape mode restricting space.
        /*mHeaderTextContainerView.getLayoutParams().height =
                resources.getDimensionPixelSize(R.dimen.qs_header_tooltip_height);
        mHeaderTextContainerView.setLayoutParams(mHeaderTextContainerView.getLayoutParams());*/

        mSystemIconsView.getLayoutParams().height = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height);
        mSystemIconsView.setLayoutParams(mSystemIconsView.getLayoutParams());
            RelativeLayout.LayoutParams lpQuickQsBrightness = (RelativeLayout.LayoutParams)
        mQuickQsBrightness.getLayoutParams();
/*        lpQuickQsBrightness.setMargins(
                resources.getDimensionPixelSize(R.dimen.notification_side_paddings)
                        - resources.getDimensionPixelSize(R.dimen.status_bar_padding_start),
                0, resources.getDimensionPixelSize(R.dimen.notification_side_paddings)
                        - resources.getDimensionPixelSize(R.dimen.status_bar_padding_end),
                0);
        mQuickQsBrightness.setLayoutParams(lpQuickQsBrightness);*/
        mQuickQsBrightness.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.quick_qs_offset_height);
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);
        updateBrightnessAlphaAnimator();
        updateStatusIconAlphaAnimator();
        updateHeaderTextContainerAlphaAnimator();
    }

    private void updateBrightnessAlphaAnimator() {
        mAAQuickQsBrightness = new TouchAnimator.Builder()
                .addFloat(mQuickQsBrightness, "alpha", 1, 0, 0)
                .build();
    }

    private void updateStatusIconAlphaAnimator() {
        /*mStatusIconsAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mQuickQsStatusIcons, "alpha", 1, 0, 0)
                .build();*/
    }

    private void updateHeaderTextContainerAlphaAnimator() {
        /*mHeaderTextContainerAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mHeaderTextContainerView, "alpha", 0, 0, mExpandedHeaderAlpha)
                .build();*/
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mHeaderQsPanel.setExpanded(expanded);
        mEdit.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updateEverything();
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;
        /*if (mStatusIconsAlphaAnimator != null) {
            mStatusIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }*/
        if (mAAQuickQsBrightness != null)
            mAAQuickQsBrightness.setPosition(keyguardExpansionFraction);

        mQuickQsBrightness.setVisibility(expansionFraction == 1 ? View.INVISIBLE : View.VISIBLE);
        mQuickQsBrightness.setTranslationY(expansionFraction * mQuickQsBrightnessExpFrac);

        mEdit.setAlpha(expansionFraction);

        if (forceExpanded) {
            // If the keyguard is showing, we want to offset the text so that it comes in at the
            // same time as the panel as it slides down.
            //mHeaderTextContainerView.setTranslationY(panelTranslationY);
        } else {
            //mHeaderTextContainerView.setTranslationY(0f);
        }

        if (mHeaderTextContainerAlphaAnimator != null) {
            mHeaderTextContainerAlphaAnimator.setPosition(keyguardExpansionFraction);
            if (keyguardExpansionFraction > 0) {
                //mHeaderTextContainerView.setVisibility(VISIBLE);
            } else {
                //mHeaderTextContainerView.setVisibility(INVISIBLE);
            }
        }
        if (expansionFraction < 1 && expansionFraction > 0.99) {
            if (mHeaderQsPanel.switchTileLayout()) {
                updateResources();
            }
        }
        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        //mHeaderTextContainerView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        //mQuickQsStatusIcons.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mQuickQsBrightness.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mRingerModeTracker.getRingerModeInternal().observe(this, ringer -> {
            mRingerMode = ringer;
            updateStatusText();
        });
        mStatusBarIconController.addIconGroup(mIconManager);
        requestApplyInsets();
        mContext.registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the clock
        DisplayCutout cutout = insets.getDisplayCutout();
        Pair<Integer, Integer> cornerCutoutPadding = StatusBarWindowView.cornerCutoutMargins(
                cutout, getDisplay());
        Pair<Integer, Integer> padding =
                StatusBarWindowView.paddingNeededForCutoutAndRoundedCorner(
                        cutout, cornerCutoutPadding, -1);
        mCutOutPaddingLeft = padding.first;
        mCutOutPaddingRight = padding.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;
        updateClockPadding();
        return super.onApplyWindowInsets(insets);
    }

    private void updateClockPadding() {
        updateDecorViews(mColorExtractor.getNeutralColors().supportsDarkText());
        int clockPaddingLeft = 0;
        int clockPaddingRight = 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = lp.leftMargin;
        int rightMargin = lp.rightMargin;

        // The clock might collide with cutouts, let's shift it out of the way.
        // We only do that if the inset is bigger than our own padding, since it's nicer to
        // align with
        if (mCutOutPaddingLeft > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingLeft, mRoundedCornerPadding);
            int contentMarginLeft = isLayoutRtl() ? mContentMarginEnd : mContentMarginStart;
            clockPaddingLeft = Math.max(cutoutPadding - contentMarginLeft - leftMargin, 0);
        }
        if (mCutOutPaddingRight > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingRight, mRoundedCornerPadding);
            int contentMarginRight = isLayoutRtl() ? mContentMarginStart : mContentMarginEnd;
            clockPaddingRight = Math.max(cutoutPadding - contentMarginRight - rightMargin, 0);
        }

        mSystemIconsView.setPadding(clockPaddingLeft,
                mWaterfallTopInset + mStatusBarPaddingTop,
                clockPaddingRight,
                0);


    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        setListening(false);
        mRingerModeTracker.getRingerModeInternal().removeObservers(this);
        mStatusBarIconController.removeIconGroup(mIconManager);
        super.onDetachedFromWindow();
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        handleEvents();
        mHeaderQsPanel.setListening(listening);
        if (mHeaderQsPanel.switchTileLayout()) {
            updateResources();
        }
        mListening = listening;

        if (listening) {
            if (mWeatherWidgetClass != null) mWeatherWidgetClass.setWeatherData();
            mZenController.addCallback(this);
            mAlarmController.addCallback(this);
            mLifecycle.setCurrentState(Lifecycle.State.RESUMED);
            mBrightnessController.registerCallbacks();
        } else {
            mZenController.removeCallback(this);
            mAlarmController.removeCallback(this);
            mLifecycle.setCurrentState(Lifecycle.State.CREATED);
            mBrightnessController.unregisterCallbacks();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mClockView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mNextAlarmContainer && mNextAlarmContainer.isVisibleToUser()) {
            if (mNextAlarm.getShowIntent() != null) {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        mNextAlarm.getShowIntent());
            } else {
                Log.d(TAG, "No PendingIntent for next alarm. Using default intent");
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            }
        } else if (v == mRingerContainer && mRingerContainer.isVisibleToUser()) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Settings.ACTION_SOUND_SETTINGS), 0);
        } else if (v == mSettings) {
            if (!mDeviceProvisionedController.isCurrentUserSetup()) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                mActivityStarter.postQSRunnableDismissingKeyguard(() -> {
                });
                return;
            }
            startSettingsActivity();
        } else if (v == mQSBHEventListener) {
            if (mAlarmVisibleNow)
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            if (mEventsVisibleNow)
                Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(new Intent(
                        Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time")),0);
        } else if (v == mDateView) {
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(new Intent(
                        Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time")),0);
        } else if (v == mBatteryRemainingIcon) {
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(new Intent(
                        Intent.ACTION_POWER_USAGE_SUMMARY),0);
        } else if (v == mIconContainer) {
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(new Intent(
                        Settings.ACTION_WIRELESS_SETTINGS),0);
        } else if (v == mWeatherWidget) {
            mWeatherWidgetClass.createDialog();
        }
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        handleEvents();
        updateStatusText();
    }

    @Override
    public void onZenChanged(int zen) {
        updateStatusText();
    }

    @Override
    public void onConfigChanged(ZenModeConfig config) {
        updateStatusText();
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        setupHost(qsPanel.getHost());
    }

    public void setupHost(final QSTileHost host) {
        mHost = host;
        //host.setHeaderView(mExpandIndicator);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host, null /* No customization in header */);


        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);
        mBatteryRemainingIcon.onDarkChanged(tintArea, intensity, fillColor);
    }

    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    private String formatNextAlarm(AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = android.text.format.DateFormat
                .is24HourFormat(mContext, ActivityManager.getCurrentUser()) ? "EHm" : "Ehma";
        String pattern = android.text.format.DateFormat
                .getBestDateTimePattern(Locale.getDefault(), skeleton);
        return android.text.format.DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    public static float getColorIntensity(@ColorInt int color) {
        return color == Color.WHITE ? 0 : 1;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycle;
    }

    public void setContentMargins(int marginStart, int marginEnd) {
        mContentMarginStart = marginStart;
        mContentMarginEnd = marginEnd;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view == mHeaderQsPanel) {
                // QS panel doesn't lays out some of its content full width
                mHeaderQsPanel.setContentMargins(marginStart, marginEnd);
            } else {
                if (mQuickQsBrightness != view || mClockView != view || mEventPill != view ) {
                    MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                    lp.setMarginStart(marginStart);
                    lp.setMarginEnd(marginEnd);
                    view.setLayoutParams(lp);
                }
            }
        }
        updateClockPadding();
    }

    public void setExpandedScrollAmount(int scrollY) {
        // The scrolling of the expanded qs has changed. Since the header text isn't part of it,
        // but would overlap content, we're fading it out.
        float newAlpha = 1.0f;
        /*if (mHeaderTextContainerView.getHeight() > 0) {
            newAlpha = MathUtils.map(0, mHeaderTextContainerView.getHeight() / 2.0f, 1.0f, 0.0f,
                    scrollY);
            newAlpha = Interpolators.ALPHA_OUT.getInterpolation(newAlpha);
        }
        mHeaderTextContainerView.setScrollY(scrollY);*/
        if (newAlpha != mExpandedHeaderAlpha) {
            mExpandedHeaderAlpha = newAlpha;
            /*mHeaderTextContainerView.setAlpha(MathUtils.lerp(0.0f, mExpandedHeaderAlpha,
                    mKeyguardExpansionFraction));*/
            updateHeaderTextContainerAlphaAnimator();
        }
    }
}
