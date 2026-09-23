package com.jantztechnologies.ride2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.jantztechnologies.ride2.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PreferencesManager preferencesManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable removeTitleRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferencesManager = PreferencesManager.getInstance(this);

        // Check if we should display the intro title screen (every 18 hours)
        long lastLaunchTimestamp = preferencesManager.getLastLaunchTimestamp();
        long eighteenHours = 1000L * 3600 * 18;
        if (lastLaunchTimestamp == -1 || System.currentTimeMillis() - lastLaunchTimestamp > eighteenHours) {
            animateTitleScreen();
        }
        preferencesManager.setLastLaunchTimestamp(System.currentTimeMillis());

        binding.toolbarMain.setTitle(R.string.past_rides_title);
        binding.toolbarMain.setTitleTextColor(getResources().getColor(R.color.black, getTheme()));
        setSupportActionBar(binding.toolbarMain);

        binding.statisticsIcon.setOnClickListener(this::startStatisticsActivity);
        binding.settingsIcon.setOnClickListener(this::startSettingsActivity);
        binding.riderIconImageView.setOnClickListener(this::startRecordRideActivity);

        binding.savedRidesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.savedRidesRecyclerView.setHasFixedSize(true);

        // Observe Room database for rides in real time
        AppDatabase.getInstance(this).rideDao().getAllRides().observe(this, rides -> {
            SavedRidesListAdapter adapter = new SavedRidesListAdapter(MainActivity.this, rides);
            binding.savedRidesRecyclerView.setAdapter(adapter);
        });
    }

    public void startStatisticsActivity(View view) {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    public void startSettingsActivity(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void startRecordRideActivity(View view) {
        Intent intent = new Intent(this, RecordRideActivity.class);
        startActivity(intent);
    }

    private void animateTitleScreen() {
        binding.titleScreenGradient.setVisibility(View.VISIBLE);
        binding.logoImageView.setVisibility(View.VISIBLE);
        binding.titleAppNameImageView.setVisibility(View.VISIBLE);
        binding.companyNameTextView.setVisibility(View.VISIBLE);
        binding.tapToContinueTextView.setVisibility(View.VISIBLE);

        binding.titleScreenGradient.setOnClickListener(v -> removeTitleScreen());

        removeTitleRunnable = this::removeTitleScreen;
        handler.postDelayed(removeTitleRunnable, 4000);
    }

    private void removeTitleScreen() {
        if (removeTitleRunnable != null) {
            handler.removeCallbacks(removeTitleRunnable);
        }
        binding.logoImageView.setVisibility(View.INVISIBLE);
        binding.titleAppNameImageView.setVisibility(View.INVISIBLE);
        binding.companyNameTextView.setVisibility(View.INVISIBLE);
        binding.tapToContinueTextView.setVisibility(View.INVISIBLE);
        binding.titleScreenGradient.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (removeTitleRunnable != null) {
            handler.removeCallbacks(removeTitleRunnable);
        }
    }
}