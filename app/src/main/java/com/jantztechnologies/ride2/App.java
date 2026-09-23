package com.jantztechnologies.ride2;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class App extends Application {

    public static final String CHANNEL_ID = "foregroundServiceChannel";
    public static final String RECORDING_ACTIVITY_MESSAGE_ID = "notificationDisplay";

    public static final double UNRELIABLE_ANGLE = 36000;
    public static final int NO_ELEVATION_DATA = -9999;

    public static final int AUTOPAUSE_DISABLED = 0;
    public static final int AUTOPAUSE_ENABLED = 1;

    public static final int KEEP_SCREEN_ON_DURING_RIDES_DISABLED = 0;
    public static final int KEEP_SCREEN_ON_DURING_RIDES_ENABLED = 1;

    public static final int MAP_STYLE_DARK = 0;
    public static final int MAP_STYLE_LIGHT = 1;
    public static final int MAP_STYLE_SATELLITE = 2;
    public static final int MAP_STYLE_HYBRID = 3;

    public static final int IMPERIAL_UNITS = 0;
    public static final int METRIC_UNITS = 1;

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Recording Ride channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Notify me that a ride is being recorded");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public static Context getAppContext() {
        return instance != null ? instance.getApplicationContext() : null;
    }
}
