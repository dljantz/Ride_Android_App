package com.jantztechnologies.ride;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentContainerView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.jantztechnologies.ride.databinding.ActivityViewRideBinding;

import java.util.ArrayList;
import java.util.Objects;

public class ViewRideActivity extends AppCompatActivity implements OnMapReadyCallback {

    //TODO: create button to toggle between satellite and terrain
    //TODO: Create a home button to resize back to view full route

    private int mapWidthPx;
    private int mapHeightPx;
    private double zoomOut; // so the ride polyline doesn't go right up to the edge
    private ArrayList<SerializableLocation> acceptedLocations;
    private ArrayList<Integer> manualPauseIndicesAcceptedLocations; // lists indices of every location accepted right before the user hit pause
    private ArrayList<SerializableLocation> allLocations;
    private ArrayList<Integer> manualPauseIndicesAllLocations; // indices of every very last location, accepted or not, before user hit pause
    private String rideFolder;
    private RideStats rideStats;
    private int distanceUnits;
    private NetworkElevationDataAccessor elevationGetter;
    private SerializableLocation lastElevationDefiningLocation;
    TextView redSpeedShadeExplanationTextView;
    TextView purpleSpeedShadeExplanationTextView;

    private long deleteButtonClickTime;
    private TextView deleteButt;

    // used to flip button back to say "discard" after 7 seconds
    Handler handler = new Handler();
    Runnable deleteButtonRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() > deleteButtonClickTime + 7000)
                deleteButt.setText(getResources().getString(R.string.delete_ride_button));
            else
                handler.postDelayed(deleteButtonRunnable, 100);
        }
    };

    Runnable getNetworkElevationRunnable = new Runnable() {

        // Once every second, check for elevation data returned from the interwebz to get and store
        @Override
        public void run() {
            // This is all copied from RecordRideService, but tweaked for this activity.
            // Below: grab any elevations that might be returned from the network
            boolean isReturnedNetworkElevationDatumPossiblyAvailable = true;
            boolean wereAnyElevationsAssigned = false;
            while (isReturnedNetworkElevationDatumPossiblyAvailable) {
                isReturnedNetworkElevationDatumPossiblyAvailable = tryToAssignNetworkElevationDatumToLocation();
                if (isReturnedNetworkElevationDatumPossiblyAvailable) wereAnyElevationsAssigned = true;
            }

            // if any were actually available, great! redraw graph / save to file
            if (wereAnyElevationsAssigned) {
                redrawElevationGraph(true);
                setRideStatsTextViews();
                overwriteOldRideStatsFile();
                overwriteOldAcceptedLocationsFile();
            }

            // If the last location in the list is still missing its elevation value, keep requesting data from
            //      network and re-post the runnable. otherwise, cut it off as we have all the data we need.
            if (acceptedLocations.get(acceptedLocations.size() - 1).getNetworkElevationMeters() == App.NO_ELEVATION_DATA) {
                // calling requestElevations all the time like this means that if the user suddenly
                //      gains internet access while reviewing their ride, it can still load.
                elevationGetter.requestElevations(1); // allow any query length to clean up all loose ends. If there are a lot of elevations to request, elevationGetter will maximize the length of the query automatically.
                handler.postDelayed(getNetworkElevationRunnable, 1010); // ensure we don't hit the 1 query / second limit
            }
        }
    };
