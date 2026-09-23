package com.jantztechnologies.ride2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.jantztechnologies.ride2.databinding.ActivitySettingsBinding;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferencesManager = PreferencesManager.getInstance(this);

        binding.toolbarSettings.setTitle(R.string.settings_title);
        binding.toolbarSettings.setTitleTextColor(getResources().getColor(R.color.black, getTheme()));
        setSupportActionBar(binding.toolbarSettings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);
        }

        prepareAutopauseSetting();
        prepareKeepScreenOnSetting();
        prepareMapTypeSetting();
        prepareDistanceUnitsSetting();

        binding.aboutTextView.setOnClickListener(this::startAboutActivity);
    }

    private void prepareAutopauseSetting() {
        binding.autopauseSwitch.setChecked(preferencesManager.isAutopauseEnabled());
        binding.autopauseSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferencesManager.setAutopauseEnabled(isChecked));
    }

    private void prepareKeepScreenOnSetting() {
        binding.keepScreenOnSwitch.setChecked(preferencesManager.isKeepScreenOnEnabled());
        binding.keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferencesManager.setKeepScreenOnEnabled(isChecked));
    }

    private void prepareMapTypeSetting() {
        int mapTypeSetting = preferencesManager.getMapStyle();
        switch (mapTypeSetting) {
            case App.MAP_STYLE_LIGHT:
                binding.mapTypeRadioGroup.check(R.id.lightMapStyleRadioButton);
                break;
            case App.MAP_STYLE_SATELLITE:
                binding.mapTypeRadioGroup.check(R.id.satelliteMapStyleRadioButton);
                break;
            case App.MAP_STYLE_HYBRID:
                binding.mapTypeRadioGroup.check(R.id.hybridMapStyleRadioButton);
                break;
            case App.MAP_STYLE_DARK:
            default:
                binding.mapTypeRadioGroup.check(R.id.darkMapStyleRadioButton);
                break;
        }

        binding.mapTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.darkMapStyleRadioButton) {
                preferencesManager.setMapStyle(App.MAP_STYLE_DARK);
            } else if (checkedId == R.id.lightMapStyleRadioButton) {
                preferencesManager.setMapStyle(App.MAP_STYLE_LIGHT);
            } else if (checkedId == R.id.satelliteMapStyleRadioButton) {
                preferencesManager.setMapStyle(App.MAP_STYLE_SATELLITE);
            } else if (checkedId == R.id.hybridMapStyleRadioButton) {
                preferencesManager.setMapStyle(App.MAP_STYLE_HYBRID);
            }
        });
    }

    private void prepareDistanceUnitsSetting() {
        int distanceUnitsSetting = preferencesManager.getDistanceUnits();
        if (distanceUnitsSetting == App.METRIC_UNITS) {
            binding.distanceUnitsRadioGroup.check(R.id.metricUnitsRadioButton);
        } else {
            binding.distanceUnitsRadioGroup.check(R.id.imperialUnitsRadioButton);
        }

        binding.distanceUnitsRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.imperialUnitsRadioButton) {
                preferencesManager.setDistanceUnits(App.IMPERIAL_UNITS);
            } else if (checkedId == R.id.metricUnitsRadioButton) {
                preferencesManager.setDistanceUnits(App.METRIC_UNITS);
            }
        });
    }

    public void startAboutActivity(View view) {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
}