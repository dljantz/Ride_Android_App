package com.jantztechnologies.ride;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;

public class StatisticsFragment extends Fragment {

    // fragment initialization parameter keys
    private static final String STATS_RANGE_NUM_DAYS = "statsRangeDays";
    private static final String RIDE_STATS_ARRAYLIST = "rideStatsArrayList";
    private static final String DISTANCE_UNITS = "distance_units";

    private int dayRange;
    private ArrayList<RideStats> rideStatsArrayList;
    private int graphColor; // same as tab color
    private int middleLineColor; // The color of the middle separator lines
    private int gradient; // gradient from one side of the screen to the other for horizontal separator lines

    // all the dividing lines between stats textviews
    private View horizontalLine1;
    private View horizontalLine2;
    private View horizontalLine3;
    private View horizontalLine4;
    private View verticalLine1;
    private View verticalLine2;
    private View verticalLine3;
    private View verticalLine4;
    private View verticalLine5;

    // Views not addressed above that need to be removed from shorter time range fragments
    private TextView distanceRecordValue30Days;
    private TextView distanceRecordTitle30Days;
    private TextView distanceRecordValue7Days;
    private TextView distanceRecordTitle7Days;
    private View bottomBlackRectangle;

    // TextViews not addressed above that need to be assigned values
    private TextView numberOfRidesValue;
    private TextView totalDistanceValue;
    private TextView totalTimeValue;
    private TextView averageRideDistanceValue;
    private TextView averageRideDurationValue;
    private TextView averageRideSpeedValue;
    private TextView fastestRideValue;
    private TextView longestRideValue;

    private int distanceUnits;

    // fragments are weird, bro. I am required to have a no-argument constructor
    //      bc the system might decide to destroy / recreate this fragment, such
    //      as on device rotation. So all data / parameters determining behavior
    //      of the fragment must be inputted elsewhere, in a Bundle. This is performed
    //      in static method newInstance below.
    public StatisticsFragment() {
        // Required empty public constructor
    }

    public static StatisticsFragment newInstance(int numDays, ArrayList<RideStats> rideStatsArrayList, int distanceUnits) {
        StatisticsFragment fragment = new StatisticsFragment();
        Bundle args = new Bundle();
        args.putInt(STATS_RANGE_NUM_DAYS, numDays);
        args.putSerializable(RIDE_STATS_ARRAYLIST, rideStatsArrayList);
        args.putInt(DISTANCE_UNITS, distanceUnits);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set up all the colors and whatnot
        if (getArguments() != null) {
            dayRange = getArguments().getInt(STATS_RANGE_NUM_DAYS);
            rideStatsArrayList = (ArrayList<RideStats>) getArguments().getSerializable(RIDE_STATS_ARRAYLIST);
            distanceUnits = getArguments().getInt(DISTANCE_UNITS);
            defineColorValues();
        }
    }

