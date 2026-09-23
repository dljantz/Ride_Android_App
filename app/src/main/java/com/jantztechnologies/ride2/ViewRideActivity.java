package com.jantztechnologies.ride2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.jantztechnologies.ride2.databinding.ActivityViewRideBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ViewRideActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityViewRideBinding binding;
    private long rideId;
    private RideEntity rideEntity;
    private RideStats rideStats;
    private int distanceUnits;

    private int mapWidthPx;
    private int mapHeightPx;
    private final double zoomOut = 0.2;

    private ArrayList<SerializableLocation> acceptedLocations;
    private ArrayList<Integer> manualPauseIndicesAcceptedLocations;
    private ArrayList<SerializableLocation> allLocations;
    private ArrayList<Integer> manualPauseIndicesAllLocations;

    private NetworkElevationDataAccessor elevationGetter;
    private SerializableLocation lastElevationDefiningLocation;

    private long deleteButtonClickTime;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable deleteButtonRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() > deleteButtonClickTime + 7000) {
                binding.deleteRideTextView.setText(getString(R.string.delete_ride_button));
            } else {
                handler.postDelayed(this, 100);
            }
        }
    };

    private final Runnable getNetworkElevationRunnable = new Runnable() {
        @Override
        public void run() {
            boolean isReturnedNetworkElevationDatumPossiblyAvailable = true;
            boolean wereAnyElevationsAssigned = false;
            while (isReturnedNetworkElevationDatumPossiblyAvailable) {
                isReturnedNetworkElevationDatumPossiblyAvailable = tryToAssignNetworkElevationDatumToLocation();
                if (isReturnedNetworkElevationDatumPossiblyAvailable) {
                    wereAnyElevationsAssigned = true;
                }
            }

            if (wereAnyElevationsAssigned) {
                redrawElevationGraph(true);
                setRideStatsTextViews();
                persistElevationUpdates();
            }

            if (!acceptedLocations.isEmpty() &&
                    acceptedLocations.get(acceptedLocations.size() - 1).getNetworkElevationMeters() == App.NO_ELEVATION_DATA) {
                elevationGetter.requestElevations(1);
                handler.postDelayed(this, 1010);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewRideBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Read ride ID
        rideId = getIntent().getLongExtra("ride_id", -1);
        if (rideId == -1) {
            String folderName = getIntent().getStringExtra(getString(R.string.ride_folder_name));
            if (folderName != null && folderName.startsWith("ride_")) {
                try {
                    rideId = Long.parseLong(folderName.substring("ride_".length()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Query database for ride
        AppDatabase db = AppDatabase.getInstance(this);
        rideEntity = db.rideDao().getRideById(rideId);
        if (rideEntity == null) {
            Toast.makeText(this, "Ride data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<TrackPointEntity> points = db.trackPointDao().getTrackPointsForRide(rideId);
        buildLocationLists(points);

        rideStats = new RideStats(
                rideEntity.timestamp,
                rideEntity.distanceMeters,
                rideEntity.averageSpeedMps,
                rideEntity.maxSpeedMps,
                rideEntity.maxElevationMeters,
                rideEntity.minElevationMeters,
                rideEntity.durationMillis,
                rideEntity.elevationGainMeters,
                rideEntity.elevationLossMeters
        );

        distanceUnits = PreferencesManager.getInstance(this).getDistanceUnits();

        // Setup Toolbar
        binding.toolbarViewRide.setTitleTextColor(ContextCompat.getColor(this, R.color.black));
        binding.toolbarViewRide.setSubtitleTextColor(ContextCompat.getColor(this, R.color.black));
        setSupportActionBar(binding.toolbarViewRide);

        if (getIntent().getBooleanExtra(getString(R.string.enable_back_button), true)) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);
            }
            binding.toolbarViewRide.setNavigationOnClickListener(v -> finish());
        }

        binding.deleteRideTextView.setOnClickListener(this::onDeleteRideClick);
        binding.exitViewRideTextView.setOnClickListener(this::onExitButtonClick);

        prioritizeMapMovementOverScrollViewAndMakeShadingExplanationsDisappear();

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        mapWidthPx = displayMetrics.widthPixels;
        mapHeightPx = (int) (displayMetrics.heightPixels * 0.6);

        setRideStatsTextViews();

        // Graphs
        binding.speedGraph.redraw(
                rideStats.distanceMeters,
                rideStats.maxSpeedMps,
                0,
                acceptedLocations,
                null,
                distanceUnits,
                0,
                false
        );

        redrawElevationGraph(false);

        // Map setup
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.ride_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        binding.rideMap.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.rideMap.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapWidthPx = binding.rideMap.getMeasuredWidth();
                mapHeightPx = binding.rideMap.getMeasuredHeight();
            }
        });

        elevationGetter = new NetworkElevationDataAccessor(this);
        getMissingElevationData();

        // Speed shading explanation for recent first rides
        if (rideId == 1 && (System.currentTimeMillis() - rideStats.timestamp < 1000L * 60 * 60 * 8)) {
            binding.speedShadingExplanationRedTextView.setVisibility(View.VISIBLE);
            binding.speedShadingExplanationPurpleTextView.setVisibility(View.VISIBLE);
        }
    }

    private void buildLocationLists(List<TrackPointEntity> points) {
        acceptedLocations = new ArrayList<>();
        manualPauseIndicesAcceptedLocations = new ArrayList<>();
        allLocations = new ArrayList<>();
        manualPauseIndicesAllLocations = new ArrayList<>();

        if (points == null) return;

        for (TrackPointEntity tp : points) {
            SerializableLocation loc = SerializableLocation.fromTrackPoint(tp);
            if (tp.isAccepted) {
                acceptedLocations.add(loc);
                if (tp.isManualPauseBreak) {
                    manualPauseIndicesAcceptedLocations.add(acceptedLocations.size() - 1);
                }
            }
            allLocations.add(loc);
            if (tp.isManualPauseBreak) {
                manualPauseIndicesAllLocations.add(allLocations.size() - 1);
            }
        }
    }

    private void redrawElevationGraph(boolean isLateRedraw) {
        ArrayList<SerializableLocation> networkElevationLocations = new ArrayList<>();
        for (SerializableLocation l : acceptedLocations) {
            if (l.getNetworkElevationMeters() != App.NO_ELEVATION_DATA) {
                networkElevationLocations.add(l);
            }
        }
        binding.elevationGraph.redraw(
                rideStats.distanceMeters,
                rideStats.getMaxElevationMeters(),
                rideStats.getMinElevationMeters(),
                networkElevationLocations,
                null,
                distanceUnits,
                0,
                isLateRedraw
        );
    }

    @SuppressLint("ClickableViewAccessibility")
    private void prioritizeMapMovementOverScrollViewAndMakeShadingExplanationsDisappear() {
        binding.transparentView.setOnTouchListener((view, event) -> {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    binding.viewRideScrollView.requestDisallowInterceptTouchEvent(true);
                    binding.speedShadingExplanationRedTextView.setVisibility(View.INVISIBLE);
                    binding.speedShadingExplanationPurpleTextView.setVisibility(View.INVISIBLE);
                    return false;
                case MotionEvent.ACTION_UP:
                    binding.viewRideScrollView.requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MapIllustrator mapIllustrator = new MapIllustrator(
                this,
                mapWidthPx,
                mapHeightPx,
                googleMap,
                "ride_" + rideId,
                manualPauseIndicesAllLocations,
                manualPauseIndicesAcceptedLocations,
                allLocations,
                acceptedLocations,
                rideStats
        );
        mapIllustrator.setMapStyle();
        mapIllustrator.setMapSettings(true);
        mapIllustrator.establishCameraBounds(zoomOut);
        mapIllustrator.generatePolylineOptions(5);
        mapIllustrator.drawPolylines();
        saveThumbnail(this, googleMap);

        binding.recenterImageView.setOnClickListener(view -> mapIllustrator.animateCameraRecenter());
    }

    private void setRideStatsTextViews() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(UnitConversion.getTimeOfDayText(rideStats.timestamp));
            getSupportActionBar().setSubtitle(UnitConversion.getDateText(rideStats.timestamp, true));
        }

        String rideDistanceText;
        String rideDurationText = UnitConversion.millisToClockString((long) rideStats.durationMillis);
        String averageSpeedText;
        String maxSpeedText;
        String elevationGainText;
        String elevationLossText;

        if (distanceUnits == App.IMPERIAL_UNITS) {
            rideDistanceText = UnitConversion.getDistanceStringMiles(rideStats.distanceMeters) + " mi";
            averageSpeedText = UnitConversion.getSpeedStringMph(rideStats.averageSpeedMps) + " mph";
            maxSpeedText = UnitConversion.getSpeedStringMph(rideStats.maxSpeedMps) + " mph";
            elevationGainText = UnitConversion.getElevationStringFeet(rideStats.getElevationGainMeters()) + " ft";
            elevationLossText = UnitConversion.getElevationStringFeet(rideStats.getElevationLossMeters()) + " ft";
        } else {
            rideDistanceText = UnitConversion.getDistanceStringKilometers(rideStats.distanceMeters) + " km";
            averageSpeedText = UnitConversion.getSpeedStringKph(rideStats.averageSpeedMps) + " kph";
            maxSpeedText = UnitConversion.getSpeedStringKph(rideStats.maxSpeedMps) + " kph";
            elevationGainText = UnitConversion.getElevationStringMeters(rideStats.getElevationGainMeters()) + " m";
            elevationLossText = UnitConversion.getElevationStringMeters(rideStats.getElevationLossMeters()) + " m";
        }

        binding.totalDistanceValueTextView.setText(rideDistanceText);
        binding.totalRideDurationValueTextView.setText(rideDurationText);
        binding.totalAverageSpeedValueTextView.setText(averageSpeedText);
        binding.maxSpeedValueTextView.setText(maxSpeedText);
        binding.elevationGainValueTextView.setText(elevationGainText);
        binding.elevationLossValueTextView.setText(elevationLossText);
    }

    private void saveThumbnail(Context context, GoogleMap googleMap) {
        googleMap.setOnMapLoadedCallback(() -> {
            googleMap.snapshot(bitmap -> {
                if (bitmap != null) {
                    double cropAmount = zoomOut / 2;
                    int topY = (int) (cropAmount * bitmap.getHeight());
                    int height = (int) ((1 - cropAmount * 2) * bitmap.getHeight());
                    int width = Math.min(height, bitmap.getWidth());
                    int leftX = (int) (bitmap.getWidth() - width) / 2;

                    Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, leftX, topY, width, height);

                    File thumbnailsDir = new File(context.getFilesDir(), "thumbnails");
                    if (!thumbnailsDir.exists()) {
                        thumbnailsDir.mkdirs();
                    }
                    File thumbnailFile = new File(thumbnailsDir, "ride_" + rideId + ".png");
                    try (FileOutputStream fos = new FileOutputStream(thumbnailFile)) {
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                        rideEntity.thumbnailPath = thumbnailFile.getAbsolutePath();
                        AppDatabase.getInstance(context).rideDao().updateRide(rideEntity);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    private void onDeleteRideClick(View view) {
        if (binding.deleteRideTextView.getText().equals(getString(R.string.delete_ride_button))) {
            binding.deleteRideTextView.setText(getString(R.string.discard_ride_button_verification));
            deleteButtonClickTime = System.currentTimeMillis();
            handler.post(deleteButtonRunnable);
        } else {
            AppDatabase.getInstance(this).rideDao().deleteRideById(rideId);
            if (rideEntity.thumbnailPath != null) {
                File thumb = new File(rideEntity.thumbnailPath);
                if (thumb.exists()) {
                    thumb.delete();
                }
            }
            Toast.makeText(this, "Ride deleted", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void onExitButtonClick(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void getMissingElevationData() {
        int lastElevationIndex = findLastElevationIndex();
        try {
            lastElevationDefiningLocation = acceptedLocations.get(lastElevationIndex);
        } catch (IndexOutOfBoundsException ignored) {
        }

        if (lastElevationIndex != acceptedLocations.size() - 1) {
            for (int i = lastElevationIndex + 1; i < acceptedLocations.size(); i++) {
                if (acceptedLocations.get(i).getIsElevationDefining()) {
                    double latitude = acceptedLocations.get(i).latitude;
                    double longitude = acceptedLocations.get(i).longitude;
                    elevationGetter.addCoordinatesToQuery(latitude, longitude, i);
                }
            }
            handler.post(getNetworkElevationRunnable);
        }
    }

    private int findLastElevationIndex() {
        int lastLocationWithNetworkElevation = -1;
        for (int i = acceptedLocations.size() - 1; i >= 0; i--) {
            if (acceptedLocations.get(i).getNetworkElevationMeters() != App.NO_ELEVATION_DATA) {
                lastLocationWithNetworkElevation = i;
                break;
            }
        }
        return lastLocationWithNetworkElevation;
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

    private void updateElevationStats(SerializableLocation location) {
        if (lastElevationDefiningLocation == null) {
            rideStats.setMaxElevationMeters(location.getNetworkElevationMeters() + 10);
            rideStats.setMinElevationMeters(location.getNetworkElevationMeters() - 10);
        } else if (!location.getWasManuallyResumedRightBeforeThisLocation()) {
            double elevationDifference = location.getNetworkElevationMeters() - lastElevationDefiningLocation.getNetworkElevationMeters();
            if (elevationDifference > 0) {
                rideStats.setElevationGainMeters(rideStats.getElevationGainMeters() + elevationDifference);
            } else {
                rideStats.setElevationLossMeters(rideStats.getElevationLossMeters() - elevationDifference);
            }
        }

        if (location.getNetworkElevationMeters() > rideStats.getMaxElevationMeters()) {
            rideStats.setMaxElevationMeters(location.getNetworkElevationMeters());
        } else if (location.getNetworkElevationMeters() < rideStats.getMinElevationMeters()) {
            rideStats.setMinElevationMeters(location.getNetworkElevationMeters());
        }

        lastElevationDefiningLocation = location;
    }

    private void persistElevationUpdates() {
        rideEntity.maxElevationMeters = rideStats.getMaxElevationMeters();
        rideEntity.minElevationMeters = rideStats.getMinElevationMeters();
        rideEntity.elevationGainMeters = rideStats.getElevationGainMeters();
        rideEntity.elevationLossMeters = rideStats.getElevationLossMeters();
        AppDatabase.getInstance(this).rideDao().updateRide(rideEntity);

        List<TrackPointEntity> updatedPoints = new ArrayList<>();
        for (SerializableLocation loc : acceptedLocations) {
            updatedPoints.add(loc.toTrackPoint(rideId, true));
        }
        AppDatabase.getInstance(this).trackPointDao().insertTrackPoints(updatedPoints);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}