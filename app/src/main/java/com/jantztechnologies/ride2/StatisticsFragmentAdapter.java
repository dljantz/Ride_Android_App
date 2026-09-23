package com.jantztechnologies.ride2;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatisticsFragmentAdapter extends FragmentStateAdapter {

    private final int distanceUnits;

    public StatisticsFragmentAdapter(@NonNull FragmentManager fragmentManager,
                                     @NonNull Lifecycle lifecycle,
                                     int distanceUnits) {
        super(fragmentManager, lifecycle);
        this.distanceUnits = distanceUnits;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return StatisticsFragment.newInstance(30, distanceUnits);
            case 2:
                return StatisticsFragment.newInstance(365, distanceUnits);
            case 3:
                return StatisticsFragment.newInstance(1000000, distanceUnits);
            default:
                return StatisticsFragment.newInstance(7, distanceUnits);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
