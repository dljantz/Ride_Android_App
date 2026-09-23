package com.jantztechnologies.ride2;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.jantztechnologies.ride2.databinding.ActivityStatisticsBinding;

import java.util.Objects;

public class StatisticsActivity extends AppCompatActivity {

    private ActivityStatisticsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStatisticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        binding.toolbarStatistics.setTitle(R.string.statistics_title);
        binding.toolbarStatistics.setTitleTextColor(ContextCompat.getColor(this, R.color.black));
        setSupportActionBar(binding.toolbarStatistics);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);
        }
        binding.toolbarStatistics.setNavigationOnClickListener(v -> finish());

        // ViewPager2 & Adapter
        int distanceUnits = PreferencesManager.getInstance(this).getDistanceUnits();
        StatisticsFragmentAdapter adapter = new StatisticsFragmentAdapter(
                getSupportFragmentManager(),
                getLifecycle(),
                distanceUnits
        );
        binding.statisticsViewPager2.setAdapter(adapter);

        // Tabs
        TabLayout tabs = binding.statisticsTabLayout;
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_week));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_month));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_year));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_full));

        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.purple));
        tabs.setTabTextColors(ContextCompat.getColor(this, R.color.light_gray), ContextCompat.getColor(this, R.color.purple));

        // Disable tab tooltips
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null) {
                TooltipCompat.setTooltipText(tab.view, null);
            }
        }

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.statisticsViewPager2.setCurrentItem(tab.getPosition(), true);
                switch (tab.getPosition()) {
                    case 0:
                        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(StatisticsActivity.this, R.color.purple));
                        tabs.setTabTextColors(ContextCompat.getColor(StatisticsActivity.this, R.color.light_gray), ContextCompat.getColor(StatisticsActivity.this, R.color.purple));
                        break;
                    case 1:
                        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(StatisticsActivity.this, R.color.purplish_pink));
                        tabs.setTabTextColors(ContextCompat.getColor(StatisticsActivity.this, R.color.light_gray), ContextCompat.getColor(StatisticsActivity.this, R.color.purplish_pink));
                        break;
                    case 2:
                        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(StatisticsActivity.this, R.color.reddish_pink));
                        tabs.setTabTextColors(ContextCompat.getColor(StatisticsActivity.this, R.color.light_gray), ContextCompat.getColor(StatisticsActivity.this, R.color.reddish_pink));
                        break;
                    case 3:
                        tabs.setSelectedTabIndicatorColor(ContextCompat.getColor(StatisticsActivity.this, R.color.red));
                        tabs.setTabTextColors(ContextCompat.getColor(StatisticsActivity.this, R.color.light_gray), ContextCompat.getColor(StatisticsActivity.this, R.color.red));
                        break;
                }

                for (int i = 0; i < tabs.getTabCount(); i++) {
                    TabLayout.Tab t = tabs.getTabAt(i);
                    if (t != null) {
                        TooltipCompat.setTooltipText(t.view, null);
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.statisticsViewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                TabLayout.Tab tab = tabs.getTabAt(position);
                if (tab != null) {
                    tabs.selectTab(tab);
                }
            }
        });
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