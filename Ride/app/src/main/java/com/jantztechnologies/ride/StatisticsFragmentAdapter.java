package com.jantztechnologies.ride;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;

public class StatisticsFragmentAdapter extends FragmentStateAdapter {

    ArrayList<RideStats> rideStatsArrayList;
    int distanceUnits;

    public StatisticsFragmentAdapter(@NonNull FragmentManager fragmentManager,
                                     @NonNull Lifecycle lifecycle,
                                     ArrayList<RideStats> rideStatsArrayList,
                                     int distanceUnits) {
        super(fragmentManager, lifecycle);
        this.rideStatsArrayList = rideStatsArrayList;
        this.distanceUnits = distanceUnits;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // calls to static newInstance method in fragment class generate a new
        //      instance of the desired fragment, but also bundles "constructor"
        //      parameter(s) to survive fragment destruction / recreation.
        switch (position) {
            case 1: return StatisticsFragment.newInstance(30, getRideStatsArrayListInsideNumDays(30), distanceUnits);
            case 2: return StatisticsFragment.newInstance(365, getRideStatsArrayListInsideNumDays(365), distanceUnits);
            case 3: return StatisticsFragment.newInstance(1000000, getRideStatsArrayListInsideNumDays(1000000), distanceUnits);
            default: return StatisticsFragment.newInstance(7, getRideStatsArrayListInsideNumDays(7), distanceUnits);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    // iterate backwards in rideStatsArrayList (newest to oldest), adding only the ones within
    //      the desired time range to a new arraylist.
    private ArrayList<RideStats> getRideStatsArrayListInsideNumDays(int numDays) {
        // the whole thing is in a try catch block to avoid app crash if files could not be
        //      accessed for some reason
        try {
            // quick little check for full time range to avoid doing unnecessary work below
            if (numDays > 365) return rideStatsArrayList;

            ArrayList<RideStats> truncatedRideStatsArrayList = new ArrayList<>();
            for (int i = rideStatsArrayList.size() - 1; i >= 0; i--) {
                double daysAgo = (System.currentTimeMillis() - rideStatsArrayList.get(i).timestamp) / 1000.0 / 60.0 / 60.0 / 24.0;
                if (daysAgo > numDays) {
                    return truncatedRideStatsArrayList; // no need to check further as list is ordered chronologically
                }
                truncatedRideStatsArrayList.add(0, rideStatsArrayList.get(i)); // have to keep the list in the same
                //      order as the original or distance record stats don't work -- the daysPrevious variable ends up negative,
                //      so there's never any cutoff capping sample range to 7 or 30 days.
            }
            return truncatedRideStatsArrayList;
        } catch (NullPointerException e) {
            return null;
        }
    }
}