    // this method is important to inflate layout for the fragment. So although it says it's
    //      creating a view, in reality it's creating all views in the fragment
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_statistics, container, false);

        // assign all the views to variables to be manipulated
        assignViewVariables(v);

        // get all the colors and gradients set up
        setViewColors(v);

        // if it's a fragment for a shorter time span, remove a couple of statistics and their supporting
        //      views from the page.
        removeUnnecessaryViews();

        // assign statistics values and their appropriate units to textviews
        setViewValues();

        createCumulativeDistanceGraph(v);

        return v;
    }

    // different fragments to have different color schemes to create gradient effect
    //      as you swipe across the four tabs.
    private void defineColorValues() {
        switch (dayRange) {
            case 30:
                graphColor = getResources().getColor(R.color.purplish_pink);
                middleLineColor = getResources().getColor(R.color.purple_three_eighths);
                gradient = R.drawable._pinkish_purple_to_pink_gradient;
                break;
            case 365:
                graphColor = getResources().getColor(R.color.reddish_pink);
                middleLineColor = getResources().getColor(R.color.red_three_eighths);
                gradient = R.drawable._pink_to_pinkish_red_gradient;
                break;
            case 1000000:
                graphColor = getResources().getColor(R.color.red);
                middleLineColor = getResources().getColor(R.color.red_one_eighth);
                gradient = R.drawable._pinkish_red_to_red_gradient;
                break;
            default:
                graphColor = getResources().getColor(R.color.purple);
                middleLineColor = getResources().getColor(R.color.purple_one_eighth);
                gradient = R.drawable._purple_to_pinkish_purple_gradient;
        }
    }

    private void assignViewVariables(View v) {
        // Views to have gradients adjusted
        horizontalLine1 = v.findViewById(R.id.gradientLine1);
        horizontalLine2 = v.findViewById(R.id.gradientLine2);
        horizontalLine3 = v.findViewById(R.id.gradientLine3);
        horizontalLine4 = v.findViewById(R.id.gradientLine4);
        verticalLine1 = v.findViewById(R.id.verticalLine1);
        verticalLine2 = v.findViewById(R.id.verticalLine2);
        verticalLine3 = v.findViewById(R.id.verticalLine3);
        verticalLine4 = v.findViewById(R.id.verticalLine4);
        verticalLine5 = v.findViewById(R.id.verticalLine5);

        // assign values to views I'll make invisible / gone later.
        distanceRecordValue30Days = v.findViewById(R.id.thirtyDayDistanceRecordValueTextView);
        distanceRecordTitle30Days = v.findViewById(R.id.thirtyDayDistanceRecordTitleTextView);
        distanceRecordValue7Days = v.findViewById(R.id.sevenDayDistanceRecordValueTextView);
        distanceRecordTitle7Days = v.findViewById(R.id.sevenDayDistanceRecordTitleTextView);
        bottomBlackRectangle = v.findViewById(R.id.bottomBlackRectangle );

        // assign values to remaining TextViews that will be assigned values
        numberOfRidesValue = v.findViewById(R.id.numberOfRidesValueTextView);
        totalDistanceValue = v.findViewById(R.id.totalDistanceValueTextView);
        totalTimeValue = v.findViewById(R.id.totalRideTimeValueTextView);
        averageRideDistanceValue = v.findViewById(R.id.averageRideDistanceValueTextView);
        averageRideDurationValue = v.findViewById(R.id.averageRideDurationValueTextView);
        averageRideSpeedValue = v.findViewById(R.id.averageRideSpeedValueTextView);
        fastestRideValue = v.findViewById(R.id.fastestRideValueTextView);
        longestRideValue = v.findViewById(R.id.longestRideValueTextView);
    }

    // Used set all the dividing lines and whatnot to make a gradient across 4 tabs.
    private void setViewColors(View v) {

        // previously had this stuff set up with gradients, but I decided a single consistent
        //      color per tab looked better.

        ////////////////horizontalLine1.setBackgroundResource(gradient);
        /////////////////horizontalLine2.setBackgroundResource(gradient);
        //////////////////////horizontalLine3.setBackgroundResource(gradient);
        //////////////////////////////horizontalLine4.setBackgroundResource(gradient);

        horizontalLine1.setBackgroundColor(graphColor);
        horizontalLine2.setBackgroundColor(graphColor);
        horizontalLine3.setBackgroundColor(graphColor);
        horizontalLine4.setBackgroundColor(graphColor);

        //////////////////verticalLine1.setBackgroundColor(middleLineColor);
        ///////////////////verticalLine2.setBackgroundColor(middleLineColor);
        //////////////////verticalLine3.setBackgroundColor(middleLineColor);
        /////////////////verticalLine4.setBackgroundColor(middleLineColor);
        ///////////////////verticalLine5.setBackgroundColor(middleLineColor);

        verticalLine1.setBackgroundColor(graphColor);
        verticalLine2.setBackgroundColor(graphColor);
        verticalLine3.setBackgroundColor(graphColor);
        verticalLine4.setBackgroundColor(graphColor);
        verticalLine5.setBackgroundColor(graphColor);
    }

    private void removeUnnecessaryViews() {
        if (dayRange < 365) {
            horizontalLine4.setVisibility(View.INVISIBLE); // made this one invisible bc one little dinky vertical line relies on it for positioning in xml
            distanceRecordValue30Days.setVisibility(View.GONE);
            distanceRecordTitle30Days.setVisibility(View.GONE);
            distanceRecordValue7Days.setVisibility(View.GONE);
            distanceRecordTitle7Days.setVisibility(View.GONE);
            bottomBlackRectangle.setVisibility(View.GONE);
        }
    }

    // first calculate the values, then set them to TextViews
    private void setViewValues() {

        // STEP 1: initialize variables with values to prevent app crashing in case of
        //      null RideStats ArrayList
        int numRides = 0;
        double totalDistanceMeters = 0;
        double totalTimeMillis = 0;
        double averageRideDistanceMeters = 0;
        double averageRideDurationMillis = 0;
        double averageRideSpeedMps = 0;
        double fastestRideMps = 0;
        double longestRideMeters = 0;
        double sevenDayDistanceRecordMeters = 0;
        double thirtyDayDistanceRecordMeters = 0;


        // STEP 2: one loop to rule them all... finds distance, time, speed, everything
        for (int i = 0; i < rideStatsArrayList.size(); i++) {
            RideStats ride = rideStatsArrayList.get(i); // a pinch of syntactic sugar
            numRides++;
            totalDistanceMeters += ride.distanceMeters;
            totalTimeMillis += ride.durationMillis;
            if (ride.averageSpeedMps > fastestRideMps) fastestRideMps = ride.averageSpeedMps;
            if (ride.distanceMeters > longestRideMeters) longestRideMeters = ride.distanceMeters;

            // backwards iterating loop to find the 7 and 30 day distance traveled for this
            //      particular ride. After the for loop, check to see if it's higher than the
            //      last one and update distance record variables if so.
            double distanceLast7DaysMeters = 0;
            double distanceLast30DaysMeters = 0;
            for (int j = i; j >= 0; j--) {
                RideStats pastRide = rideStatsArrayList.get(j); // some more syntactic sugar
                double daysPrevious = (ride.timestamp - pastRide.timestamp) / (1000.0 * 60.0 * 60.0 * 24.0);

                if (daysPrevious < 7) {
                    distanceLast7DaysMeters += pastRide.distanceMeters;
                }
                if (daysPrevious < 30) {
                    distanceLast30DaysMeters += pastRide.distanceMeters;
                } else {
                    break; // no need to keep going, our work here is done
                }
            }
            if (distanceLast7DaysMeters > sevenDayDistanceRecordMeters) sevenDayDistanceRecordMeters = distanceLast7DaysMeters;
            if (distanceLast30DaysMeters > thirtyDayDistanceRecordMeters) thirtyDayDistanceRecordMeters = distanceLast30DaysMeters;
        }
        averageRideDistanceMeters = totalDistanceMeters / (double) numRides; // casting to double to prevent divide by int issues
        averageRideDurationMillis = totalTimeMillis / (double) numRides;
        averageRideSpeedMps = totalDistanceMeters / totalTimeMillis * 1000;

        ///////////////System.out.println("days: " + dayRange + rideStatsArrayList);

        // STEP 3: convert to appropriate units and make String objects
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


        // STEP 4: assign to textViews
        numberOfRidesValue.setText(numRidesString);
        totalDistanceValue.setText(totalDistanceString);
        totalTimeValue.setText(totalTimeString);
        averageRideDistanceValue.setText(averageRideDistanceString);
        averageRideDurationValue.setText(averageRideDurationString);
        averageRideSpeedValue.setText(averageRideSpeedString);
        fastestRideValue.setText(fastestRideString);
        longestRideValue.setText(longestRideString);
        distanceRecordValue7Days.setText(sevenDayDistanceRecordString);
        distanceRecordValue30Days.setText(thirtyDayDistanceRecordString);
    }

    private void createCumulativeDistanceGraph(View v) {
        GraphView cumulativeDistanceGraph = v.findViewById(R.id.cumulativeDistanceGraph);
        // find the max Y axis value for graphing real quick
        double totalCumulativeDistanceMeters = 0;
        for (RideStats ride: rideStatsArrayList) {
            totalCumulativeDistanceMeters += ride.distanceMeters;
        }

        // find the correct millisecond value real quick... if the time range is full (1000000 days),
        //      don't do the conversion because that's dumb. Just make the millisecond range go back
        //      far enough to include the first ride.
        double dayRangeInMilliseconds;
        if (dayRange > 365) {
            // edge case: full history graph generation crashes the app when no rides are recorded yet
            //      because we're trying to access a RideStats object from an empty ArrayList.
            try {
                dayRangeInMilliseconds = System.currentTimeMillis() - rideStatsArrayList.get(0).timestamp;
            } catch (IndexOutOfBoundsException i) {
                dayRangeInMilliseconds = 24 * 60 * 60 * 1000; // If no data, default to 24 hours for full history graph x axis
            }
        } else {
            dayRangeInMilliseconds = (double) dayRange * 24 * 60 * 60 * 1000;
        }

        cumulativeDistanceGraph.redraw(
                dayRangeInMilliseconds, // put it in with default units (milliseconds)
                totalCumulativeDistanceMeters,
                0,
                null,
                rideStatsArrayList,
                distanceUnits,
                graphColor,
                false);
    }
}