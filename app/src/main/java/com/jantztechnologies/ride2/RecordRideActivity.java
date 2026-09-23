package com.jantztechnologies.ride2;

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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.gms.tasks.Task;
import com.jantztechnologies.ride2.databinding.ActivityRecordRideBinding;

import java.util.ArrayList;
import java.util.List;

public class RecordRideActivity extends AppCompatActivity {
    private static final int LOCATION_FOUND_RADIUS_METERS = 25;

    private ActivityRecordRideBinding binding;
    private RecordRideService service;
    private boolean isServiceBound = false;
    private PreferencesManager preferencesManager;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            RecordRideService.RecordRideBinder binder = (RecordRideService.RecordRideBinder) iBinder;
            service = binder.getService();
            isServiceBound = true;
            subscribeToServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            service = null;
        }
    };

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private boolean isLocationBeingRequested = false;

    private int distanceUnits;
    private static float screenWidthPx;
    @ColorInt private static int purpleInt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int gpsFoundAnimationCounter = 0;

    private final Runnable preRideRunnable = new Runnable() {
        private final int acquiringGpsPostDelayMillis = 50;
        private int dotsAnimationCounter = 0;

        @Override
        public void run() {
            if (currentLocation != null &&
                    SystemClock.elapsedRealtimeNanos() - currentLocation.getElapsedRealtimeNanos() < 10000000000L) {
                binding.startResumeTextView.setClickable(true);
                binding.startResumeTextView.setBackgroundColor(ContextCompat.getColor(RecordRideActivity.this, R.color.purple));

                if (currentLocation.getAccuracy() <= LOCATION_FOUND_RADIUS_METERS) {
                    binding.gpsBackgroundColor.setBackgroundColor(ContextCompat.getColor(RecordRideActivity.this, R.color.purple));
                    binding.acquiringGPSTextView.setText(R.string.gps_found);
                    binding.dot1.setVisibility(View.INVISIBLE);
                    binding.dot2.setVisibility(View.INVISIBLE);
                    binding.dot3.setVisibility(View.INVISIBLE);

                    int gpsFoundPostDelay = 1000;
                    gpsFoundAnimationCounter += gpsFoundPostDelay;
                    if (gpsFoundAnimationCounter > 3000) {
                        binding.acquiringGPSTextView.setVisibility(View.INVISIBLE);
                        binding.gpsBackgroundColor.setVisibility(View.INVISIBLE);
                    } else {
                        binding.acquiringGPSTextView.setVisibility(View.VISIBLE);
                        binding.gpsBackgroundColor.setVisibility(View.VISIBLE);
                    }
                    dotsAnimationCounter = 0;

                    handler.postDelayed(this, gpsFoundPostDelay);

                } else {
                    binding.gpsBackgroundColor.setBackgroundColor(ContextCompat.getColor(RecordRideActivity.this, R.color.pink));
                    binding.acquiringGPSTextView.setText(R.string.refining_location_message);
                    binding.acquiringGPSTextView.setVisibility(View.VISIBLE);
                    binding.gpsBackgroundColor.setVisibility(View.VISIBLE);
                    animateDots();
                    gpsFoundAnimationCounter = 0;

                    handler.postDelayed(this, acquiringGpsPostDelayMillis);
                }
            } else {
                binding.startResumeTextView.setClickable(false);
                binding.startResumeTextView.setBackgroundColor(ContextCompat.getColor(RecordRideActivity.this, R.color.gray));

                binding.gpsBackgroundColor.setVisibility(View.VISIBLE);
                binding.gpsBackgroundColor.setBackgroundColor(ContextCompat.getColor(RecordRideActivity.this, R.color.red));
                binding.acquiringGPSTextView.setVisibility(View.VISIBLE);
                binding.acquiringGPSTextView.setText(R.string.finding_location_message);
                animateDots();
                gpsFoundAnimationCounter = 0;

                if (!isLocationBeingRequested) {
                    startLocationRequests();
                }
                handler.postDelayed(this, acquiringGpsPostDelayMillis);
            }
        }

        private void animateDots() {
            dotsAnimationCounter += acquiringGpsPostDelayMillis;
            if (dotsAnimationCounter == 500) {
                binding.dot1.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter == 1000) {
                binding.dot2.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter == 1500) {
                binding.dot3.setVisibility(View.VISIBLE);
            } else if (dotsAnimationCounter >= 2000) {
                binding.dot1.setVisibility(View.INVISIBLE);
                binding.dot2.setVisibility(View.INVISIBLE);
                binding.dot3.setVisibility(View.INVISIBLE);
                dotsAnimationCounter = 0;
            }
        }
    };

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordRideBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferencesManager = PreferencesManager.getInstance(this);

        if (preferencesManager.isKeepScreenOnEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        binding.toolbarViewRide.setTitle("");
        setSupportActionBar(binding.toolbarViewRide);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow_white);
        }
        binding.toolbarViewRide.setNavigationOnClickListener(v -> finish());

        distanceUnits = preferencesManager.getDistanceUnits();
        setupUnitsLabels();

        binding.stopFinishTextView.setOnClickListener(this::onStopFinishButtonClick);
        binding.startResumeTextView.setOnClickListener(this::onStartResumeButtonClick);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenWidthPx = displayMetrics.widthPixels;
        purpleInt = ContextCompat.getColor(this, R.color.purple);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        startLocationRequests();
                    }
                }
        );

        enableLocationServices();
        requestRequiredPermissions();

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(100);
        locationRequest.setFastestInterval(50);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    currentLocation = location;
                }
            }
        };

        // Observe service running state
        RecordRideService.isServiceRunningLiveData.observe(this, isRunning -> {
            if (isRunning && !isServiceBound) {
                Intent intent = new Intent(this, RecordRideService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE);
            }
        });
    }

    private void setupUnitsLabels() {
        String distanceTitleWithUnits;
        String currentSpeedTitleWithUnits;
        String averageSpeedTitleWithUnits;
        if (distanceUnits == App.IMPERIAL_UNITS) {
            distanceTitleWithUnits = getString(R.string.distance_title_text) + " (" + getString(R.string.imperial_distance_units) + ")";
            currentSpeedTitleWithUnits = getString(R.string.current_speed_title_text) + " (" + getString(R.string.imperial_speed_units) + ")";
            averageSpeedTitleWithUnits = getString(R.string.average_speed_title_text) + " (" + getString(R.string.imperial_speed_units) + ")";
        } else {
            distanceTitleWithUnits = getString(R.string.distance_title_text) + " (" + getString(R.string.metric_distance_units) + ")";
            currentSpeedTitleWithUnits = getString(R.string.current_speed_title_text) + " (" + getString(R.string.metric_speed_units) + ")";
            averageSpeedTitleWithUnits = getString(R.string.average_speed_title_text) + " (" + getString(R.string.metric_speed_units) + ")";
        }
        binding.distanceTitle.setText(distanceTitleWithUnits);
        binding.currentSpeedTitle.setText(currentSpeedTitleWithUnits);
        binding.averageSpeedTitle.setText(averageSpeedTitleWithUnits);
    }

    private void subscribeToServiceState() {
        RecordRideService.recordingStateLiveData.observe(this, state -> {
            if (state == null) return;

            binding.timerTextView.setText(UnitConversion.millisToClockString(state.durationMillis));

            if (distanceUnits == App.IMPERIAL_UNITS) {
                binding.rideDistanceTextView.setText(UnitConversion.getDistanceStringMiles(state.distanceMeters));
                if (state.isAutoPaused || state.isManuallyPaused) {
                    binding.currentSpeedValue.setText(UnitConversion.getSpeedStringMph(0));
                } else {
                    binding.currentSpeedValue.setText(UnitConversion.getSpeedStringMph(state.currentSpeedMps));
                }
                binding.averageSpeedValue.setText(UnitConversion.getSpeedStringMph(state.averageSpeedMps));
            } else {
                binding.rideDistanceTextView.setText(UnitConversion.getDistanceStringKilometers(state.distanceMeters));
                if (state.isAutoPaused || state.isManuallyPaused) {
                    binding.currentSpeedValue.setText(UnitConversion.getSpeedStringKph(0));
                } else {
                    binding.currentSpeedValue.setText(UnitConversion.getSpeedStringKph(state.currentSpeedMps));
                }
                binding.averageSpeedValue.setText(UnitConversion.getSpeedStringKph(state.averageSpeedMps));
            }

            if (state.isManuallyPaused) {
                binding.divider0.setVisibility(View.INVISIBLE);
                binding.autoPauseTextView.setVisibility(View.VISIBLE);
                binding.autoPauseTextView.setText(getString(R.string.manually_paused_text));
                binding.autoPauseTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.red));
            } else if (state.isAutoPaused) {
                binding.divider0.setVisibility(View.INVISIBLE);
                binding.autoPauseTextView.setVisibility(View.VISIBLE);
                binding.autoPauseTextView.setText(getString(R.string.autopaused_text));
                binding.autoPauseTextView.setBackgroundColor(purpleInt);
            } else {
                binding.divider0.setVisibility(View.VISIBLE);
                binding.autoPauseTextView.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        binding.autoPauseTextView.setVisibility(View.INVISIBLE);

        if (RecordRideService.isServiceCreated()) {
            Intent intent = new Intent(this, RecordRideService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isServiceBound = true;

            if (RecordRideService.getIsManuallyPaused()) {
                binding.autoPauseTextView.setVisibility(View.VISIBLE);
                binding.startResumeTextView.setText(R.string.resume_button);
                binding.stopFinishTextView.setText(R.string.finish_button);
                binding.startResumeTextView.setClickable(true);
                binding.stopFinishTextView.setClickable(true);

                binding.stopFinishTextView.post(() -> {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.stopFinishTextView.getLayoutParams();
                    binding.stopFinishTextView.setX(screenWidthPx - lp.rightMargin * (float) (4 / 3.0) - binding.stopFinishTextView.getWidth());
                });
                binding.startResumeTextView.post(() -> {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.startResumeTextView.getLayoutParams();
                    binding.startResumeTextView.setX(lp.leftMargin * (float) (4 / 3.0));
                });
            } else {
                binding.stopFinishTextView.bringToFront();
                binding.startResumeTextView.setText(R.string.resume_button);
                binding.stopFinishTextView.setClickable(true);
                if (RecordRideService.getIsAutoPaused()) {
                    binding.autoPauseTextView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            binding.startResumeTextView.post(() -> binding.startResumeTextView.setX(screenWidthPx / 2 - (float) binding.startResumeTextView.getWidth() / 2));
            binding.stopFinishTextView.post(() -> binding.stopFinishTextView.setX(screenWidthPx / 2 - (float) binding.stopFinishTextView.getWidth() / 2));
            binding.startResumeTextView.setText(R.string.start_button);
            binding.startResumeTextView.bringToFront();
            binding.stopFinishTextView.setClickable(false);

            binding.rideDistanceTextView.setText(getString(R.string.default_distance_text));
            binding.currentSpeedValue.setText(getString(R.string.no_ride_data_available));
            binding.averageSpeedValue.setText(getString(R.string.no_ride_data_available));
            binding.timerTextView.setText(getString(R.string.default_timer_text));

            handler.post(preRideRunnable);
            startLocationRequests();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            unbindService(connection);
            isServiceBound = false;
        }
        handler.removeCallbacks(preRideRunnable);
        stopLocationRequests();
        gpsFoundAnimationCounter = 0;
    }

    public void onStartResumeButtonClick(View view) {
        if (!isServiceBound && binding.startResumeTextView.getText().equals(getString(R.string.start_button))) {
            binding.acquiringGPSTextView.setVisibility(View.INVISIBLE);
            binding.gpsBackgroundColor.setVisibility(View.INVISIBLE);
            binding.dot1.setVisibility(View.INVISIBLE);
            binding.dot2.setVisibility(View.INVISIBLE);
            binding.dot3.setVisibility(View.INVISIBLE);

            stopLocationRequests();
            handler.removeCallbacks(preRideRunnable);

            startService();
            Intent intent = new Intent(this, RecordRideService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isServiceBound = true;

            binding.startResumeTextView.setClickable(false);
            binding.stopFinishTextView.setClickable(true);
            binding.stopFinishTextView.setText(R.string.stop_button);
            binding.stopFinishTextView.bringToFront();
        } else if (isServiceBound && service != null) {
            service.manualResume();
            binding.stopFinishTextView.bringToFront();
            binding.startResumeTextView.setClickable(false);

            binding.stopFinishTextView.setText(R.string.stop_button);
            animateButtonXMovement(binding.stopFinishTextView, screenWidthPx / 2 - (float) binding.stopFinishTextView.getWidth() / 2);
            animateButtonXMovement(binding.startResumeTextView, screenWidthPx / 2 - (float) binding.startResumeTextView.getWidth() / 2);
        }
    }

    public void onStopFinishButtonClick(View view) {
        if (!isServiceBound || service == null) return;

        if (binding.stopFinishTextView.getText().equals(getString(R.string.stop_button))) {
            service.manualPause();
            binding.stopFinishTextView.setText(R.string.finish_button);

            ViewGroup.MarginLayoutParams stopLp = (ViewGroup.MarginLayoutParams) binding.stopFinishTextView.getLayoutParams();
            animateButtonXMovement(binding.stopFinishTextView, screenWidthPx - stopLp.rightMargin * (float) (4 / 3.0) - binding.stopFinishTextView.getWidth());

            binding.startResumeTextView.setText(R.string.resume_button);
            binding.startResumeTextView.setClickable(true);
            ViewGroup.MarginLayoutParams startLp = (ViewGroup.MarginLayoutParams) binding.startResumeTextView.getLayoutParams();
            animateButtonXMovement(binding.startResumeTextView, startLp.leftMargin * (float) (4 / 3.0));
        } else {
            long rideId = service.saveRide();
            unbindAndEndService();
            Toast.makeText(this, "Ride saved", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, ViewRideActivity.class);
            intent.putExtra("ride_id", rideId);
            intent.putExtra(getString(R.string.enable_back_button), false);
            startActivity(intent);
            finish();
        }
    }

    public void startService() {
        Intent serviceIntent = new Intent(this, RecordRideService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public void endService() {
        Intent serviceIntent = new Intent(this, RecordRideService.class);
        stopService(serviceIntent);
    }

    private void unbindAndEndService() {
        endService();
        if (isServiceBound) {
            unbindService(connection);
            isServiceBound = false;
        }
    }

    private void animateButtonXMovement(View view, float value) {
        ObjectAnimator animation = ObjectAnimator.ofFloat(view, "x", value);
        animation.setDuration(400);
        animation.start();
    }

    private void enableLocationServices() {
        LocationRequest request = LocationRequest.create();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(request);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(RecordRideActivity.this, 42);
                } catch (IntentSender.SendIntentException sendEx) {
                    Toast.makeText(RecordRideActivity.this, "Failed to adjust location settings. Please navigate to settings app and turn on location.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void requestRequiredPermissions() {
        List<String> neededPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!neededPermissions.isEmpty()) {
            permissionLauncher.launch(neededPermissions.toArray(new String[0]));
        }
    }

    private void startLocationRequests() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isLocationBeingRequested = true;
        }
    }

    private void stopLocationRequests() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        isLocationBeingRequested = false;
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
