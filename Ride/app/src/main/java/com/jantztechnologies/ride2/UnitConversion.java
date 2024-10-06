package com.jantztechnologies.ride2;

import org.jetbrains.annotations.NotNull;

public class UnitConversion {
    public static final double ROUND_DOWN_THRESHOLD_MPH = 0;
    public static final double AUTOPAUSE_THRESHOLD_MPS = UnitConversion.mphToMps(ROUND_DOWN_THRESHOLD_MPH);

    public static double mpsToKph(double mps) {
        return mps / 1000 * 3600;
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
        return mph * 1609.344 / 3600;
    }

    public static double mpsToMph(double mps) {
        return mps * 3600 / 1609.344;
    }

    // converts millisecond time value into string in digital clock format
    @NotNull
    public static String millisToClockString(double milliseconds) {
        int seconds = (int) milliseconds / 1000; // casting to int truncates
        int hours = seconds / 3600;
        seconds = seconds % 3600;
        int minutes = seconds / 60;
        seconds = seconds % 60;

        String minutesString = String.valueOf(minutes);
        String secondsString = String.valueOf(seconds);

        if (minutesString.length() == 1) minutesString = "0" + minutesString;
        if (secondsString.length() == 1) secondsString = "0" + secondsString;

        String timeString;
        if (hours == 0) {
            timeString = minutesString + ":" + secondsString;
        } else {
            timeString = hours + ":" + minutesString + ":" + secondsString;
        }

        return timeString;
    }

    public static double millisToDays(double millis) {
        return millis / (1000.0 * 60 * 60 * 24);
    }

    public static String getDistanceStringKilometers(double meters) {
        double km = Math.round(meters / 10) / 100.0;
        String kmString = String.valueOf(km);
        if (kmString.length() == 3) kmString = kmString + "0";
        return kmString;
    }

    public static String getSpeedStringKph(double speedMps) {
        double kph = mpsToKph(speedMps);
        if (kph < ROUND_DOWN_THRESHOLD_MPH) kph = 0;
        else kph = Math.round(kph * 10) / 10.0; // to the nearest tenth
        return String.valueOf(kph);
    }

    public static String getDistanceStringMiles(double meters) {
        double miles = metersToMiles(meters);
        miles = Math.round(miles * 100) / 100.0; // nearest hundredth
        String milesString = String.valueOf(miles);
        if (milesString.length() == 3) milesString = milesString + "0";
        return milesString;
    }

    public static String getSpeedStringMph(double speedMps) {
        double mph = UnitConversion.mpsToMph(speedMps);
        if (mph < ROUND_DOWN_THRESHOLD_MPH) mph = 0;
        else mph = Math.round(mph * 10) / 10.0; // to the nearest tenth
        return String.valueOf(mph);
    }

    // round to nearest whole number
    public static String getElevationStringMeters(double meters) {
        return String.valueOf(Math.round(meters));
    }

    // round to nearest whole number
    public static String getElevationStringFeet(double meters) {
        return String.valueOf(Math.round(metersToFeet(meters)));
    }
}
