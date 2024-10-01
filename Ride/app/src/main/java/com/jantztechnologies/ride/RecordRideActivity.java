package com.jantztechnologies.ride;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Objects;

public class RecordRideActivity extends AppCompatActivity {
    private static final int LOCATION_FOUND_RADIUS_METERS = 25;

    // set up the activity to bind to the service
    RecordRideService service;
    boolean isServiceBound = false;
    // based on developer docs. I think it just toggles the "bound" boolean, not too fancy
    // these methods don't seem to get called automatically on service disconnect / connect.
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            // We've bound to the service, cast the IBinder and get service instance
            RecordRideService.RecordRideBinder binder = (RecordRideService.RecordRideBinder) iBinder;
            service = binder.getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    // this stuff is used for several quick location checks before launching foreground service
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private boolean isLocationBeingRequested = false;

    private int distanceUnits;
    private TextView timerTextView;
    private TextView rideDistanceTextView;
    private TextView currentSpeedTextView;
    private TextView averageSpeedTextView;
    private TextView stopFinishButt;
    private TextView startResumeButt;
    private TextView acquiringGPSTextView;
    private TextView dot1TextView;
    private TextView dot2TextView;
    private TextView dot3TextView;
    private View gpsBackgroundColorTextView;
    private TextView autoPauseTextView;
    private View topDividingLine;

    private static float screenWidthPx;
    @ColorInt private static int purpleInt;

    // handler / runnable used to update the activity screen objects with data from RecordRideService.
    Handler handler = new Handler();
    // runnable repeatedly gets data from the service (on the main thread) and updates data on the screen.
    Runnable duringRideRunnable = new Runnable() {

        int autoPauseFadeIncrement = -8;
        int alpha = 255;
        int counter = 0; // used to make average speed update less often so it doesn't look like it's having a seizure
        final int millisPerFrame = 50;

        @Override
        public void run() {
            try {
                // call method to get timer data from foreground service
                double rawTimerMillis = service.getTimerValue();
                // convert milliseconds into much prettier hour, minute, second format
                String timerString = UnitConversion.millisToClockString(rawTimerMillis);
                timerTextView.setText(timerString);

                if (service.getCurrentLocation() != null) {
                    if (distanceUnits == App.IMPERIAL_UNITS) {
                        rideDistanceTextView.setText(UnitConversion.getDistanceStringMiles(service.getRideDistanceMeters())); // in miles
                        if (RecordRideService.getIsAutoPaused() || RecordRideService.getIsManuallyPaused()) {
                            currentSpeedTextView.setText(UnitConversion.getSpeedStringMph(0)); // just set to zero so the display doesn't cause confusion with either type of pause event
                        } else {
                            currentSpeedTextView.setText(UnitConversion.getSpeedStringMph(service.getCurrentSpeed())); // in mph
                        }
                        if (counter % 1000 == 0) averageSpeedTextView.setText(UnitConversion.getSpeedStringMph(service.getAverageSpeedMps())); // in mph
                    } else { // set to metric units or error getting units setting from file
                        rideDistanceTextView.setText(UnitConversion.getDistanceStringKilometers(service.getRideDistanceMeters())); // in km
                        if (RecordRideService.getIsAutoPaused() || RecordRideService.getIsManuallyPaused()) {
                            currentSpeedTextView.setText(UnitConversion.getSpeedStringKph(0)); // just set to zero so the display doesn't cause confusion with either type of pause event
                        } else {
                            currentSpeedTextView.setText(UnitConversion.getSpeedStringKph(service.getCurrentSpeed())); // in kph
                        }
                        if (counter % 1000 == 0) averageSpeedTextView.setText(UnitConversion.getSpeedStringKph(service.getAverageSpeedMps())); // in kph
                    }
                }

                if (RecordRideService.getIsManuallyPaused()) {
                    topDividingLine.setVisibility(View.INVISIBLE);
                    autoPauseTextView.setVisibility(View.VISIBLE);
                    autoPauseTextView.setText(getResources().getString(R.string.manually_paused_text));
                    autoPauseTextView.setBackgroundColor(getResources().getColor(R.color.red)); // should also take care of alpha
                    alpha = 255;
                } else if (RecordRideService.getIsAutoPaused()) {
                    topDividingLine.setVisibility(View.INVISIBLE);
                    autoPauseTextView.setVisibility(View.VISIBLE);
                    autoPauseTextView.setText(getResources().getString(R.string.autopaused_text));
                    if (alpha >= 255) autoPauseFadeIncrement = -8;
                    else if (alpha <= 127) autoPauseFadeIncrement = 8;
                    alpha += autoPauseFadeIncrement;
                    purpleInt = Color.argb(alpha, Color.red(purpleInt), Color.green(purpleInt), Color.blue(purpleInt));
                    autoPauseTextView.setBackgroundColor(purpleInt);
                } else {
                    topDividingLine.setVisibility(View.VISIBLE);
                    autoPauseTextView.setVisibility(View.INVISIBLE);
                    alpha = 255;
                }

            } catch (NullPointerException ignored) { }// keeps app from crashing if service is not yet bound. Happens regularly

            counter += millisPerFrame;
            // VERY important below!! decides how often the Runnable is rerun on the main thread.
            handler.postDelayed(this, millisPerFrame); // watch out for frame rate changes, animation increment is hardcoded right now...
        }
    };

