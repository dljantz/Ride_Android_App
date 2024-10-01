package com.jantztechnologies.ride;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class StatisticsActivity extends AppCompatActivity {

    private TabLayout tabs;
    private ViewPager2 viewPager2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // gradient toolbar with proper title and background
        Toolbar toolbar = findViewById(R.id.toolbar_statistics);
        toolbar.setTitle(R.string.statistics_title);
        toolbar.setTitleTextColor(getResources().getColor(R.color.black));
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true); // weird stuff to prevent null pointer exception
        getSupportActionBar().setHomeAsUpIndicator(R.drawable._back_arrow);

        // set up tabs, viewpager, all that stuff... done according to
        //      https://www.youtube.com/watch?v=5-RWOvJ9oq8
        tabs = findViewById(R.id.statistics_tab_layout);
        viewPager2 = findViewById(R.id.statistics_view_pager_2);

        // set up the fragment adapter, which then creates the fragments
        int distanceUnits = FileIO.loadInt(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.distance_units_setting_filename));
        FragmentManager fragmentManager = getSupportFragmentManager();
        StatisticsFragmentAdapter adapter = new StatisticsFragmentAdapter(fragmentManager,
                                                                          getLifecycle(),
                                                                          getOrderedRideStatsArrayList(),
                                                                          distanceUnits);
        viewPager2.setAdapter(adapter);

        tabs.addTab(tabs.newTab().setText(R.string.tab_text_week));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_month));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_year));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text_full));

        tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.purple));
        tabs.setTabTextColors(getResources().getColor(R.color.light_gray), getResources().getColor(R.color.purple));

        // turn off that stupid tooltip text thing
        for (int i=0; i<tabs.getTabCount(); i++) {
            TooltipCompat.setTooltipText(Objects.requireNonNull(tabs.getTabAt(i)).view, null);
        }

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager2.setCurrentItem(tab.getPosition(), true);
                // this switch / case block fucks with ripple animation on select, so I just removed the ripple
                //      in the xml file
                switch (tab.getPosition()) {
                    case 0: tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.purple));
                            tabs.setTabTextColors(getResources().getColor(R.color.light_gray), getResources().getColor(R.color.purple));
                            break;
                    case 1: tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.purplish_pink));
                            tabs.setTabTextColors(getResources().getColor(R.color.light_gray), getResources().getColor(R.color.purplish_pink));
                            break;
                    case 2: tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.reddish_pink));
                            tabs.setTabTextColors(getResources().getColor(R.color.light_gray), getResources().getColor(R.color.reddish_pink));
                            break;
                    case 3: tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.red));
                            tabs.setTabTextColors(getResources().getColor(R.color.light_gray), getResources().getColor(R.color.red));
                            break;
                }

                // I don't know how else to turn off the tooltips that appear on tab long clicks. They constantly
                //      reset themselves after any tab relayout, so I have to constantly keep turning them
                //      off again.
                for (int i=0; i<tabs.getTabCount(); i++) {
                    TooltipCompat.setTooltipText(Objects.requireNonNull(tabs.getTabAt(i)).view, null);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        // make sure the selected tab is the one corresponding to the current page.
        //      this is needed because it's possible to change pages without selecting
        //      a tab -- simply by swiping sideways.
        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position); // youtube tutorial eliminated this but I don't
                //      see why it hurts to leave it here
                tabs.selectTab(tabs.getTabAt(position)); // this DOES result in a call to onTabSelected
                //      by onTabSelectedListener
            }
        });
    }

    // makes the arrayList with the proper rideStats objects from within this time range.
    private ArrayList<RideStats> getOrderedRideStatsArrayList() {

        Context context = App.getAppContext();
        File appDirectory = context.getFilesDir();

        // First, make a starting list containing ALL app files
        String[] allAppFiles = appDirectory.list();

        // Next, place only the ride folders from that list in a new array. Hopefully the null pointer exception is never thrown...
        ArrayList<String> rideFoldersArrayList = new ArrayList<>();
        try {
            for (String file : allAppFiles) {
                if (file.startsWith(getResources().getString(R.string.ride_folder_prefix))) {
                    rideFoldersArrayList.add(file);
                }
            }
        } catch (NullPointerException e) {
            return null; // if a null pointer exception was thrown, we can't access app files for some reason
        }

        // Then, make an Array out of the new arrayList of only ride folders. I didn't want to do this step, because
        //      who cares, but using the built in ArrayList sort method requires API level 24 or higher for some reason
        String[] rideFoldersArray = new String[rideFoldersArrayList.size()];
        rideFoldersArrayList.toArray(rideFoldersArray);

        // Now do the actual sorting based on the ending integers in the file names.
        Arrays.sort(rideFoldersArray, (o1, o2) -> {
            int o1Suffix = FileIO.getRideFolderNumber(o1);
            int o2Suffix = FileIO.getRideFolderNumber(o2);
            return Integer.compare(o1Suffix, o2Suffix);
        });

        // Now I can pull rideStats objects from file and put them in a new ArrayList.
        ArrayList<RideStats> rideStatsArrayList = new ArrayList<>();
        for (String rideFolderName : rideFoldersArray) {
            rideStatsArrayList.add(FileIO.loadRideStats(context, rideFolderName, getResources().getString(R.string.ride_stats_filename)));
        }

        return rideStatsArrayList;
    }
}