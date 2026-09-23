package com.jantztechnologies.ride2;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.jantztechnologies.ride2.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAboutBinding binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarAbout.setTitle(R.string.about_title);
        binding.toolbarAbout.setTitleTextColor(getResources().getColor(R.color.black, getTheme()));
        setSupportActionBar(binding.toolbarAbout);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);
        }
    }
}