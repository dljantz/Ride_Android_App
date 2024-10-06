package com.jantztechnologies.ride2;

import static com.jantztechnologies.ride2.App.CHANNEL_ID;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

public class RecordRideService extends Service implements SensorEventListener {

    private final int notificationId = 1;
    private PendingIntent pendingIntent;
    private NotificationManagerCompat notificationManager;
    private int distanceUnits; // needed to display the distance in notification correctly
    private FusedLocationProviderClient fusedLocationClient; // high level location API
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private ArrayList<SerializableLocation> acceptedLocations;
    private ArrayList<Integer> manualPauseIndicesAcceptedLocations; // lists indices of every location accepted right before the user hit pause
    private ArrayList<SerializableLocation> allLocations;
    private ArrayList<Integer> manualPauseIndicesAllLocations; // indices of every very last location, accepted or not, before user hit pause
    private double rideDistanceMeters;
    private double distanceSinceLastElevationLocation; // every time this gets larger than 30 meters, the next location is triggered to be added to the next elevation api query.
    private NetworkElevationDataAccessor elevationGetter; // queries opentopodata.org for elevation data since satellites suck at that
    private double elevationGainMeters;
    private double elevationLossMeters; // this is a POSITIVE value.
    private SerializableLocation lastElevationDefiningLocation; // used to calculate elevation gain / loss / max / min stats
    private double blackBoxSpeedMetersPerSecond;
    private double averageSpeedMetersPerSecond;
    private double maxSpeedMps;
    private double maxElevationMeters;
    private double minElevationMeters;
    private static boolean isManuallyPaused; // need to query this in a situation where we asked to re-bind milliseconds ago, so it's not done binding yet. So making it static is a workaround
    private static boolean isAutoPaused; // same deal here as it turns out
    private boolean justManuallyResumed; // used to prevent elevation stats from calculating across pause events
    private boolean isAutopauseEnabled; // read settings file to determine this
    private long mostRecentLocationTimeNanos;
    private long timestamp;
    private long startTimeNanos; // used to filter out location objects from before the start of the service... the location callback often receives these "premature locations"
    private double cumulativeAngleChange; // lower = more reliable, smooth curves. Higher = squiggly and unreliable

    //instance variable used to find out if the service is running from a different process
    //    this allows the activity to decide whether to try to bind to it when the activity starts.
    private static RecordRideService instance = null;

    // Android developers docs told me to do this:
    // Binder given to clients
    private final IBinder binder = new RecordRideBinder();

    // uhh... weird stuff below. I guess I'm allowing the binder to return
    //    the current instance of this Service to the client that's asking.
    //    This allows the client to call public methods in this Service.
    public class RecordRideBinder extends Binder {
        public RecordRideService getService() {
            return RecordRideService.this;
        }
    }

    // Define timer stuff here
    private long startTime; // gets updated with System.currentTimeMillis() every time we restart the timer.
    private long lastTimerValue; // the ending timer value when pause was last pressed, in milliseconds
    private long rideDurationMillis; // Units: milliseconds. gets stored as lastTimerValue when you hit pause

