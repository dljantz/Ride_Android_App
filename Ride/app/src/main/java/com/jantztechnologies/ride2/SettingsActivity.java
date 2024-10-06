package com.jantztechnologies.ride2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setTitle(R.string.settings_title);
        toolbar.setTitleTextColor(getResources().getColor(R.color.black));
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true); // weird stuff to prevent null pointer exception
        getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);

        prepareAutopauseSetting();
        prepareKeepScreenOnSetting();
        prepareMapTypeSetting();
        prepareDistanceUnitsSetting();

        TextView aboutActivityButton = findViewById(R.id.aboutTextView);
        aboutActivityButton.setOnClickListener(this::startAboutActivity);
    }

    // Two tasks:
    //      set UI to display correct setting from file
    //      set click listener to allow user to set and save new setting to file.
    private void prepareAutopauseSetting() {
        SwitchCompat autopauseSwitch = findViewById(R.id.autopauseSwitch);
        int isAutopauseEnabled = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.autopause_setting_filename));
        switch (isAutopauseEnabled) {
            case App.AUTOPAUSE_ENABLED: autopauseSwitch.setChecked(true);
                break;
            case App.AUTOPAUSE_DISABLED: autopauseSwitch.setChecked(false);
                break;
            default: System.out.println("Error: autopause setting from file = " + isAutopauseEnabled);
        }

        autopauseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // save state to file... again, I'm using ints as booleans here so we can have a third state (-1) as an error flag
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.autopause_setting_filename), App.AUTOPAUSE_ENABLED);
            } else {
                // save the other state to file
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.autopause_setting_filename), App.AUTOPAUSE_DISABLED);
            }
        });
    }

    // Two tasks:
    //      set UI to display correct setting from file
    //      set click listener to allow user to set and save new setting to file.
    private void prepareKeepScreenOnSetting() {
        SwitchCompat keepScreenOnSwitch = findViewById(R.id.keepScreenOnSwitch);
        int isKeepScreenOnEnabled = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.keep_screen_on_setting_filename));
        switch (isKeepScreenOnEnabled) {
            case App.KEEP_SCREEN_ON_DURING_RIDES_ENABLED: keepScreenOnSwitch.setChecked(true);
                break;
            case App.KEEP_SCREEN_ON_DURING_RIDES_DISABLED: keepScreenOnSwitch.setChecked(false);
                break;
            default: System.out.println("Error: autopause setting from file = " + isKeepScreenOnEnabled);
        }

        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // save state to file... again, I'm using ints as booleans here so we can have a third state (-1) as an error flag
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.keep_screen_on_setting_filename), App.KEEP_SCREEN_ON_DURING_RIDES_ENABLED);
            } else {
                // save the other state to file
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.keep_screen_on_setting_filename), App.KEEP_SCREEN_ON_DURING_RIDES_DISABLED);
            }
        });
    }

    // Two tasks:
    //      set UI to display correct setting from file
    //      set click listener to allow user to set and save new setting to file.
    private void prepareMapTypeSetting() {
        RadioGroup mapTypeRadioGroup = findViewById(R.id.mapTypeRadioGroup);
        int mapTypeSetting = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.map_type_setting_filename));

        switch (mapTypeSetting) {
            case App.MAP_STYLE_DARK: mapTypeRadioGroup.check(R.id.darkMapStyleRadioButton);
                break;
            case App.MAP_STYLE_LIGHT: mapTypeRadioGroup.check(R.id.lightMapStyleRadioButton);
                break;
            case App.MAP_STYLE_SATELLITE: mapTypeRadioGroup.check(R.id.satelliteMapStyleRadioButton);
                break;
            case App.MAP_STYLE_HYBRID: mapTypeRadioGroup.check(R.id.hybridMapStyleRadioButton);
                break;
            default: System.out.println("Error: map type setting from file = " + mapTypeSetting);
                break;
        }

        mapTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.darkMapStyleRadioButton) {
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.map_type_setting_filename), App.MAP_STYLE_DARK);
            } else if (checkedId == R.id.lightMapStyleRadioButton) {
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.map_type_setting_filename), App.MAP_STYLE_LIGHT);
            } else if (checkedId == R.id.satelliteMapStyleRadioButton) {
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.map_type_setting_filename), App.MAP_STYLE_SATELLITE);
            } else if (checkedId == R.id.hybridMapStyleRadioButton) {
            FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.map_type_setting_filename), App.MAP_STYLE_HYBRID);
        }
        });
    }

    // Two tasks:
    //      set UI to display correct setting from file
    //      set click listener to allow user to set and save new setting to file.
    private void prepareDistanceUnitsSetting() {
        RadioGroup distanceUnitsRadioGroup = findViewById(R.id.distanceUnitsRadioGroup);
        int distanceUnitsSetting = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename));

        switch (distanceUnitsSetting) {
            case App.IMPERIAL_UNITS: distanceUnitsRadioGroup.check(R.id.imperialUnitsRadioButton);
                break;
            case App.METRIC_UNITS: distanceUnitsRadioGroup.check(R.id.metricUnitsRadioButton);
                break;
            default: System.out.println("Error: distance units setting from file = " + distanceUnitsSetting);
                break;
        }

        distanceUnitsRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.imperialUnitsRadioButton) {
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename), App.IMPERIAL_UNITS);
            } else if (checkedId == R.id.metricUnitsRadioButton) {
                FileIO.saveInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename), App.METRIC_UNITS);
            }
        });
    }

    // called by onClickListener
    public void startAboutActivity(View view) {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
}