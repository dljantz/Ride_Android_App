package com.jantztechnologies.ride2;

import android.location.Location;

public class RideRecordingState {
    public final long durationMillis;
    public final double distanceMeters;
    public final double currentSpeedMps;
    public final double averageSpeedMps;
    public final boolean isAutoPaused;
    public final boolean isManuallyPaused;
    public final Location currentLocation;
    public final boolean isGpsAcquired;

    public RideRecordingState(long durationMillis,
                              double distanceMeters,
                              double currentSpeedMps,
                              double averageSpeedMps,
                              boolean isAutoPaused,
                              boolean isManuallyPaused,
                              Location currentLocation,
                              boolean isGpsAcquired) {
        this.durationMillis = durationMillis;
        this.distanceMeters = distanceMeters;
        this.currentSpeedMps = currentSpeedMps;
        this.averageSpeedMps = averageSpeedMps;
        this.isAutoPaused = isAutoPaused;
        this.isManuallyPaused = isManuallyPaused;
        this.currentLocation = currentLocation;
        this.isGpsAcquired = isGpsAcquired;
    }
}
