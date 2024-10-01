package com.jantztechnologies.ride;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

// gets called when the app is run for the very first time, I think
public class App extends Application {

    // TODO: plug memory leaks in general using Profiler... I think there are some lazy things going
    //      on with static variables etc.

    public static final String CHANNEL_ID = "foregroundServiceChannel";
    public static final String RECORDING_ACTIVITY_MESSAGE_ID = "notificationDisplay";

    public static final double UNRELIABLE_ANGLE = 36000; // could be anything, this is just a ridiculous angle
    public static final int NO_ELEVATION_DATA = -9999; // A sufficiently ridiculous elevation value to serve as a no data available placeholder

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

    private static Context appContext; // used by any fragments, views, etc. that need access to the application context for anything

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        appContext = getApplicationContext();
    }

    // Notification channels do not exist below API 26 (Oreo).
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Recording ride channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Notify me that a ride is being recorded");

        // could copy and paste the whole shebang in the lines above to create a second notification channel if desired.

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    public static Context getAppContext() {
        return appContext;
    }
}