/*
    Runnable speedShadingExplanationAnimationRunnable = new Runnable() {
        int millisecondsPassed = 0;
        final int loopRateMillis = 1000;
        @Override
        public void run() {
            if (millisecondsPassed >= 5000) {
                redSpeedShadeExplanationTextView.setVisibility(View.INVISIBLE);
                purpleSpeedShadeExplanationTextView.setVisibility(View.INVISIBLE);
            } else {
                millisecondsPassed += loopRateMillis;
                handler.postDelayed(speedShadingExplanationAnimationRunnable, loopRateMillis);
            }
        }
    }; */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("checkpoint 1");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_ride);
        ActivityViewRideBinding.inflate(getLayoutInflater());

        // custom toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_view_ride);
        toolbar.setTitleTextColor(getResources().getColor(R.color.black));
        toolbar.setSubtitleTextColor(getResources().getColor(R.color.black));
        setSupportActionBar(toolbar);
        // enable back arrow if the intent came from main. If not, no back arrow because we don't want to make
        //      users think they can return to the ride they just finished.
        if (getIntent().getBooleanExtra(getResources().getString(R.string.enable_back_button), true)) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true); // weird stuff to prevent null pointer exception
            getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);
        }

        // global so it can change its text to say "are you sure?"
        deleteButt = findViewById(R.id.deleteRideTextView);
        deleteButt.setOnClickListener(this::onDeleteRideClick);

        TextView exitButt = findViewById(R.id.exitViewRideTextView);
        exitButt.setOnClickListener(this::onExitButtonClick);

        prioritizeMapMovementOverScrollViewAndMakeShadingExplanationsDisappear();

        // I'm doing this here to ensure map has a reasonably accurate size going into onMapReady.
        //      Down below, onGlobalLayoutListener refines measurements (height in particular)
        //      to exclude the title bar and home / back button bar at the bottom.
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        mapWidthPx = displayMetrics.widthPixels;
        mapHeightPx = (int) (displayMetrics.heightPixels * 0.6);
        zoomOut = 0.2;

        // get all the ride stats / path information
        rideFolder = getIntent().getStringExtra(getResources().getString(R.string.ride_folder_name));
        rideStats = FileIO.loadRideStats(this, rideFolder, getResources().getString(R.string.ride_stats_filename));
        rideStats.getTimeOfDayText();
        manualPauseIndicesAllLocations = FileIO.loadArrayListIntegers(this, rideFolder, getResources().getString(R.string.manual_pause_indices_all_locations_filename));
        allLocations = FileIO.loadArrayListLocations(this, rideFolder, getResources().getString(R.string.all_locations_filename));
        manualPauseIndicesAcceptedLocations = FileIO.loadArrayListIntegers(this, rideFolder, getResources().getString(R.string.manual_pause_indices_accepted_locations_filename));
        acceptedLocations = FileIO.loadArrayListLocations(this, rideFolder, getResources().getString(R.string.accepted_locations_filename));
        System.out.println("accepted Locations is assigned");
        System.out.println(acceptedLocations);
        distanceUnits = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename));
        setRideStatsTextViews();

        // deliver necessary data to speed graph and draw
        GraphView speedGraph = findViewById(R.id.speedGraph);
        speedGraph.redraw(rideStats.distanceMeters,
                          rideStats.maxSpeedMps,
                          0,
                          acceptedLocations,
                          null, // would be needed if I was drawing a graph of ride distance over a week or month
                          distanceUnits,
                          0, // 0 is a stand in for null -- color is already defined in xml
                          false);

        // this has to be called at the beginning but also every time new elevation data is loaded, so I
        //      lumped all this code into a method so I could call it from anywhere.
        redrawElevationGraph(false);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.ride_map);
        assert mapFragment != null; // this apparently prevents null pointer exceptions...
        mapFragment.getMapAsync(this);

        FragmentContainerView fragmentContainerView = (FragmentContainerView) findViewById(R.id.ride_map);
        fragmentContainerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                fragmentContainerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapWidthPx = fragmentContainerView.getMeasuredWidth();
                mapHeightPx = fragmentContainerView.getMeasuredHeight();
            }
        });

        elevationGetter = new NetworkElevationDataAccessor(this);
        getMissingElevationData();

        redSpeedShadeExplanationTextView = findViewById(R.id.speedShadingExplanationRedTextView);
        purpleSpeedShadeExplanationTextView = findViewById(R.id.speedShadingExplanationPurpleTextView);
        // if this is the first ride and we're viewing it within the first 8 hours of recording,
        //      show the speed shading explanation for a while. Touching the map makes them
        //      disappear. That's handled by an onTouchListener in prioritizeMapMovementOverScrollViewAndMakeShadingExplanationsDisappear().
        if (FileIO.getRideFolderNumber(rideFolder) == 0 &&
                System.currentTimeMillis() - rideStats.timestamp < 1000 * 60 * 60 * 8) {
            redSpeedShadeExplanationTextView.setVisibility(View.VISIBLE);
            purpleSpeedShadeExplanationTextView.setVisibility(View.VISIBLE);
        }
    }

    // this has to be called at the beginning but also every time new elevation data is loaded, so I
    //      lumped all this code into a method so I could call it from anywhere.
    private void redrawElevationGraph(boolean isLateRedraw) {
        // deliver necessary data to elevation graph and draw. But first make sure we're not passing
        //      in a bunch of points that have their default elevation value of -9999, that leads
        //      to some wacko graphs
        ArrayList<SerializableLocation> networkElevationLocations = new ArrayList<>();
        for (SerializableLocation l : acceptedLocations) {
            if (l.getNetworkElevationMeters() != App.NO_ELEVATION_DATA) {
                networkElevationLocations.add(l);
            }
        }
        GraphView elevationGraph = findViewById(R.id.elevationGraph);
        elevationGraph.redraw(rideStats.distanceMeters,
                rideStats.getMaxElevationMeters(),
                rideStats.getMinElevationMeters(),
                networkElevationLocations,
                null,
                distanceUnits,
                0,
                isLateRedraw);
    }

    // android studio wanted me to call performClick, which is unnecessary methinks
    @SuppressLint("ClickableViewAccessibility")
    private void prioritizeMapMovementOverScrollViewAndMakeShadingExplanationsDisappear() {
        ScrollView scrollView = (ScrollView) findViewById(R.id.viewRideScrollView);
        View transparentView = (View) findViewById(R.id.transparentView);

        transparentView.setOnTouchListener((view, event) -> {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:

                case MotionEvent.ACTION_MOVE:
                    // Disallow ScrollView from intercepting touch events.
                    scrollView.requestDisallowInterceptTouchEvent(true);
                    // makes sure red / purple route shading textViews go away
                    redSpeedShadeExplanationTextView.setVisibility(View.INVISIBLE);
                    purpleSpeedShadeExplanationTextView.setVisibility(View.INVISIBLE);
                    // Disable touch on transparent view
                    return false;

                case MotionEvent.ACTION_UP:
                    // Allow ScrollView to intercept touch events.
                    scrollView.requestDisallowInterceptTouchEvent(false);
                    return true;

                default:
                    return true;
            }
        });
    }

    // Manipulates the map once available, triggered by OnMapReadyCallback interface
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MapIllustrator mapIllustrator = new MapIllustrator(
                this,
                mapWidthPx,
                mapHeightPx,
                googleMap,
                rideFolder,
                manualPauseIndicesAllLocations,
                manualPauseIndicesAcceptedLocations,
                allLocations,
                acceptedLocations,
                rideStats);
        mapIllustrator.setMapStyle(); // light / dark / satellite, that sort of thing
        mapIllustrator.setMapSettings(true); // zoom controls enabled = true
        mapIllustrator.establishCameraBounds(zoomOut);
        mapIllustrator.generatePolylineOptions(5);
        mapIllustrator.drawPolylines();
        saveThumbnail(this, googleMap);

        ImageView recenterButton = findViewById(R.id.recenterImageView);
        recenterButton.setOnClickListener(view -> mapIllustrator.animateCameraRecenter());
    }

    private void setRideStatsTextViews() {
        TextView rideDistanceTextView = (TextView) findViewById(R.id.totalDistanceValueTextView);
        TextView rideDurationTextView = (TextView) findViewById(R.id.totalRideDurationValueTextView);
        TextView averageSpeedTextView = (TextView) findViewById(R.id.totalAverageSpeedValueTextView);
        TextView maxSpeedTextView = (TextView) findViewById(R.id.maxSpeedValueTextView);
        TextView elevationGainTextView = (TextView) findViewById(R.id.elevationGainValueTextView);
        TextView elevationLossTextView = (TextView) findViewById(R.id.elevationLossValueTextView);

        getSupportActionBar().setTitle(rideStats.getTimeOfDayText());
        getSupportActionBar().setSubtitle(rideStats.getDateText(true));
        String rideDistanceText;
        String rideDurationText;
        String averageSpeedText;
        String maxSpeedText;
        String elevationGainText;
        String elevationLossText;

        // doesn't need to be in if else block because time units don't change
        try {
            rideDurationText = UnitConversion.millisToClockString(rideStats.durationMillis);
        } catch (NullPointerException e) {
            rideDurationText = "";
        }

        if (distanceUnits == App.IMPERIAL_UNITS) {
            try {
                rideDistanceText = UnitConversion.getDistanceStringMiles(rideStats.distanceMeters) + " mi";
            } catch (NullPointerException e) {
                rideDistanceText = "";
            }
            try {
                averageSpeedText = UnitConversion.getSpeedStringMph(rideStats.averageSpeedMps) + " mph";
            } catch (NullPointerException e) {
                averageSpeedText = "";
            }
            try {
                maxSpeedText = UnitConversion.getSpeedStringMph(rideStats.maxSpeedMps) + " mph";
            } catch (NullPointerException e) {
                maxSpeedText = "";
            }
            try {
                elevationGainText = UnitConversion.getElevationStringFeet(rideStats.getElevationGainMeters()) + " ft";
            } catch (NullPointerException e) {
                elevationGainText = "";
            }
            try {
                elevationLossText = UnitConversion.getElevationStringFeet(rideStats.getElevationLossMeters()) + " ft";
            } catch (NullPointerException e) {
                elevationLossText = "";
            }

        } else { // metric units or error getting units setting from file
            try {
                rideDistanceText = UnitConversion.getDistanceStringKilometers(rideStats.distanceMeters) + " km";
            } catch (NullPointerException e) {
                rideDistanceText = "";
            }
            try {
                averageSpeedText = UnitConversion.getSpeedStringKph(rideStats.averageSpeedMps) + " kph";
            } catch (NullPointerException e) {
                averageSpeedText = "";
            }
            try {
                maxSpeedText = UnitConversion.getSpeedStringKph(rideStats.maxSpeedMps) + " kph";
            } catch (NullPointerException e) {
                maxSpeedText = "";
            }
            try {
                elevationGainText = UnitConversion.getElevationStringMeters(rideStats.getElevationGainMeters()) + " m";
            } catch (NullPointerException e) {
                elevationGainText = "";
            }
            try {
                elevationLossText = UnitConversion.getElevationStringMeters(rideStats.getElevationLossMeters()) + " m";
            } catch (NullPointerException e) {
                elevationLossText = "";
            }
        }

        rideDistanceTextView.setText(rideDistanceText);
        rideDurationTextView.setText(rideDurationText);
        averageSpeedTextView.setText(averageSpeedText);
        maxSpeedTextView.setText(maxSpeedText);
        elevationGainTextView.setText(elevationGainText);
        elevationLossTextView.setText(elevationLossText);
        elevationLossTextView.setText(elevationLossText);
    }

    // capture bitmap and save to file as the MainActivity thumbnail
    private void saveThumbnail(Context context, GoogleMap googleMap) {
        // make sure to wait until tiles, styles, labels are good to go
        googleMap.setOnMapLoadedCallback(() -> {
            GoogleMap.SnapshotReadyCallback callback = bitmap -> {
                // below -- crop out the google logo, no free advertising here thank you very much
                Bitmap croppedBitmap = null;
                if (bitmap != null) {
                    double cropAmount = zoomOut / 2; // shooting to cut 10% off all sides
                    int topY = (int) ((cropAmount) * bitmap.getHeight());
                    // 0.92 multiplier for last parameter did the trick on my phone, play it safe and do a bit less
                    int height = (int) ((1 - cropAmount * 2) * bitmap.getHeight());
                    // chop width down if needed, but defs don't increase it or the app will crash
                    int width = Math.min(height, bitmap.getWidth());
                    // if leftX ends up negative, the app crashes. But that should be IMPOSSIBLE
                    //      as width has to be equal to or less than bitmap.getWidth().
                    int leftX = (int) (bitmap.getWidth() - width) / 2;

                    croppedBitmap = Bitmap.createBitmap(bitmap, leftX, topY, width, height);
                }
                ////////////final Bitmap lastBitmap = Bitmap.createScaledBitmap(finalBitmap, 144, 144, true);
                FileIO.saveBitmap(context, rideFolder, getResources().getString(R.string.route_thumbnail_filename), croppedBitmap);
            };
            googleMap.snapshot(callback);
        });
    }

    private void onDeleteRideClick(View view) {
        rideFolder = getIntent().getStringExtra(getResources().getString(R.string.ride_folder_name));
        if (deleteButt.getText().equals(getResources().getString(R.string.delete_ride_button))) {
            deleteButt.setText(getResources().getString(R.string.discard_ride_button_verification));
            deleteButtonClickTime = System.currentTimeMillis();
            handler.post(deleteButtonRunnable);
        } else if (FileIO.deleteFolder(this, rideFolder)){ // they already clicked it once, time to actually delete ride data
            Toast.makeText(this, "Ride deleted", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    // file already saved, just launch MainActivity on exit button press
    private void onExitButtonClick(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void getMissingElevationData() {
        int lastElevationIndex = findLastElevationIndex();
        try {
            lastElevationDefiningLocation = acceptedLocations.get(lastElevationIndex); // used for elevation gain / loss stats
        } catch (IndexOutOfBoundsException ignored) { } // expected if acceptedLocations is empty

        // figure out if we're missing any elevation data. If so, iterate through the rest of the
        //      list starting with the next location after the last one with a received network elevation
        //      value. Grab each location that's supposed to be elevation-defining and throw it in a list
        //      with elevationGetter to be included in a query to opentopodata
        if (lastElevationIndex != acceptedLocations.size() - 1) { // if acceptedLocations is empty, this statement is checking if -1 != -1, so the if statement does not run and the runnable is never posted.
            for (int i = lastElevationIndex + 1; i < acceptedLocations.size(); i++) {
                if (acceptedLocations.get(i).getIsElevationDefining()) {
                    double latitude = acceptedLocations.get(i).latitude;
                    double longitude = acceptedLocations.get(i).longitude;
                    elevationGetter.addCoordinatesToQuery(latitude, longitude, i);
                }
            }
            handler.post(getNetworkElevationRunnable); // start asking for and then receiving chunks of elevation data
        }
    }

    // loop backwards through acceptedLocations to see if we're missing elevation data, and if so,
    //      determine what index has the last location with network elevation data.
    private int findLastElevationIndex() {
        int lastLocationWithNetworkElevation = -1; // default assumption: no elevation data at all
        for (int i = acceptedLocations.size() - 1; i >= 0; i--) {
            // index out of bounds exception avoided if list size is zero -- i will be -1, violating i >= 0 requirement
            if (acceptedLocations.get(i).getNetworkElevationMeters() != App.NO_ELEVATION_DATA) {
                lastLocationWithNetworkElevation = i;
                break;
            }
        }
        return lastLocationWithNetworkElevation;
    }

    // to be called by while loop -- keeps going until this method returns false.
    // This method is pretty much copied from RecordRideService.
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
                // below: elevation gain / loss & max / min stat tracking.
                updateElevationStats(location);
                //      probably has to be adjusted anyway to account for the fact that I'm pulling rideStats
                //      from file anyway
                return true; // signal that there are probs more data out there to get
            }
        } catch (IndexOutOfBoundsException ignored) {
            // should happen often, as elevation data are constantly getting emptied out of elevationGetter...
            //      so the the list we're pulling from is often empty and that's fine
        }
        return false; // if we ever make it this far, we're done pulling data for now; returning false cuts off the while loop this method is called from.
    }

    // copied and adapted from RecordRideService
    // does elevation gain, loss, max, and min
    private void updateElevationStats(SerializableLocation location) {

        // case 1: for some reason no elevation data at all were acquired during the ride,
        //      so here we'll just set some baseline values. The way I'm checking for this
        //      is actually checking if there was literally no elevation change
        //      during the ride, which can technically occur on ridiculously flat / short rides.
        //      But that's ok because we can still handle it the same way -- just (re)set the baseline
        //      max and min values above and below the current elevation.
        if (lastElevationDefiningLocation == null) { // todo: Does this work for all cases? list with no elevations assigned, list with some elevations assigned
        ////////////////////if (rideStats.getElevationGainMeters() == 0 && rideStats.getElevationLossMeters() == 0) {
            rideStats.setMaxElevationMeters(location.getNetworkElevationMeters() + 10);
            rideStats.setMinElevationMeters(location.getNetworkElevationMeters() - 10);
        }

        // case 2: not the first time it's called, gotta do some work to update elevation stats
        else if (!location.getWasManuallyResumedRightBeforeThisLocation()){
            // gain / loss stuff
            double elevationDifference = location.getNetworkElevationMeters() - lastElevationDefiningLocation.getNetworkElevationMeters();
            if (elevationDifference > 0) rideStats.setElevationGainMeters(rideStats.getElevationGainMeters() + elevationDifference);
            else rideStats.setElevationLossMeters(rideStats.getElevationLossMeters() - elevationDifference); // subtracting a negative keeps this variable positive
        }

        // max / min stuff
        if (location.getNetworkElevationMeters() > rideStats.getMaxElevationMeters()) rideStats.setMaxElevationMeters(location.getNetworkElevationMeters());
        else if (location.getNetworkElevationMeters() < rideStats.getMinElevationMeters()) rideStats.setMinElevationMeters(location.getNetworkElevationMeters());

        lastElevationDefiningLocation = location; // update so we're ready for the next run through
    }

    // needs context to work, hence this is a separate method outside the runnable
    private void overwriteOldRideStatsFile() {
        // overwrite previous files
        FileIO.saveRideStats(this, rideFolder, rideStats, getResources().getString(R.string.ride_stats_filename));
    }

    // needs context to work, hence this is a separate method outside the runnable
    private void overwriteOldAcceptedLocationsFile() {
        FileIO.saveArrayListLocations(this, rideFolder, acceptedLocations, getResources().getString(R.string.accepted_locations_filename));
    }
}


/* notes:
great documentation:
https://developers.google.com/maps/documentation/android-sdk/views#center-map

Can make the app respond to camera movement

can make app respond to touch events on map -- the callback method
    OnMapClickListener.onMapClick() responds to a single tap on the map.
    could use this to add/remove distance markers on the route polyline.

map.getMinimumZoomLevel() can tell me what zoom that device is capable of.

I think CameraPosition / CameraPosition.Builder might be needed if I want to change tilt and stuff?
    pretty good code example at the end of the camera and view documentation page

map.moveCamera moves it instantly, map.animateCamera animates it

remove POIs. have to use JSON, whatever that is https://developers.google.com/maps/documentation/android-sdk/hiding-features


*/