    // This runnable is only meant to run once per second (for now) to make sure location data
    //      is frequent enough to allow the service to start. Technically, I could include an
    //      age check for the currentLocation object when Start is clicked. However, then the
    //      user is more likely to be frustrated if location check fails because the button did
    //      not gray out as a warning ahead of time.
    private int gpsFoundAnimationCounter = 0; // this one gets changed in onStop, gotta stay more global
    Runnable preRideRunnable = new Runnable() {
        private final int acquiringGpsPostDelayMillis = 50;
        private int dotsAnimationCounter = 0;

        @Override
        public void run() {
            // Make sure we have location data from within the last 10 seconds before allowing button clickability
            if (currentLocation != null &&
                    SystemClock.elapsedRealtimeNanos() - currentLocation.getElapsedRealtimeNanos() < 10000000000L) {
                startResumeButt.setClickable(true);// default is not clickable
                startResumeButt.setBackgroundColor(getResources().getColor(R.color.purple));

                if (currentLocation.getAccuracy() <= LOCATION_FOUND_RADIUS_METERS) {
                    gpsBackgroundColorTextView.setBackgroundColor(getResources().getColor(R.color.purple));
                    acquiringGPSTextView.setText(R.string.gps_found);
                    dot1TextView.setVisibility(View.INVISIBLE);
                    dot2TextView.setVisibility(View.INVISIBLE);
                    dot3TextView.setVisibility(View.INVISIBLE);

                    int gpsFoundPostDelay = 1000;
                    gpsFoundAnimationCounter += gpsFoundPostDelay;
                    if (gpsFoundAnimationCounter > 3000) {
                        acquiringGPSTextView.setVisibility(View.INVISIBLE);
                        gpsBackgroundColorTextView.setVisibility(View.INVISIBLE);
                    } else {
                        acquiringGPSTextView.setVisibility(View.VISIBLE);
                        gpsBackgroundColorTextView.setVisibility(View.VISIBLE);
                    }
                    dotsAnimationCounter = 0;

                    // location data looks fine, so repost runnable but at a lazy update interval just
                    //      to check in every once in a while
                    handler.postDelayed(this, gpsFoundPostDelay);

                } else { // we have shitty location data from the last 10 seconds, keep trying to refine
                    gpsBackgroundColorTextView.setBackgroundColor(getResources().getColor(R.color.pink));
                    acquiringGPSTextView.setText(R.string.refining_location_message);
                    acquiringGPSTextView.setVisibility(View.VISIBLE);
                    gpsBackgroundColorTextView.setVisibility(View.VISIBLE);
                    animateDots();
                    gpsFoundAnimationCounter = 0;

                    handler.postDelayed(this, acquiringGpsPostDelayMillis); // keep iterating fast to get better data ASAP
                }
            } else { // location not found yet, deactivate button and check again at a fast interval
                startResumeButt.setClickable(false);
                startResumeButt.setBackgroundColor(getResources().getColor(R.color.gray));

                gpsBackgroundColorTextView.setVisibility(View.VISIBLE);
                gpsBackgroundColorTextView.setBackgroundColor(getResources().getColor(R.color.red));
                acquiringGPSTextView.setVisibility(View.VISIBLE);
                acquiringGPSTextView.setText(R.string.finding_location_message);
                animateDots();
                gpsFoundAnimationCounter = 0;

                if (!isLocationBeingRequested) startLocationRequests(); // handles edge case where user just approved location permission
                handler.postDelayed(this, acquiringGpsPostDelayMillis);
            }
        }

        public void animateDots() {
            dotsAnimationCounter += acquiringGpsPostDelayMillis;
            if (dotsAnimationCounter == 500) {
                dot1TextView.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter == 1000) {
                dot2TextView.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter == 1500) {
                dot3TextView.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter >= 2000) {
                dot1TextView.setVisibility(View.INVISIBLE);
                dot2TextView.setVisibility(View.INVISIBLE);
                dot3TextView.setVisibility(View.INVISIBLE);
                dotsAnimationCounter = 0;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_ride);
        // implement keep screen on setting if needed
        if (FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.keep_screen_on_setting_filename)) == App.KEEP_SCREEN_ON_DURING_RIDES_ENABLED)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar_view_ride);
        toolbar.setTitle(""); // idk how to remove it any other way
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true); // weird stuff to prevent null pointer exception
        getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow_white);

        distanceUnits = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename));
        timerTextView = (TextView) findViewById(R.id.timerTextView);
        rideDistanceTextView = (TextView) findViewById(R.id.rideDistanceTextView);
        currentSpeedTextView = (TextView) findViewById(R.id.currentSpeedValue);
        averageSpeedTextView = (TextView) findViewById(R.id.averageSpeedValue);
        acquiringGPSTextView = (TextView) findViewById(R.id.acquiringGPSTextView);
        dot1TextView = (TextView) findViewById(R.id.dot1);
        dot2TextView = (TextView) findViewById(R.id.dot2);
        dot3TextView = (TextView) findViewById(R.id.dot3);
        gpsBackgroundColorTextView = (View) findViewById(R.id.gps_background_color);
        autoPauseTextView = (TextView) findViewById(R.id.autoPauseTextView);
        topDividingLine = (View) findViewById(R.id.divider0);

        // add units to title textviews
        TextView rideDistanceTitleTextView = (TextView) findViewById(R.id.distanceTitle);
        TextView currentSpeedTitleTextView = (TextView) findViewById(R.id.currentSpeedTitle);
        TextView averageSpeedTitleTextView = (TextView) findViewById(R.id.averageSpeedTitle);
        String distanceTitleWithUnits;
        String currentSpeedTitleWithUnits;
        String averageSpeedTitleWithUnits;
        if (distanceUnits == App.IMPERIAL_UNITS) {
            distanceTitleWithUnits = getResources().getString(R.string.distance_title_text) + " (" + getResources().getString(R.string.imperial_distance_units) + ")";
            currentSpeedTitleWithUnits = getResources().getString(R.string.current_speed_title_text) + " (" + getResources().getString(R.string.imperial_speed_units) + ")";
            averageSpeedTitleWithUnits = getResources().getString(R.string.average_speed_title_text) + " (" + getResources().getString(R.string.imperial_speed_units) + ")";
        } else { // set to metric units or error getting units setting from file
            distanceTitleWithUnits = getResources().getString(R.string.distance_title_text) + " (" + getResources().getString(R.string.metric_distance_units) + ")";
            currentSpeedTitleWithUnits = getResources().getString(R.string.current_speed_title_text) + " (" + getResources().getString(R.string.metric_speed_units) + ")";
            averageSpeedTitleWithUnits = getResources().getString(R.string.average_speed_title_text) + " (" + getResources().getString(R.string.metric_speed_units) + ")";
        }
        rideDistanceTitleTextView.setText(distanceTitleWithUnits);
        currentSpeedTitleTextView.setText(currentSpeedTitleWithUnits);
        averageSpeedTitleTextView.setText(averageSpeedTitleWithUnits);

        // default clickability is false!! set in xml
        stopFinishButt = findViewById(R.id.stopFinishTextView);
        stopFinishButt.setOnClickListener(this::onStopFinishButtonClick);
        startResumeButt = findViewById(R.id.startResumeTextView);
        startResumeButt.setOnClickListener(this::onStartResumeButtonClick);

        // It took forever to figure this out... displayMetrics.density is a multiplier
        //    from "standard" mdpi. So for my phone, which is xxhdpi, the value is 3.0.
        // side note: I don't think mdpi is actually 160 dpi, otherwise my 1080px wide
        //      phone would only be 2.25 inches wide.
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenWidthPx = displayMetrics.widthPixels;
        purpleInt = getResources().getColor(R.color.purple); // gotta assign down here or it throws a null pointer exception...

        // creates locationRequest object used later by the foreground service, but also makes sure
        //    user turns on location services if they haven't already.
        enableLocationServices(); // should I put this back in onCreate? or have a boolean to decide whether to run it? It seems like a computationally minimal operation, probs not...
        // once we know location is turned on, get permission to access fine location data
        requestLocationPermission();

        // pretty much exactly the same stuff as in the service class to ask for multiple location updates per second
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(100); // preferred location update frequency. this value is used to assign power blame by the system.
        locationRequest.setFastestInterval(50); // fastest location update frequency my app can handle
        // note -- the system on my phone seems to max out at slightly faster than 1 second updates
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); // more likely to use GPS than wifi or cell tower data
        // fusedLocationClient is an object allowing simple method calls for last location, current location, etc.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) { // locationResult will never be null with NonNull notation
                // so... I'm perplexed why I have to do this, but for some reason locationResult
                //    returns a list of length 1 instead of a simple location object. So I iterate
                //    through a for loop once to get it lol
                for (Location location : locationResult.getLocations()) {
                    // update current location with latest data
                    currentLocation = location;
                }
            }
        };
    }

    @Override
    // re-binds to service every time this activity is started. This used to be onResume,
    //      but I changed it bc I was worried about crashing due to attempted double-binding to the service
    //      (see android activity lifecycle docs... it could be possible to enter onResume twice without
    //      passing through onStop)
    protected void onStart() {
        super.onStart();
        autoPauseTextView.setVisibility(View.INVISIBLE);

        /* Bind to RecordRideService. Supposedly, it doesn't matter what order I
        bind / independently start the service. As long as I call startService()
        at some point, the system will not destroy the service -- I have to manage
        its lifecycle myself, which is what I want.

        Anyway, this block checks to see if the service was running already and only binds to
        it if true. It also handles button placement stuff. I could have tried to
        use ViewModel and/or onSaveInstanceState to preserve UI state, but this seems
        more robust -- System UI state should not simply be remembered, but be recreated
        based on the actual status of the service running / paused / not running, etc.
         */
        if (RecordRideService.isServiceCreated()) {
            // "oh shit, something is happening, better reconnect to the service to see what's going on"
            Intent intent = new Intent(this, RecordRideService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isServiceBound = true; // The ServiceConnection object does NOT do this automatically.
            handler.post(duringRideRunnable); // start updating timerTextView regardless of isTimerPaused status

            // Additionally check if ride is recording so we know how to lay out buttons.
            // Below: service is running, ride is paused. Resume and Finish buttons both visible
            if (RecordRideService.getIsManuallyPaused()) {
                autoPauseTextView.setVisibility(View.VISIBLE);

                startResumeButt.setText(R.string.resume_button);
                stopFinishButt.setText(R.string.finish_button);
                startResumeButt.setClickable(true); //unlock buttons from default
                stopFinishButt.setClickable(true);

                // According to my hero on stack overflow, this queues my dynamic
                //      button placement to occur after the layout pass started by
                //      setContentView() in onCreate(). idk what's best, but to be safe
                //      I'm posting it separately for each view to make sure each one
                //      is done before its position gets adjusted.
                // Android studio suggested I convert the anonymous runnables to lambdas
                //      and it still works, so it's different than the explanation
                //      I posted on stack overflow to answer my own question.
                stopFinishButt.post(() -> {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) stopFinishButt.getLayoutParams();
                    stopFinishButt.setX(screenWidthPx - lp.rightMargin * (float)(4/3.0) - stopFinishButt.getWidth());
                });
                startResumeButt.post(() -> {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) startResumeButt.getLayoutParams();
                    startResumeButt.setX(lp.leftMargin * (float)(4/3.0));
                });

            } else { // ride is recording (not paused). PAUSE button visible only.
                stopFinishButt.bringToFront();
                // all the other layout stuff should be set by default xml values or saved values from last time activity was visible.
                startResumeButt.setText(R.string.resume_button);
                stopFinishButt.setClickable(true); // only visible / clickable button is STOP.
                if (RecordRideService.getIsAutoPaused()) autoPauseTextView.setVisibility(View.VISIBLE);
            }
        } else { // service is not yet running, START button visible only in the center
            // none of the layout stuff below is necessary if onCreate is called. But one of the back arrows to get to this activity
            //      only calls onStart... so we have this, which makes some of the xml layout stuff redundant. whatever though. Also,
            //      I may not keep this stuff anyway because we probably want resume and finish buttons visible instead in the end...
            //      it makes it look cleaner for now though.

            // queue layout changes to occur after layout is finished
            startResumeButt.post(() -> {startResumeButt.setX(screenWidthPx / 2 - (float) startResumeButt.getWidth() / 2);});
            stopFinishButt.post(() -> {stopFinishButt.setX(screenWidthPx / 2 - (float) stopFinishButt.getWidth() / 2);});
            // change to say "start", animate movement to center
            startResumeButt.setText(R.string.start_button);
            // reorder vertical view hierarchy and clickability
            startResumeButt.bringToFront();
            stopFinishButt.setClickable(false);

            rideDistanceTextView.setText(getResources().getString(R.string.default_distance_text));
            currentSpeedTextView.setText(getResources().getString(R.string.no_ride_data_available));
            averageSpeedTextView.setText(getResources().getString(R.string.no_ride_data_available));
            timerTextView.setText(getResources().getString(R.string.default_timer_text));

            handler.post(preRideRunnable); // check for location availability before unlocking start button

            // start some location requests to get a head start before user starts the service.
            // once the user starts the service, location requests from TimerActivity are stopped (elsewhere) so we don't do redundant work.
            startLocationRequests();
        }
    }

    @Override
    // unbind service when activity is no longer visible
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            unbindService(connection);
            isServiceBound = false; // for some reason does not happen automatically in my ServiceConnection object, gotta figure that out.
        }
        handler.removeCallbacks(preRideRunnable);
        handler.removeCallbacks(duringRideRunnable);
        // as far as I can tell, I'm allowed to call this even if location updates aren't currently happening
        stopLocationRequests();
        // allow finding GPS UI to pop back up again when activity reopened
        gpsFoundAnimationCounter = 0;
    }

    // start / bind to service or just resume location finding and timer running
    // also take cares of some dynamic button layout adjustment
    public void onStartResumeButtonClick(View view) {
        // you are starting the ride from zero
        if (!isServiceBound && startResumeButt.getText().equals(getResources().getString(R.string.start_button))) {
            acquiringGPSTextView.setVisibility(View.INVISIBLE);
            gpsBackgroundColorTextView.setVisibility(View.INVISIBLE);
            dot1TextView.setVisibility(View.INVISIBLE);
            dot2TextView.setVisibility(View.INVISIBLE);
            dot3TextView.setVisibility(View.INVISIBLE);

            stopLocationRequests();
            handler.removeCallbacks(preRideRunnable); // don't want the button graying out during the ride
            startService(); // important!! allows the service to run even when unbound
            // also bind the service so the activity can display service data
            Intent intent = new Intent(this, RecordRideService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isServiceBound = true;
            handler.post(duringRideRunnable);

            startResumeButt.setClickable(false);
            stopFinishButt.setClickable(true);
            stopFinishButt.setText(R.string.stop_button);
            stopFinishButt.bringToFront();
        }
        // if you are resuming the ride
        else if (isServiceBound){
            service.manualResume(); // resume running the timer

            // reorder vertical view hierarchy
            stopFinishButt.bringToFront();  // only necessary in the edge case where activity was destroyed in between clicking STOP and now
            startResumeButt.setClickable(false);

            // change to say "stop" then move button back to center
            stopFinishButt.setText(R.string.stop_button);
            animateButtonXMovement(stopFinishButt, screenWidthPx / 2 - (float) stopFinishButt.getWidth() / 2);

            // animate movement to center
            animateButtonXMovement(startResumeButt, screenWidthPx / 2 - (float) startResumeButt.getWidth() / 2);
        }
    }

    // pause location / timer services OR unbind and kill the service and reset timer to zero.
    // Eventually need to save ride data when this button is pressed.
    public void onStopFinishButtonClick(View view) {
        if (!isServiceBound) return; // a bit of insulation against crashing

        // if you are pausing the ride:
        if (stopFinishButt.getText().equals(getResources().getString(R.string.stop_button))) {
            service.manualPause(); // pause the timer

            // change to say "finish" then move button to the right
            stopFinishButt.setText(R.string.finish_button);
            ViewGroup.MarginLayoutParams stopFinishButtLayoutParams = (ViewGroup.MarginLayoutParams) stopFinishButt.getLayoutParams();
            animateButtonXMovement(stopFinishButt, screenWidthPx - stopFinishButtLayoutParams.rightMargin * (float)(4/3.0) - stopFinishButt.getWidth()); // 4/3 multiplier evenly distributes x axis margins so middle margin isn't awkwardly large

            // animate movement to the left, reactivate resume button
            startResumeButt.setText(R.string.resume_button);
            startResumeButt.setClickable(true);
            ViewGroup.MarginLayoutParams startResumeButtLayoutParams = (ViewGroup.MarginLayoutParams) startResumeButt.getLayoutParams();
            animateButtonXMovement(startResumeButt, startResumeButtLayoutParams.leftMargin * (float)(4/3.0)); // 4/3 multiplier evenly distributes x axis margins so middle margin isn't awkwardly large

        }
        // if you are finishing the ride
        else {
            handler.removeCallbacks(duringRideRunnable);

            // returned boolean makes sure the service has finished saving ride before killing it
            String rideFolder = service.saveRide();
            unbindAndEndService();
            Toast.makeText(this, "Ride saved", Toast.LENGTH_LONG).show();

            // pull up activity with ride data and map
            Intent intent = new Intent(this, ViewRideActivity.class);
            intent.putExtra(getResources().getString(R.string.ride_folder_name), rideFolder); // have to have two Strings, so it's a bit redundant...
            intent.putExtra(getResources().getString(R.string.enable_back_button), false);
            startActivity(intent);
            finish();
        }
    }

    // called to start service. There was a big shift with API 26 to require services to call
    //      startForegroundService instead of startService, so here we are. The service promotes
    //      itself to the foreground in onStartCommand regardless of API level though
    public void startService() {
        Intent serviceIntent = new Intent(this, RecordRideService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent); // service has 5 seconds to upgrade itself to the foreground
        } else { // some internet dude said startService is fine for pre-Oreo versions. It still gets promoted to the foreground in onStartCommand regardless.
            startService(serviceIntent);
        }
    }

    // stops service. I named it endService so it's not named the same as a method it calls, that was creeping me out
    public void endService() {
        Intent serviceIntent = new Intent(this, RecordRideService.class);
        stopService(serviceIntent);
    }

    // takes care of everything necessary to unbind / terminate the service.
    private void unbindAndEndService() {
        endService();
        unbindService(connection);
        isServiceBound = false; // for some reason this does not happen automatically in my ServiceConnection class.
    }

    // These three lines kept getting repeated so here we are
    private void animateButtonXMovement(View view, float value) {
        ObjectAnimator animation = ObjectAnimator.ofFloat(view, "x", value);
        animation.setDuration(400);
        animation.start();
    }

    // Make sure location services are turned on. If request fails, prompt user to update settings to turn on location services.
    // I originally had this in the service, but it may make more sense here. More importantly, startResolutionForResult() requires
    //      an activity as one of the parameters, so I'm throwing it here. The service still needs a LocationRequest object so I make
    //      a new one over there.
    private void enableLocationServices(){
        LocationRequest locationRequest = LocationRequest.create(); // different from the locationRequest object in the foreground service
        // who knows how this shit works but it's how android docs say to make sure
        //    current system location settings are configured right
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        // at this point I have queried Settings regarding location settings
        // the LocationSettingsResponse object that was returned contains relevant info about location settings.

        task.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied! No further action necessary.
            }
        });
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(RecordRideActivity.this, 42); // second parameter here just has to be a unique positive int for referencing in callback function.

                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                        Toast.makeText(RecordRideActivity.this, "Failed to adjust location settings. Please navigate to settings app and turn on location.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    // make sure the app has permission to access fine location.
    private void requestLocationPermission() {
        // pop up request permission dialog defined here. isGranted used to be a larger lambda function till I got rid of that.
        // To restore lambda function to its former glory, refer to the interwebz (android developer location permission documentation).
        // necessary to have system pop-up dialog asking for any permission
        ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

        // pop up request permission dialog implemented here
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // ask for location permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        // TO DO: Perhaps later I can revisit best practices for permission requests / educational UIs for if statement above
        //     https://developer.android.com/training/permissions/requesting
    }

    // This method does the same things as the one in RecordRideService -- high frequency location updates
    private void startLocationRequests() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isLocationBeingRequested = true;
        } else { // location permission not granted!! Admonish the user for being a terrible person
            // TO DO: make this into a UI message that permanently displays message below in the activity so they have time to read it.
            //      "Ride recording is not possible without access to location data. Please change settings to give this app permission to access device location", Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocationRequests() {
        fusedLocationClient.removeLocationUpdates(locationCallback); // stop location updates
        isLocationBeingRequested = false; // I don't think anything depends on this but best to be safe
    }
}


////////////Toast.makeText(this, "", Toast.LENGTH_SHORT).show();
