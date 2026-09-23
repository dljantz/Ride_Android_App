package com.jantztechnologies.ride2;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {

    private static final String PREFS_NAME = "ride_user_preferences";

    private static final String KEY_AUTOPAUSE = "autopause_enabled";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_MAP_STYLE = "map_style";
    private static final String KEY_DISTANCE_UNITS = "distance_units";
    private static final String KEY_LAST_LAUNCH_TIMESTAMP = "last_launch_timestamp";

    private static volatile PreferencesManager instance;
    private final SharedPreferences sharedPreferences;

    private PreferencesManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static PreferencesManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PreferencesManager.class) {
                if (instance == null) {
                    instance = new PreferencesManager(context);
                }
            }
        }
        return instance;
    }

    public boolean isAutopauseEnabled() {
        return sharedPreferences.getBoolean(KEY_AUTOPAUSE, true);
    }

    public void setAutopauseEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_AUTOPAUSE, enabled).apply();
    }

    public boolean isKeepScreenOnEnabled() {
        return sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public void setKeepScreenOnEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    public int getMapStyle() {
        return sharedPreferences.getInt(KEY_MAP_STYLE, App.MAP_STYLE_DARK);
    }

    public void setMapStyle(int style) {
        sharedPreferences.edit().putInt(KEY_MAP_STYLE, style).apply();
    }

    public int getDistanceUnits() {
        return sharedPreferences.getInt(KEY_DISTANCE_UNITS, App.IMPERIAL_UNITS);
    }

    public void setDistanceUnits(int units) {
        sharedPreferences.edit().putInt(KEY_DISTANCE_UNITS, units).apply();
    }

    public long getLastLaunchTimestamp() {
        return sharedPreferences.getLong(KEY_LAST_LAUNCH_TIMESTAMP, -1);
    }

    public void setLastLaunchTimestamp(long timestamp) {
        sharedPreferences.edit().putLong(KEY_LAST_LAUNCH_TIMESTAMP, timestamp).apply();
    }
}
