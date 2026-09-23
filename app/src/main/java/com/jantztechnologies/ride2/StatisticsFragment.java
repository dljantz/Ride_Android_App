package com.jantztechnologies.ride2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.jantztechnologies.ride2.databinding.FragmentStatisticsBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {

    private static final String STATS_RANGE_NUM_DAYS = "statsRangeDays";
    private static final String DISTANCE_UNITS = "distance_units";

    private int dayRange;
    private int distanceUnits;
    private int graphColor;

    private FragmentStatisticsBinding binding;
    private ArrayList<RideStats> rideStatsArrayList = new ArrayList<>();

    public StatisticsFragment() {
    }

    public static StatisticsFragment newInstance(int numDays, int distanceUnits) {
        StatisticsFragment fragment = new StatisticsFragment();
        Bundle args = new Bundle();
        args.putInt(STATS_RANGE_NUM_DAYS, numDays);
        args.putInt(DISTANCE_UNITS, distanceUnits);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dayRange = getArguments().getInt(STATS_RANGE_NUM_DAYS);
            distanceUnits = getArguments().getInt(DISTANCE_UNITS);
            defineColorValues();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setViewColors();
        removeUnnecessaryViews();
        loadStatisticsData();
    }

    private void defineColorValues() {
        switch (dayRange) {
            case 30:
                graphColor = getResources().getColor(R.color.purplish_pink, requireActivity().getTheme());
                break;
            case 365:
                graphColor = getResources().getColor(R.color.reddish_pink, requireActivity().getTheme());
                break;
            case 1000000:
                graphColor = getResources().getColor(R.color.red, requireActivity().getTheme());
                break;
            default:
                graphColor = getResources().getColor(R.color.purple, requireActivity().getTheme());
        }
    }

    private void setViewColors() {
        binding.gradientLine1.setBackgroundColor(graphColor);
        binding.gradientLine2.setBackgroundColor(graphColor);
        binding.gradientLine3.setBackgroundColor(graphColor);
        binding.gradientLine4.setBackgroundColor(graphColor);

        binding.verticalLine1.setBackgroundColor(graphColor);
        binding.verticalLine2.setBackgroundColor(graphColor);
        binding.verticalLine3.setBackgroundColor(graphColor);
        binding.verticalLine4.setBackgroundColor(graphColor);
        binding.verticalLine5.setBackgroundColor(graphColor);
    }

    private void removeUnnecessaryViews() {
        if (dayRange < 365) {
            binding.gradientLine4.setVisibility(View.INVISIBLE);
            binding.thirtyDayDistanceRecordValueTextView.setVisibility(View.GONE);
            binding.thirtyDayDistanceRecordTitleTextView.setVisibility(View.GONE);
            binding.sevenDayDistanceRecordValueTextView.setVisibility(View.GONE);
            binding.sevenDayDistanceRecordTitleTextView.setVisibility(View.GONE);
            binding.bottomBlackRectangle.setVisibility(View.GONE);
        }
    }

    private void loadStatisticsData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long sinceTimestamp = 0;
            if (dayRange <= 365) {
                sinceTimestamp = System.currentTimeMillis() - ((long) dayRange * 24L * 3600L * 1000L);
            }
            List<RideEntity> entities = AppDatabase.getInstance(requireContext())
                    .rideDao().getRidesSince(sinceTimestamp);

            ArrayList<RideStats> statsList = new ArrayList<>();
            for (RideEntity entity : entities) {
                statsList.add(new RideStats(
                        entity.timestamp,
                        entity.distanceMeters,
                        entity.averageSpeedMps,
                        entity.maxSpeedMps,
                        entity.maxElevationMeters,
                        entity.minElevationMeters,
                        entity.durationMillis,
                        entity.elevationGainMeters,
                        entity.elevationLossMeters
                ));
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded()) {
                    rideStatsArrayList = statsList;
                    setViewValues();
                    createCumulativeDistanceGraph();
                }
            });
        });
    }

    private void setViewValues() {
        int numRides = 0;
        double totalDistanceMeters = 0;
        double totalTimeMillis = 0;
        double fastestRideMps = 0;
        double longestRideMeters = 0;
        double sevenDayDistanceRecordMeters = 0;
        double thirtyDayDistanceRecordMeters = 0;

        for (int i = 0; i < rideStatsArrayList.size(); i++) {
            RideStats ride = rideStatsArrayList.get(i);
            numRides++;
            totalDistanceMeters += ride.distanceMeters;
            totalTimeMillis += ride.durationMillis;
            if (ride.averageSpeedMps > fastestRideMps) fastestRideMps = ride.averageSpeedMps;
            if (ride.distanceMeters > longestRideMeters) longestRideMeters = ride.distanceMeters;

            double distanceLast7DaysMeters = 0;
            double distanceLast30DaysMeters = 0;
            for (int j = i; j >= 0; j--) {
                RideStats pastRide = rideStatsArrayList.get(j);
                double daysPrevious = (ride.timestamp - pastRide.timestamp) / (1000.0 * 60.0 * 60.0 * 24.0);

                if (daysPrevious < 7) {
                    distanceLast7DaysMeters += pastRide.distanceMeters;
                }
                if (daysPrevious < 30) {
                    distanceLast30DaysMeters += pastRide.distanceMeters;
                } else {
                    break;
                }
            }
            if (distanceLast7DaysMeters > sevenDayDistanceRecordMeters) sevenDayDistanceRecordMeters = distanceLast7DaysMeters;
            if (distanceLast30DaysMeters > thirtyDayDistanceRecordMeters) thirtyDayDistanceRecordMeters = distanceLast30DaysMeters;
        }

        double averageRideDistanceMeters = numRides > 0 ? totalDistanceMeters / numRides : 0;
        double averageRideDurationMillis = numRides > 0 ? totalTimeMillis / numRides : 0;
        double averageRideSpeedMps = totalTimeMillis > 0 ? (totalDistanceMeters / totalTimeMillis * 1000) : 0;

        String numRidesString = String.valueOf(numRides);
        String totalTimeString = UnitConversion.millisToClockString(totalTimeMillis);
        String averageRideDurationString = UnitConversion.millisToClockString(averageRideDurationMillis);

        String averageRideDistanceString;
        String totalDistanceString;
        String averageRideSpeedString;
        String fastestRideString;
        String longestRideString;
        String sevenDayDistanceRecordString;
        String thirtyDayDistanceRecordString;

        if (distanceUnits == App.IMPERIAL_UNITS) {
            totalDistanceString = UnitConversion.getDistanceStringMiles(totalDistanceMeters) + " mi";
            averageRideDistanceString = UnitConversion.getDistanceStringMiles(averageRideDistanceMeters) + " mi";
            averageRideSpeedString = UnitConversion.getSpeedStringMph(averageRideSpeedMps) + " mph";
            fastestRideString = UnitConversion.getSpeedStringMph(fastestRideMps) + " mph avg";
            longestRideString = UnitConversion.getDistanceStringMiles(longestRideMeters) + " mi";
            sevenDayDistanceRecordString = UnitConversion.getDistanceStringMiles(sevenDayDistanceRecordMeters) + " mi";
            thirtyDayDistanceRecordString = UnitConversion.getDistanceStringMiles(thirtyDayDistanceRecordMeters) + " mi";
        } else {
            totalDistanceString = UnitConversion.getDistanceStringKilometers(totalDistanceMeters) + " km";
            averageRideDistanceString = UnitConversion.getDistanceStringKilometers(averageRideDistanceMeters) + " km";
            averageRideSpeedString = UnitConversion.getSpeedStringKph(averageRideSpeedMps) + " kph";
            fastestRideString = UnitConversion.getSpeedStringKph(fastestRideMps) + " kph avg";
            longestRideString = UnitConversion.getDistanceStringKilometers(longestRideMeters) + " km";
            sevenDayDistanceRecordString = UnitConversion.getDistanceStringKilometers(sevenDayDistanceRecordMeters) + " km";
            thirtyDayDistanceRecordString = UnitConversion.getDistanceStringKilometers(thirtyDayDistanceRecordMeters) + " km";
        }

        binding.numberOfRidesValueTextView.setText(numRidesString);
        binding.totalDistanceValueTextView.setText(totalDistanceString);
        binding.totalRideTimeValueTextView.setText(totalTimeString);
        binding.averageRideDistanceValueTextView.setText(averageRideDistanceString);
        binding.averageRideDurationValueTextView.setText(averageRideDurationString);
        binding.averageRideSpeedValueTextView.setText(averageRideSpeedString);
        binding.fastestRideValueTextView.setText(fastestRideString);
        binding.longestRideValueTextView.setText(longestRideString);
        binding.sevenDayDistanceRecordValueTextView.setText(sevenDayDistanceRecordString);
        binding.thirtyDayDistanceRecordValueTextView.setText(thirtyDayDistanceRecordString);
    }

    private void createCumulativeDistanceGraph() {
        double totalCumulativeDistanceMeters = 0;
        for (RideStats ride : rideStatsArrayList) {
            totalCumulativeDistanceMeters += ride.distanceMeters;
        }

        double dayRangeInMilliseconds;
        if (dayRange > 365) {
            if (!rideStatsArrayList.isEmpty()) {
                dayRangeInMilliseconds = System.currentTimeMillis() - rideStatsArrayList.get(0).timestamp;
            } else {
                dayRangeInMilliseconds = 24 * 60 * 60 * 1000;
            }
        } else {
            dayRangeInMilliseconds = (double) dayRange * 24 * 60 * 60 * 1000;
        }

        binding.cumulativeDistanceGraph.redraw(
                dayRangeInMilliseconds,
                totalCumulativeDistanceMeters,
                0,
                null,
                rideStatsArrayList,
                distanceUnits,
                graphColor,
                false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}