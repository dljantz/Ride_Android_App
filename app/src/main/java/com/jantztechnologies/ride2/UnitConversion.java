package com.jantztechnologies.ride2;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class UnitConversion {
    public static final double ROUND_DOWN_THRESHOLD_MPH = 0;
    public static final double AUTOPAUSE_THRESHOLD_MPS = UnitConversion.mphToMps(ROUND_DOWN_THRESHOLD_MPH);

    public static double mpsToKph(double mps) {
        return mps / 1000.0 * 3600.0;
    }

    public static double metersToKilometers(double meters) {
        return meters / 1000.0;
    }

    public static double metersToMiles(double meters) {
        return meters / 1609.344;
    }

    public static double metersToFeet(double meters) {
        return meters * 3.28084;
    }

    public static double mphToMps(double mph) {
        return mph * 1609.344 / 3600.0;
    }

    public static double mpsToMph(double mps) {
        return mps * 3600.0 / 1609.344;
    }

    @NotNull
    public static String millisToClockString(double milliseconds) {
        int seconds = (int) milliseconds / 1000;
        int hours = seconds / 3600;
        seconds = seconds % 3600;
        int minutes = seconds / 60;
        seconds = seconds % 60;

        if (hours == 0) {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    public static double millisToDays(double millis) {
        return millis / (1000.0 * 60 * 60 * 24);
    }

    public static String getDistanceStringKilometers(double meters) {
        double km = Math.round(meters / 10.0) / 100.0;
        return String.format(Locale.getDefault(), "%.2f", km);
    }

    public static String getSpeedStringKph(double speedMps) {
        double kph = mpsToKph(speedMps);
        if (kph < ROUND_DOWN_THRESHOLD_MPH) kph = 0;
        return String.format(Locale.getDefault(), "%.1f", kph);
    }

    public static String getDistanceStringMiles(double meters) {
        double miles = metersToMiles(meters);
        return String.format(Locale.getDefault(), "%.2f", miles);
    }

    public static String getSpeedStringMph(double speedMps) {
        double mph = UnitConversion.mpsToMph(speedMps);
        if (mph < ROUND_DOWN_THRESHOLD_MPH) mph = 0;
        return String.format(Locale.getDefault(), "%.1f", mph);
    }

    public static String getElevationStringMeters(double meters) {
        return String.valueOf(Math.round(meters));
    }

    public static String getElevationStringFeet(double meters) {
        return String.valueOf(Math.round(metersToFeet(meters)));
    }

    public static String getTimeOfDayText(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour >= 22 || hour <= 3) return "Night Ride";
        else if (hour <= 11) return "Morning Ride";
        else if (hour <= 17) return "Afternoon Ride";
        else return "Evening Ride";
    }

    public static String getDateText(long timestamp, boolean includeWeekDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        SimpleDateFormat sdf = includeWeekDay ?
                new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) :
                new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }
}
