package com.jantztechnologies.ride2;

import static com.jantztechnologies.ride2.App.CHANNEL_ID;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class RecordRideService extends Service implements SensorEventListener {

    private final int notificationId = 1;
    private PendingIntent pendingIntent;
    private NotificationManagerCompat notificationManager;
    private int distanceUnits; // needed to display distance in notification
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private ArrayList<SerializableLocation> acceptedLocations;
    private ArrayList<Integer> manualPauseIndicesAcceptedLocations;
    private ArrayList<SerializableLocation> allLocations;
    private ArrayList<Integer> manualPauseIndicesAllLocations;
    private double rideDistanceMeters;
    private double distanceSinceLastElevationLocation;
    private NetworkElevationDataAccessor elevationGetter;
    private double elevationGainMeters;
    private double elevationLossMeters;
    private SerializableLocation lastElevationDefiningLocation;
    private double blackBoxSpeedMetersPerSecond;
    private double averageSpeedMetersPerSecond;
    private double maxSpeedMps;
    private double maxElevationMeters;
    private double minElevationMeters;
    private static volatile boolean isManuallyPaused;
    private static volatile boolean isAutoPaused;
    private boolean justManuallyResumed;
    private boolean isAutopauseEnabled;
    private long mostRecentLocationTimeNanos;
    private long timestamp;
    private long startTimeNanos;
    private double cumulativeAngleChange;

    private static volatile RecordRideService instance = null;

    private final IBinder binder = new RecordRideBinder();

    public static final MutableLiveData<RideRecordingState> recordingStateLiveData = new MutableLiveData<>();
    public static final MutableLiveData<Boolean> isServiceRunningLiveData = new MutableLiveData<>(false);

    public class RecordRideBinder extends Binder {
        public RecordRideService getService() {
            return RecordRideService.this;
        }
    }

    private long startTime;
    private long lastTimerValue;
    private long rideDurationMillis;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable runnable = new Runnable() {
        final int loopRateMillis = 100;
        int counterMillis = 0;

        @Override
        public void run() {
            autoPauseAutoResume();
            if (!isAutoPaused && !isManuallyPaused) {
                long additionalTime = System.currentTimeMillis() - startTime;
                rideDurationMillis = lastTimerValue + additionalTime;
            }
            findAverageSpeed();

            // Post updated state for reactive UI observation
            Location currentLoc = null;
            if (!allLocations.isEmpty()) {
                SerializableLocation last = allLocations.get(allLocations.size() - 1);
                currentLoc = new Location("fused");
                currentLoc.setLatitude(last.latitude);
                currentLoc.setLongitude(last.longitude);
                currentLoc.setAccuracy((float) last.accuracyMeters);
                currentLoc.setElapsedRealtimeNanos(last.elapsedRealTimeNanos);
            }

            recordingStateLiveData.postValue(new RideRecordingState(
                    rideDurationMillis,
                    rideDistanceMeters,
                    getCurrentSpeed(),
                    averageSpeedMetersPerSecond,
                    isAutoPaused,
                    isManuallyPaused,
                    currentLoc,
                    !acceptedLocations.isEmpty()
            ));

            // Throttle notification updates to once per second (1000ms) to conserve battery and avoid OS throttling
            if (counterMillis % 1000 == 0) {
                String notificationTitle = getString(R.string.app_name);
                if (isManuallyPaused) {
                    notificationTitle = "Stopped Ride";
                } else if (isAutoPaused) {
                    notificationTitle = "Autopaused Ride";
                } else {
                    notificationTitle = "Recording Ride";
                }

                String notificationDistance;
                if (distanceUnits == App.METRIC_UNITS) {
                    notificationDistance = UnitConversion.getDistanceStringKilometers(rideDistanceMeters) + " km";
                } else {
                    notificationDistance = UnitConversion.getDistanceStringMiles(rideDistanceMeters) + " mi";
                }

                String notificationText = UnitConversion.millisToClockString(rideDurationMillis) + "      " + notificationDistance;

                try {
                    Notification notification = new NotificationCompat.Builder(RecordRideService.this, CHANNEL_ID)
                            .setShowWhen(false)
                            .setContentTitle(notificationTitle)
                            .setContentText(notificationText)
                            .setSmallIcon(R.drawable._notification_icon)
                            .setContentIntent(pendingIntent)
                            .setOnlyAlertOnce(true)
                            .build();

                    notificationManager.notify(notificationId, notification);
                } catch (SecurityException ignored) {
                    // Ignored if POST_NOTIFICATIONS was revoked at runtime
                }

                // Query network elevations if needed
                boolean isReturnedNetworkElevationDatumPossiblyAvailable = true;
                while (isReturnedNetworkElevationDatumPossiblyAvailable) {
                    isReturnedNetworkElevationDatumPossiblyAvailable = tryToAssignNetworkElevationDatumToLocation();
                }
                elevationGetter.requestElevations(100);
            }

            counterMillis += loopRateMillis;
            timerHandler.postDelayed(this, loopRateMillis);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setShowWhen(false)
                .setContentTitle("Recording Ride")
                .setSmallIcon(R.drawable._notification_icon)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(notificationId, notification);
        }

        startTime = System.currentTimeMillis();
        startTimeNanos = SystemClock.elapsedRealtimeNanos();
        timerHandler.post(runnable);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isServiceRunningLiveData.postValue(false);
        timerHandler.removeCallbacks(runnable);
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        try {
            notificationManager.cancel(notificationId);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isServiceRunningLiveData.postValue(true);

        notificationManager = NotificationManagerCompat.from(this);

        Intent notificationIntent = new Intent(this, RecordRideActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingFlags);

        PreferencesManager prefs = PreferencesManager.getInstance(this);
        distanceUnits = prefs.getDistanceUnits();
        isAutopauseEnabled = prefs.isAutopauseEnabled();

        lastTimerValue = 0;
        acceptedLocations = new ArrayList<>();
        manualPauseIndicesAcceptedLocations = new ArrayList<>();
        allLocations = new ArrayList<>();
        manualPauseIndicesAllLocations = new ArrayList<>();
        rideDistanceMeters = 0;
        distanceSinceLastElevationLocation = 9999;
        elevationGetter = new NetworkElevationDataAccessor(this);
        elevationGainMeters = 0;
        elevationLossMeters = 0;
        lastElevationDefiningLocation = null;
        blackBoxSpeedMetersPerSecond = 0;
        averageSpeedMetersPerSecond = 0;
        maxSpeedMps = 0;
        maxElevationMeters = 10;
        minElevationMeters = 0;
        mostRecentLocationTimeNanos = 0;
        isManuallyPaused = false;
        isAutoPaused = false;
        justManuallyResumed = false;
        timestamp = System.currentTimeMillis();
        cumulativeAngleChange = 360 * 5;

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(200);
        locationRequest.setFastestInterval(10);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location.getElapsedRealtimeNanos() > startTimeNanos) {
                        blackBoxSpeedMetersPerSecond = location.getSpeed();
                        if (!isManuallyPaused) {
                            mostRecentLocationTimeNanos = location.getElapsedRealtimeNanos();
                            SerializableLocation serializableLocation = new SerializableLocation(location);
                            if (updateAllLocations(serializableLocation)) {
                                updateAcceptedLocations(serializableLocation);
                            }
                        }
                    }
                }
            }
        };
        startLocationRequests();
    }

    public static boolean isServiceCreated() {
        return instance != null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
    }

    public double getTimerValue() {
        return rideDurationMillis;
    }

    public double getRideDistanceMeters() {
        return rideDistanceMeters;
    }

    private void autoPauseAutoResume() {
        try {
            if (acceptedLocations.isEmpty() &&
                    SystemClock.elapsedRealtimeNanos() - startTimeNanos > 10000000000L) {
                isAutoPaused = true;
                lastTimerValue = rideDurationMillis;
            } else if (isAutopauseEnabled &&
                    mostRecentLocationTimeNanos - acceptedLocations.get(acceptedLocations.size() - 1).elapsedRealTimeNanos > 10000000000L) {
                if (!isAutoPaused) {
                    isAutoPaused = true;
                    lastTimerValue = rideDurationMillis;
                }
            } else if (isAutoPaused) {
                isAutoPaused = false;
                startTime = System.currentTimeMillis();
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
    }

    public void manualPause() {
        isManuallyPaused = true;
        lastTimerValue = rideDurationMillis;
        if (!acceptedLocations.isEmpty()) {
            manualPauseIndicesAcceptedLocations.add(acceptedLocations.size() - 1);
            acceptedLocations.get(acceptedLocations.size() - 1).setWasManuallyPausedRightAfterThisLocation(true);
        }
        if (!allLocations.isEmpty()) {
            manualPauseIndicesAllLocations.add(allLocations.size() - 1);
        }

        addCurrentLocationToNetworkElevationQuery(true);
        elevationGetter.requestElevations(1);
    }

    public void manualResume() {
        isManuallyPaused = false;
        startTime = System.currentTimeMillis();
        justManuallyResumed = true;
    }

    public static boolean getIsAutoPaused() {
        return isAutoPaused;
    }

    public static boolean getIsManuallyPaused() {
        return isManuallyPaused;
    }

    public double getAverageSpeedMps() {
        return averageSpeedMetersPerSecond;
    }

    public SerializableLocation getCurrentLocation() {
        try {
            return allLocations.get(allLocations.size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public double getCurrentSpeed() {
        return blackBoxSpeedMetersPerSecond;
    }

    public LiveData<RideRecordingState> getRecordingState() {
        return recordingStateLiveData;
    }

    private void startLocationRequests() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {
        }
    }

    private boolean updateAllLocations(SerializableLocation newLocation) {
        boolean isAdded = false;
        if (allLocations.isEmpty() ||
                newLocation.distanceTo(allLocations.get(allLocations.size() - 1)) > 1) {
            allLocations.add(newLocation);
            updateCumulativeAngleChange();
            isAdded = true;
        }
        return isAdded;
    }

    private void updateAcceptedLocations(SerializableLocation newLocation) {
        int desiredStartingLocationAccuracyMeters = allLocations.size() * 5;
        if (acceptedLocations.isEmpty() && newLocation.accuracyMeters < desiredStartingLocationAccuracyMeters) {
            newLocation.setSmoothedSpeedMps(0);
            acceptedLocations.add(newLocation);
            addCurrentLocationToNetworkElevationQuery(false);

        } else if (!acceptedLocations.isEmpty()) {
            SerializableLocation previousLocation = acceptedLocations.get(acceptedLocations.size() - 1);
            double distanceToNewLocation = newLocation.distanceTo(previousLocation);
            double horizontalSensitivityAdjustment = findHorizontalSensitivityAdjustment();
            double farEnoughAway = (newLocation.accuracyMeters + previousLocation.accuracyMeters) * horizontalSensitivityAdjustment;
            if (distanceToNewLocation > farEnoughAway) {
                acceptedLocations.add(newLocation);
                boolean isManualPauseDetected = updateRideDistanceMeters(newLocation, distanceToNewLocation);
                if (!justManuallyResumed) {
                    addCurrentLocationToNetworkElevationQuery(false);
                } else {
                    newLocation.setWasManuallyResumedRightBeforeThisLocation(true);
                    addCurrentLocationToNetworkElevationQuery(true);
                    justManuallyResumed = false;
                }
                if (!isManualPauseDetected) {
                    newLocation.setRawSpeedMps(distanceToNewLocation / (newLocation.elapsedRealTimeNanos - previousLocation.elapsedRealTimeNanos) * 1000000000);
                }
                newLocation.smoothSpeed(acceptedLocations, manualPauseIndicesAcceptedLocations, 10, 7);
                if (newLocation.getSmoothedSpeedMps() > maxSpeedMps) {
                    maxSpeedMps = newLocation.getSmoothedSpeedMps();
                }
            }
        }
    }

    private void updateCumulativeAngleChange() {
        if (allLocations.size() >= 3) {
            allLocations.get(allLocations.size() - 2).calculateAngle(allLocations.get(allLocations.size() - 3), allLocations.get(allLocations.size() - 1));
        }
        if (allLocations.size() >= 7) {
            cumulativeAngleChange = 0;
            for (int i = allLocations.size() - 2; i > allLocations.size() - 7; i--) {
                double thisAngle = allLocations.get(i).getAngle();
                double lastAngle = allLocations.get(i - 1).getAngle();
                double angleChange;
                if (thisAngle == App.UNRELIABLE_ANGLE || lastAngle == App.UNRELIABLE_ANGLE) {
                    angleChange = 360;
                } else {
                    angleChange = Math.abs(thisAngle - lastAngle);
                }
                cumulativeAngleChange += angleChange;
            }
        }
    }

    private double findHorizontalSensitivityAdjustment() {
        double horizontalSensitivityAdjustment = 2;
        if (cumulativeAngleChange <= 120) {
            horizontalSensitivityAdjustment *= cumulativeAngleChange / 120.0;
        }
        return horizontalSensitivityAdjustment;
    }

    private boolean updateRideDistanceMeters(SerializableLocation newLocation, double distanceToNewLocation) {
        boolean isManualPauseDetected = true;
        if (manualPauseIndicesAcceptedLocations.isEmpty() ||
                manualPauseIndicesAcceptedLocations.get(manualPauseIndicesAcceptedLocations.size() - 1) !=
                        acceptedLocations.size() - 2) {
            rideDistanceMeters += distanceToNewLocation;
            distanceSinceLastElevationLocation += distanceToNewLocation;
            isManualPauseDetected = false;
        }

        newLocation.setDistanceFromStartMeters(rideDistanceMeters);
        return isManualPauseDetected;
    }

    private void addCurrentLocationToNetworkElevationQuery(boolean overrideDistanceRequirement) {
        try {
            if (distanceSinceLastElevationLocation > 20 || overrideDistanceRequirement) {
                distanceSinceLastElevationLocation = 0;

                int index = acceptedLocations.size() - 1;
                double latitude = acceptedLocations.get(index).latitude;
                double longitude = acceptedLocations.get(index).longitude;
                elevationGetter.addCoordinatesToQuery(latitude, longitude, index);
                acceptedLocations.get(index).setIsElevationDefining(true);
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
    }

    private void updateElevationStats(SerializableLocation location) {
        if (lastElevationDefiningLocation == null) {
            maxElevationMeters = location.getNetworkElevationMeters() + 10;
            minElevationMeters = location.getNetworkElevationMeters() - 10;
        } else if (!location.getWasManuallyResumedRightBeforeThisLocation()) {
            double elevationDifference = location.getNetworkElevationMeters() - lastElevationDefiningLocation.getNetworkElevationMeters();
            if (elevationDifference > 0) {
                elevationGainMeters += elevationDifference;
            } else {
                elevationLossMeters -= elevationDifference;
            }
        }

        if (location.getNetworkElevationMeters() > maxElevationMeters) {
            maxElevationMeters = location.getNetworkElevationMeters();
        } else if (location.getNetworkElevationMeters() < minElevationMeters) {
            minElevationMeters = location.getNetworkElevationMeters();
        }

        lastElevationDefiningLocation = location;
    }

    private void findAverageSpeed() {
        if (rideDurationMillis > 0) {
            averageSpeedMetersPerSecond = rideDistanceMeters / rideDurationMillis * 1000;
        }
    }

    /**
     * Saves ride entity and track points directly into SQLite via Room database.
     * @return Generated primary key ID of the saved ride.
     */
    public long saveRide() {
        RideEntity rideEntity = new RideEntity(
                timestamp,
                rideDistanceMeters,
                rideDurationMillis,
                averageSpeedMetersPerSecond,
                maxSpeedMps,
                maxElevationMeters,
                minElevationMeters,
                elevationGainMeters,
                elevationLossMeters
        );

        AppDatabase db = AppDatabase.getInstance(this);
        long rideId = db.rideDao().insertRide(rideEntity);

        List<TrackPointEntity> trackPointEntities = new ArrayList<>();
        for (SerializableLocation loc : acceptedLocations) {
            trackPointEntities.add(loc.toTrackPoint(rideId, true));
        }
        db.trackPointDao().insertTrackPoints(trackPointEntities);

        return rideId;
    }

    private boolean tryToAssignNetworkElevationDatumToLocation() {
        try {
            ElevationDatumPackage elevationDatum = elevationGetter.getNetworkElevations().get(0);
            if (elevationDatum.getElevationMeters() != App.NO_ELEVATION_DATA) {
                SerializableLocation location = acceptedLocations.get(elevationDatum.getLocationsListIndex());
                location.setNetworkElevationMeters(elevationDatum.getElevationMeters());

                elevationGetter.deleteElevationDatum(0);
                updateElevationStats(location);
                return true;
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return false;
    }
}