    // Handler allows me to queue future actions, very valuable
    Handler timerHandler = new Handler();
    // Loops to take care of timer tasks for now, gps and other stuff later I think
    Runnable runnable = new Runnable() {

        final int loopRateMillis = 100; // needs to be a factor of 1000 so elevation checking while loop can run any whole number of seconds I like
        int counterMillis = 0;

        @Override // Runnable is an interface but I can still override the empty method I guess
        public void run() {
            autoPauseAutoResume();
            // service has not been paused or autopaused, go ahead with timer
            if (!isAutoPaused && !isManuallyPaused) {
                long additionalTime = System.currentTimeMillis() - startTime;
                rideDurationMillis = lastTimerValue + additionalTime;
            }
            findAverageSpeed(); // needs to happen at least semi-frequently, independent of location updates...

            // notification title changes depending on state
            String notificationTitle = "Recording Ride";
            if (isManuallyPaused) notificationTitle = "Stopped Ride";
            else if (isAutoPaused) notificationTitle = "Autopaused Ride";

            // update notification content -- time, distance... avg speed???
            String notificationDistance;
            if (distanceUnits == App.METRIC_UNITS) notificationDistance = UnitConversion.getDistanceStringKilometers(rideDistanceMeters) + " km";
            else  notificationDistance = UnitConversion.getDistanceStringMiles(rideDistanceMeters) + " mi";

            String notificationText = UnitConversion.millisToClockString(rideDurationMillis) + "      " + notificationDistance;

            notificationManager.notify(notificationId, new NotificationCompat.Builder(instance, CHANNEL_ID)
                    .setShowWhen(false)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setSmallIcon(R.drawable._notification_icon)
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true)
                    .build());

            // Once every second, check for elevation data returned from the interwebz to get and store
            if (counterMillis % 1000 == 0) {
                // ...Ok, I admit, this variable name is a little long. Sorry. Basically, just transferring data from,
                //      then deleting, individual elevationDatumPackages. Cuts off when we hit one that
                //      doesn't have its elevation value yet, allowing backlog to stack up as much as it
                //      needs to.
                boolean isReturnedNetworkElevationDatumPossiblyAvailable = true;
                while (isReturnedNetworkElevationDatumPossiblyAvailable) {
                    isReturnedNetworkElevationDatumPossiblyAvailable = tryToAssignNetworkElevationDatumToLocation();
                }
                // calling requestElevations all the time like this ensures backlogs built up during periods
                //      of no internet access are always cleared.
                elevationGetter.requestElevations(100); // If I stay at one elevation-defining
                //      location per 10 meters, a minQueryLength of 100 results in one network elevation
                //      request every kilometer --> max of 1000 kilometers per day.
            }
            counterMillis += loopRateMillis; // increment up so while loop triggers again every few seconds

            // VERY important below!! decides how often the Runnable is rerun on the main thread.
            timerHandler.postDelayed(this, loopRateMillis);
        }
    };

    // The system calls this at some point to get the binder object I defined above
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // gets triggered when we start the service, params are data needed to run the service
    //    does NOT get triggered on binding, just a start command from the activity.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // channel ID required in Android Oreo and higher for better user notification control
        // Channel ID is ignored by older versions
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setShowWhen(false)
                .setContentTitle("Recording Ride")
                .setSmallIcon(R.drawable._notification_icon)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();

        // promotes the service to the foreground so user is aware of it. must occur within 5 seconds of receiving the intent.
        startForeground(notificationId, notification);

        // timer stuff
        startTime = System.currentTimeMillis();
        startTimeNanos = SystemClock.elapsedRealtimeNanos(); // not used in timer, just to prevent accepting locations from before the service started running.
        timerHandler.post(runnable); // run the timer

        // define what happens when the system kills the service
        // foreground services very unlikely to be killed, so I probs don't need
        //    to worry about restarting the service / redelivering the intent.
        return START_STICKY;
    }

    // clean up stuff
    @Override
    public void onDestroy() {
        super.onDestroy();
        // this instance of the service. I may not need to reset it but perhaps best to clean up after myself
        instance = null;
        timerHandler.removeCallbacks(runnable);
        fusedLocationClient.removeLocationUpdates(locationCallback); // stop location updates
        notificationManager.cancel(notificationId);
        ///////////////////System.out.println("RecordRideService destroyed");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // give isServiceCreated() an instance to ping

        notificationManager = NotificationManagerCompat.from(this); // used to update the notification with ongoing ride2 data
        // if you click the notification, RecordRideActivity will be opened.
        Intent notificationIntent = new Intent(this, RecordRideActivity.class);
        // 3/27/2022: Version 1.7 crashed on mom and dad's phones (Android 12). I had updated the gradle file to target SDK version 31, which
        //      triggered an IllegalArgumentException on newer phones that have stricter requirements for pendingIntent creation.
        //      It must be flagged as immutable or mutable to keep the system happy. I have no idea why, the OS developers presumably
        //      had a good reason...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        distanceUnits = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename));

        lastTimerValue = 0; // make sure the timer starts at zero each time it restarts.
        acceptedLocations = new ArrayList<>();
        manualPauseIndicesAcceptedLocations = new ArrayList<>();
        allLocations = new ArrayList<>();
        manualPauseIndicesAllLocations = new ArrayList<>();
        rideDistanceMeters = 0;
        distanceSinceLastElevationLocation = 9999; // start off at a nice high number so the first call to requestElevationData triggers an elevation request for the first accepted location.
        elevationGetter = new NetworkElevationDataAccessor(this);
        elevationGainMeters = 0;
        elevationLossMeters = 0;
        lastElevationDefiningLocation = null;
        blackBoxSpeedMetersPerSecond = 0;
        averageSpeedMetersPerSecond = 0;
        maxSpeedMps = 0;
        maxElevationMeters = 10; // not allowed to set primitives to null so here we are... not-so-crazy defaults that it won't look terrible if they accidentally get graphed.
        minElevationMeters = 0;
        mostRecentLocationTimeNanos = 0; // used in autopausing b/c of one meter radius filter turning allLocations into "mostLocations".
        isManuallyPaused = false;
        isAutoPaused = false;
        justManuallyResumed = false;
        if (FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.autopause_setting_filename)) == App.AUTOPAUSE_DISABLED) {
            // user set autopause to disabled
            isAutopauseEnabled = false;
        } else {
            // user enabled autopause or there was an error reading file, returning -1. I'm hoping enabling it is the right default move to make the most users happy.
            isAutopauseEnabled = true;
        }
        timestamp = System.currentTimeMillis();
        cumulativeAngleChange = 360 * 5; // starting value at max unreliability for now

        // got this stuff from android developer docs. Their example had everything in an activity, but I had
        //     to split it up bc one of the methods to check for correct location settings needs to operate
        //     from within an activity. So, there is a locationRequest variable created over there as well.
        //     But that variable is a different object and isn't used for anything other than setting up the
        //     task / onFailureListener, etc.
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(200); // preferred location update frequency. this value is used to assign power blame by the system.
        locationRequest.setFastestInterval(10); // fastest location update frequency my app can handle
        // note -- the system on my phone seems to max out at slightly faster than 1 second updates
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // more likely to use GPS than wifi or cell tower data
        // fusedLocationClient is an object allowing simple method calls for last location, current location, etc.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) { // NonNull annotation ensures method does not run if locationResult is null
                // so... I'm perplexed why I have to do this, but for some reason locationResult
                //    returns a list of length 1 instead of a simple location object. So I iterate
                //    through a for loop once to get it lol
                for (Location location : locationResult.getLocations()) {
                    // I ran a test, these two values below are indeed reliably comparable. This is a filter
                    //      to keep from doing anything with locations that are older than the service
                    //      itself, which was annoying for autopause and occasional weird start speeds,
                    //      particularly if you start it in a fast moving vehicle
                    if (location.getElapsedRealtimeNanos() > startTimeNanos) {
                        blackBoxSpeedMetersPerSecond = location.getSpeed();
                        if (!isManuallyPaused) {
                            // updating this value here allows autopause to still work even with the 1 m filter
                            //      on allLocations
                            mostRecentLocationTimeNanos = location.getElapsedRealtimeNanos();
                            // my attempt at getting the data to behave with file writing / reading
                            SerializableLocation serializableLocation = new SerializableLocation(location);
                            // new locations should probs only be accepted if they made it into allLocations...
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

    // used by other processes to query if this service is running
    //     MUST be static otherwise cannot be called when service isn't running
    public static boolean isServiceCreated() {
        try {
            // if instance is created, this will return true:
            return instance.ping();
        } catch (NullPointerException e) {
            // if instance does not exist, exception was thrown and we should return false
            return false;
        }
    }

    // non-static function used to determine if the service is running
    //     (causes NullPointerException if instance of service not created)
    private boolean ping() {
        return true;
    }

    // if sensor accuracy changes, I can try to handle that somehow
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { // TO DO: SENSOR WORK
        // do something i guess
    }

    // I have mixed feelings about this... maybe I should just get every data point
    //     regardless of changes? This may be more computationally efficient though
    @Override
    public void onSensorChanged(SensorEvent event) {// TO DO: SENSOR WORK
        // change some variables in here based on event.values[]
    }

    // called by RecordRideActivity to return timer data to display on the screen.
    public double getTimerValue() {
        return rideDurationMillis;
    }

    // called by activity to get current distance to display
    public double getRideDistanceMeters() {
        return rideDistanceMeters;
    }

    //  Called once per timer runnable.
    private void autoPauseAutoResume() {
        // only flip autopaused to true if it needs to be flipped
        try {
            // must have rejected location updates for 10 seconds or more to pause. This must be measured
            //      two ways for the two cases -- if we haven't accepted any locations yet, and after we
            //      have accepted at least one location.
            if (acceptedLocations.isEmpty() &&
                    SystemClock.elapsedRealtimeNanos() - startTimeNanos > 10000000000L) {
                isAutoPaused = true;
                lastTimerValue = rideDurationMillis;
            } else if (isAutopauseEnabled &&
                    mostRecentLocationTimeNanos - acceptedLocations.get(acceptedLocations.size() - 1).elapsedRealTimeNanos > 10000000000L) {
                if (!isAutoPaused) { // todo: I think this little nested if statement is unnecessary, try removing
                    isAutoPaused = true;
                    lastTimerValue = rideDurationMillis; // same deal as manualPause()
                }
            } else if (isAutoPaused) { // should only run code below once to flip it back to false
                isAutoPaused = false;
                startTime = System.currentTimeMillis();
            }
        } catch (IndexOutOfBoundsException ignored) {
            // do nothing, this is expected at the beginning due to empty arrayList (even though
            //      we check for empty arraylist above, it's possible to still advance through
            //      the other else ifs)
        }
    }

    // pause the timer without returning it to zero, stop getting location updates
    public void manualPause() {
        isManuallyPaused = true;
        lastTimerValue = rideDurationMillis; // new starting point to add time values onto.
        // this creates the possibility of isolated points due to fast resume / pause hits. But
        //      all indices have to be recorded anyway to prevent distance recording during paused movement.
        if (acceptedLocations.size() > 0) {
            manualPauseIndicesAcceptedLocations.add(acceptedLocations.size() - 1);
            acceptedLocations.get(acceptedLocations.size() - 1).setWasManuallyPausedRightAfterThisLocation(true);
        }
        if (allLocations.size() > 0) manualPauseIndicesAllLocations.add(allLocations.size() - 1);

        addCurrentLocationToNetworkElevationQuery(true); // add the very last location to the list of elevations to download.
        // I don't know that the line below will work that well... the goal is to get any elevations that haven't
        //      been gotten yet before we kill the service, in the hopes that ViewRideActivity doesn't
        //      have to do any work. But firing up the radio means it can take 2-5 seconds to get a response,
        //      so it's probably not fast enough. I guess this could pre-fire up the radio state machine
        //      so it's ready to go for ViewRideActivity... so that's something.
        elevationGetter.requestElevations(1);
    }

    // resume the timer, counting up from value it had when it was paused, resume location updates
    public void manualResume() {
        isManuallyPaused = false;
        startTime = System.currentTimeMillis();
        // make sure the current location has its manual pause record set correctly. It's not actually recorded yet,
        //      so we'll leave a marker to trigger the appropriate action when it is collected.
        justManuallyResumed = true;
    }

    public static boolean getIsAutoPaused() {
        return isAutoPaused;
    }

    /* static getter is useful in other processes if they know the service is running but
         don't know if the timer is paused or not. As a static function / variable, this
         information can be accessed without having to bind or before the process has had
         a chance to finish binding, which I have had issues with before.
     */
    public static boolean getIsManuallyPaused() {
        return isManuallyPaused; // instance is static so basically pretending that isManuallyPaused is static
    }

    public double getAverageSpeedMps() {
        return averageSpeedMetersPerSecond;
    }

    // returns location object to other process
    public SerializableLocation getCurrentLocation() {
        try {
            return allLocations.get(allLocations.size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return null; // arrayList is empty until first location found, this prevents crashing
        }
    }

    public double getCurrentSpeed() {
        return blackBoxSpeedMetersPerSecond;
        /////////////////////return acceptedLocations.get(acceptedLocations.size() - 1).getSmoothedSpeedMps();
        //////////////////////return allLocations.get(allLocations.size() - 1).getSmoothedSpeedMps();
    }

    // get the ball rolling on fast location updates!!!
    private void startLocationRequests() {
        try { // Android studio is happier when I explicitly handle security stuff in the same place I start location update requests, even though I am handling it elsewhere
            // idk how Looper works, but I guess this just means it's running on the main application thread
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            // do nothing, hopefully we never throw a Security Exception anyway
        }
    }

    // allLocations is really mostLocations now... see below
    private boolean updateAllLocations(SerializableLocation newLocation) {
        boolean isAdded = false;
        // below -- turning allLocations into "mostLocations"... new points have to be >1m from
        //      each other to be worth bothering with. But mainly, for really close points I've
        //      been getting super low angle change values, screwing with cumulative angle change
        //      measure. Hopefully excluding the ones that are super close helps with that.
        if (allLocations.size() == 0 || // or operator short-circuits if true, preventing index out of bounds exception
                newLocation.distanceTo(allLocations.get(allLocations.size() - 1)) > 1) {
            allLocations.add(newLocation);
            // smoothing below is unnecessary if I would rather use black box speed in display. can eliminate.
            // Reminder -- smoothing in allLocations also affects the locations in acceptedLocations
            //      as the lists are referring to the same objects. This is solved by re-smoothing every
            //      location as it is accepted into acceptedLocations.
            ////////////////////////newLocation.smoothSpeed(allLocations, manualPauseIndicesAllLocations, 10, 5); // playing around with using smoothed speed from allLocations as the current speed display
            updateCumulativeAngleChange();
            isAdded = true;
        }
        return isAdded;
    }

    // decides whether to confirm new location value based on accuracy estimate
    //   and distance from last location
    private void updateAcceptedLocations(SerializableLocation newLocation) {
        // very first data point below. I had some issues with the first one being super inaccurate sometimes,
        //      so the second part of the if statement is a filter of sorts. It sets high accuracy standards
        //      for the first data point, but gradually relaxes them to ensure we eventually start recording
        //      data no matter what. "desiredStartingLocationAccuracyMeters" starts at a filter of 5 m accuracy
        //      and increments up by 5 meters every time a new point is added to allLocations.
        int desiredStartingLocationAccuracyMeters = allLocations.size() * 5;
        if (acceptedLocations.size() == 0 && newLocation.accuracyMeters < desiredStartingLocationAccuracyMeters) {
            System.out.println("starting location accuracy: " + desiredStartingLocationAccuracyMeters);
            newLocation.setSmoothedSpeedMps(0); // re-smooth to address smoothing that may have occurred in updateAllLocations.
            // append location to arraylist
            acceptedLocations.add(newLocation);
            // below: must occur after newLocation is appended to acceptedLocations
            addCurrentLocationToNetworkElevationQuery(false); // on the first run it should trigger the first location to be included in the elevation data query automatically due to a high initialized value for distanceSinceLastElevationLocation

        } else if (!acceptedLocations.isEmpty()){
            SerializableLocation previousLocation = acceptedLocations.get(acceptedLocations.size() - 1);
            // there are two locations to compare, so do so here. This assumes they both
            //      have accuracy values. Defaults to 0.0 if not, so logic won't break.
            double distanceToNewLocation = newLocation.distanceTo(previousLocation);
            double horizontalSensitivityAdjustment = findHorizontalSensitivityAdjustment(); // using cumulative angle change
            double farEnoughAway = (newLocation.accuracyMeters + previousLocation.accuracyMeters) * horizontalSensitivityAdjustment;
            /////////////////////////////Toast.makeText(this, "Far enough away (m): " + (int) farEnoughAway, Toast.LENGTH_SHORT).show();
            // confirm this location and add it to the list. Then update ride2 distance, speed at this spot, elevation
            if (distanceToNewLocation > farEnoughAway) {
                // append location to arraylist
                acceptedLocations.add(newLocation);
                // it's critical that the method below is called AFTER appending above, as logic depends on arraylist sizes / indices being
                //      set up right. Otherwise we get weird stuff like measuring the distance traveled while the ride2 was manually paused,
                //      which is obviously no bueno.
                boolean isManualPauseDetected = updateRideDistanceMeters(newLocation, distanceToNewLocation);
                if (!justManuallyResumed) {
                    addCurrentLocationToNetworkElevationQuery(false); // must occur after rideDistanceMeters is updated and after newLocation is appended
                } else {
                    newLocation.setWasManuallyResumedRightBeforeThisLocation(true);
                    addCurrentLocationToNetworkElevationQuery(true); // since we could be anywhere, since we just resumed, just mark this location as elevation defining.
                    justManuallyResumed = false; // reset flag variable
                }
                // change speed attribute to be based on speed from last accepted point to this one, unless there was a manual pause
                //      between that point and this one. In that case we just let it default to zero.
                // still recording raw speed for use in color gradients in route mapping
                if (!isManualPauseDetected) newLocation.setRawSpeedMps(distanceToNewLocation / (newLocation.elapsedRealTimeNanos - previousLocation.elapsedRealTimeNanos) * 1000000000);
                // smooth speed prior to updating max speed. note: this hinges on occurring after newLocation has
                //      been appended onto acceptedLocations.
                newLocation.smoothSpeed(acceptedLocations, manualPauseIndicesAcceptedLocations, 10, 7); // used to be 20 and 10, lowered on 2/18 to get faster responses... may be penalized on walks though, idk
                // update max speed if needed
                if (newLocation.getSmoothedSpeedMps() > maxSpeedMps) maxSpeedMps = newLocation.getSmoothedSpeedMps();
            }
        }
    }

    // all the angle stuff... calculate angles, find the difference between them, add all the differences
    //      up to get a cumulative measure of path smoothness and therefore trustworthiness. The works!
    private void updateCumulativeAngleChange() {
        if (allLocations.size() >= 3) { // need at least three points to get an angle
            // below -- very lengthy way of saying, "find and store how much the path changed from continuing in a straight line".
            allLocations.get(allLocations.size() - 2).calculateAngle(allLocations.get(allLocations.size() - 3), allLocations.get(allLocations.size() - 1));
            /////////System.out.println("path change angle: " + allLocations.get(allLocations.size() - 2).getAngle());
        }
        // check the last 5 path change angles. arraylist must have 7 items because:
        //      last one hasn't calculated angle stuff yet
        //      they all have to compare themselves to the previous one, so item zero can
        //          only be used for comparison, not its own point
        if (allLocations.size() >= 7) {
            cumulativeAngleChange = 0; // reset. inefficient this way but I don't think it's a big deal, it only happens once per second or so
            // go through the last 5 items in the last that have calculated path change angles
            for (int i = allLocations.size() - 2; i > allLocations.size() - 7; i--) {
                double thisAngle = allLocations.get(i).getAngle();
                double lastAngle = allLocations.get(i-1).getAngle();
                double angleChange;
                // angle change is the measure of how much the two angles differ from each other.
                //      so, extremely tight turns with consistent angles can still trigger hyper
                //      responsive location acceptance while being confident in their accuracy.
                // below: check for location equality. If EITHER of them was determined to be  the exact same
                //      as a prior or consecutive point, we just set angle change to 360 (equal to the maximum
                //      level of unreliability possible if we were to actually carry out the angle change calculation)
                if (thisAngle == App.UNRELIABLE_ANGLE || lastAngle == App.UNRELIABLE_ANGLE) {
                    angleChange = 360; // set to max possible angle change to signal unreliability
                // below: "Great, angles were both reasonable, let's see how different these two angles are."
                } else {
                    angleChange = Math.abs(thisAngle - lastAngle);
                }
                cumulativeAngleChange += angleChange;
            }
            ///////////System.out.println("last 5 path changes totaled: " + cumulativeAngleChange);
        }
    }

    // tunes the radius to be cleared to accept new points based on cumulative angle change -- a low
    //      value indicates a nice smooth path, and we should bias towards sensitivity. High value =
    //      squiggly, bias towards certainty (higher radius to be cleared)
    private double findHorizontalSensitivityAdjustment() {
        double horizontalSensitivityAdjustment = 2;
        if (cumulativeAngleChange <= 120) { // was at 30 for a while, not sensitive enough
            horizontalSensitivityAdjustment *= cumulativeAngleChange / 120.0;
        }
        return horizontalSensitivityAdjustment;
    }

    private boolean updateRideDistanceMeters(SerializableLocation newLocation, double distanceToNewLocation) {

        boolean isManualPauseDetected = true;
        // if the ride2 has never been paused, go ahead and measure distance
        if (manualPauseIndicesAcceptedLocations.isEmpty() || // short circuiting or is essential or we get index out of bounds exception
                // below:
                //      "if the last number in the arraylist of indices is NOT equal to the size of the
                //      acceptedLocations arraylist - 2, this is not a point right after pausing. DO measure
                //      distance because it will measure a line segment that was traveled while user wanted it to be
                //      recording."
                manualPauseIndicesAcceptedLocations.get(manualPauseIndicesAcceptedLocations.size() - 1) !=
                        acceptedLocations.size() - 2) {
            rideDistanceMeters += distanceToNewLocation;
            distanceSinceLastElevationLocation += distanceToNewLocation; // also update this so later we can tell if this point is spaced out enough to warrant a network request for its elevation attribute.
            isManualPauseDetected = false;
        } // invisible else: distance update request was rejected because it was the point right after pausing,
          //        so do not update distance.

        // Lastly: store current rideDistanceMeters as an attribute of current accepted location for graphing purposes
        newLocation.setDistanceFromStartMeters(rideDistanceMeters);

        return isManualPauseDetected;
    }

    // Every 30 meters, we mark a location object as assigned to be part of the next
    //      elevation query from the opentopodata API. For efficiency, elevation data is downloaded
    //      in maximum-size chunks (100 locations per query). Additionally, there is a max of one
    //      query per second and 1000 queries per day. That means the user can only download elevation
    //      data for a max of 1000 queries/day * 100 locations/query * 10 meters/location = 1000km per day.
    //      This should be sufficient for 99.99% of people, and the other 0.01% will be literally dead
    //      after that ride2 so they will never know...
    private void addCurrentLocationToNetworkElevationQuery(boolean overrideDistanceRequirement) {
        try {
            // overrideDistanceRequirement is used to ensure that the very last location is included
            //      in the network query so the graph doesn't have a sharp drop off at the end.
            if (distanceSinceLastElevationLocation > 20 || overrideDistanceRequirement) {
                distanceSinceLastElevationLocation = 0; // reset the counter variable

                int index = acceptedLocations.size() - 1;
                double latitude = acceptedLocations.get(index).latitude;
                double longitude = acceptedLocations.get(index).longitude;
                elevationGetter.addCoordinatesToQuery(latitude, longitude, index);

                // useful in case elevation is not successfully assigned... ViewRideActivity will
                //      use this as a marker to select this location for the query do-over so it
                //      doesn't have to recalculate locations spaced out by 20 meters
                acceptedLocations.get(index).setIsElevationDefining(true);
            }
        } catch (IndexOutOfBoundsException ignored) { } // this should happen sometimes -- if user hits stop right away, acceptedLocations is empty
    }

    // occurs in spurts -- every time a chunk of elevation data comes in from the network
    // does elevation gain, loss, max, and min
    private void updateElevationStats(SerializableLocation location) {

        // case 1: first time this method is called, just set up some baseline values
        if (lastElevationDefiningLocation == null) {
            maxElevationMeters = location.getNetworkElevationMeters() + 10;
            minElevationMeters = location.getNetworkElevationMeters() - 10;
        }

        // case 2: not the first time it's called, gotta do some work to update elevation stats. But only if
        //      this isn't the location after a manual resume event, cuz that means the elevation change from
        //      last location to this one was while the ride2 was paused.
        else if (!location.getWasManuallyResumedRightBeforeThisLocation()) {
            // gain / loss stuff
            double elevationDifference = location.getNetworkElevationMeters() - lastElevationDefiningLocation.getNetworkElevationMeters();
            if (elevationDifference > 0) elevationGainMeters += elevationDifference;
            else elevationLossMeters -= elevationDifference; // subtracting a negative keeps this variable positive
        }

        // max / min stuff should happen regardless of manual pause / resume status. The two lines below
        //      are superfluous on the very first elevation defining location, since max and min stuff is
        //      set already  (see above) on that run through. But it's harmless to run and wastes very little
        //      computing power.
        if (location.getNetworkElevationMeters() > maxElevationMeters) maxElevationMeters = location.getNetworkElevationMeters();
        else if (location.getNetworkElevationMeters() < minElevationMeters) minElevationMeters = location.getNetworkElevationMeters();

        lastElevationDefiningLocation = location; // update so we're ready for the next run through
    }

    // thought about using getRealTimeNanos(), but I think this is super accurate anyway and simpler
    private void findAverageSpeed() {
        if (rideDurationMillis > 0) averageSpeedMetersPerSecond = rideDistanceMeters / rideDurationMillis * 1000;
        ///////////System.out.println("service distance (m) " + rideDistanceMeters);
        /////////////System.out.println("service time (s) " + rideDurationMillis / 1000.0);
    }

    // saves ride2 to file, generating a new folder per ride2
    public String saveRide() {

        RideStats rideStats = new RideStats(timestamp,
                                            rideDistanceMeters,
                                            averageSpeedMetersPerSecond,
                                            maxSpeedMps,
                                            maxElevationMeters,
                                            minElevationMeters,
                                            rideDurationMillis,
                                            elevationGainMeters,
                                            elevationLossMeters);

        String rideFolder = FileIO.createNewRideFolder(this);

        FileIO.saveRideStats(this, rideFolder, rideStats, getResources().getString(R.string.ride_stats_filename));
        FileIO.saveArrayListIntegers(this, rideFolder, manualPauseIndicesAllLocations, getResources().getString(R.string.manual_pause_indices_all_locations_filename));
        FileIO.saveArrayListLocations(this, rideFolder, allLocations, getResources().getString(R.string.all_locations_filename));
        FileIO.saveArrayListIntegers(this, rideFolder, manualPauseIndicesAcceptedLocations, getResources().getString(R.string.manual_pause_indices_accepted_locations_filename));
        FileIO.saveArrayListLocations(this, rideFolder, acceptedLocations, getResources().getString(R.string.accepted_locations_filename));

        return rideFolder;
    }

    // to be called by while loop -- keeps going until this method returns false.
    private boolean tryToAssignNetworkElevationDatumToLocation() {
        // Each time it pulls a datum over, it removes it from the list stored by the elevationGetter,
        //      "emptying the trash" as soon as it's no longer useful. Thus, at any given moment,
        //      only UNACCESSED data is stored by the elevationGetter.
        try {
            ElevationDatumPackage elevationDatum = elevationGetter.getNetworkElevations().get(0);
            if (elevationDatum.getElevationMeters() != App.NO_ELEVATION_DATA) {
                SerializableLocation location = acceptedLocations.get(elevationDatum.getLocationsListIndex()); // syntactic sugar
                location.setNetworkElevationMeters(elevationDatum.getElevationMeters());

                ////////////if (elevationDatum.getLatitude() == location.latitude &&
                    ////////////////elevationDatum.getLongitude() == location.longitude) {
                    /////////////////System.out.println("Elevation datum stored as attribute of correct location");
                /////////////////////////} else {
                    /////////////////System.out.println("ALERT! Elevation datum stored in wrong location object");
                ////////////////}

                elevationGetter.deleteElevationDatum(0);
                /////////////////////////////toastyMcToastFace("Elevation of " + elevationDatum.getElevationMeters() + " meters received by RecordRideService"); // todo: delete!!
                // below: elevation gain / loss & max / min stat tracking.
                updateElevationStats(location);
                return true; // signal that there probs more data out there to get
            }
        } catch (IndexOutOfBoundsException ignored) {
            // should happen often, as elevation data are constantly getting emptied out of elevationGetter...
            //      so the the list we're pulling from is often empty
        }
        return false; // if we ever make it this far, we're done pulling data for now; returning false cuts off the while loop
    }
}
/*
Some notes about sensors...
--> before attempting to perform a sensor-dependent process, check that the sensor exists
--> Can also collect information about each sensor, such as manufacturer / resolution / maximum range
--> Apparently I can define the rate at which I acquire sensor data, very useful I imagine
--> sensors are hardware or software based
--> There are several accelerometer based "sensors" -- accelerometer, gravity, linear acceleration
--> magnetic field sensor acquires geomagnetic field data for ALL THREE AXES, potentially useful
    +++ a software sensor called "TYPE_ORIENTATION" does the heavy lifting for me --
        it combines geomagnetic field data with gravity "sensor" data to output
        inclination matrix and rotation matrix data. Very intriguing...
--> Sensor Framework has four classes: SensorManager, Sensor, SensorEvent, and SensorEventListener. idk when to use each one
    +++ SensorEventListener can apparently notify me when sensor values change / when sensor accuracy changes, could be useful
--> All sensors are available after API level 14. However, TYPE_ORIENTATION has been deprecated.
    So I may just get to calculate device inclination / rotation myself :) More fun that way anyway.
    +++ Edit: wait, is it deprecated? need to double check
    +++ Edit2: apparently TYPE_ORIENTATION is deprecated, but I can call getOrientation() to get the same info
    Pretty sure I need accelerometer / gravity sensor, geomagnetic sensor... also use gyroscope?
--> I have implemented SensorEventListener, but see a problem -- how do I listen for sensor events from
    multiple different sensors in the same process? At the moment, onSensorChanged() is
    devoted to only one sensor and android docs don't say how to listen to more.
--> As I have started to do in MainActivity, at runtime I can check for the
    existence of certain sensors. OR, I can just apply Google Play filters to
    only target devices with certain sensor configurations.
--> Android emulator has some virtual sensor controls to test virtual sensors /
    how my app responds to different input!! It actually takes sensor input
    from my actual phone and uses them in the emulator.
-->



 */