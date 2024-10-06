package com.jantztechnologies.ride2;

import java.io.Serializable;
import java.util.Calendar;

public class RideStats implements Serializable {

    public final long timestamp;
    public final double distanceMeters;
    public final double averageSpeedMps;
    public final double maxSpeedMps;
    private double maxElevationMeters;
    private double minElevationMeters;
    public final double durationMillis;
    private double elevationGainMeters;
    private double elevationLossMeters;

    public RideStats(long timestamp,
                     double distanceMeters,
                     double averageSpeed,
                     double maxSpeed,
                     double maxElevationMeters,
                     double minElevationMeters,
                     double durationMillis,
                     double elevationGainMeters,
                     double elevationLossMeters) {

        this.timestamp = timestamp;
        this.distanceMeters = distanceMeters;
        this.averageSpeedMps = averageSpeed;
        this.maxSpeedMps = maxSpeed;
        this.maxElevationMeters = maxElevationMeters;
        this.minElevationMeters = minElevationMeters;
        this.durationMillis = durationMillis;
        this.elevationGainMeters = elevationGainMeters;
        this.elevationLossMeters = elevationLossMeters;
    }

    public double getMaxElevationMeters() {
        return maxElevationMeters;
    }

    public double getMinElevationMeters() {
        return minElevationMeters;
    }

    public double getElevationGainMeters() {
        return elevationGainMeters;
    }

    protected double getElevationLossMeters() {
        return elevationLossMeters;
    }

    public void setMaxElevationMeters(double maxElevationMeters) {
        this.maxElevationMeters = maxElevationMeters;
    }

    public void setMinElevationMeters(double minElevationMeters) {
        this.minElevationMeters = minElevationMeters;
    }

    public void setElevationGainMeters(double elevationGainMeters) {
        this.elevationGainMeters = elevationGainMeters;
    }

    public void setElevationLossMeters(double elevationLossMeters) {
        this.elevationLossMeters = elevationLossMeters;
    }

    public String getTimeOfDayText() {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTimeInMillis(timestamp);
        } catch (NullPointerException e) {
            // ride2 doesn't have calendar info, just return empty string
            return "View Ride"; // default action bar title
        }

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String introString;
        if (hour >= 22 || hour <= 3) introString = "Night ride2";
        else if (hour <= 11) introString = "Morning ride2";
        else if (hour <= 17) introString = "Afternoon ride2";
        else introString = "Evening ride2";

        return introString;
        }

    public String getDateText(boolean includeWeekDay) {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTimeInMillis(timestamp);
        } catch (NullPointerException e) {
            // ride2 doesn't have calendar info, just return nothing for action bar subtitle
            return null;
        }

        // Day of week starts from ONE??? what the hell
        int dayInt = calendar.get(Calendar.DAY_OF_WEEK);
        String dayString;
        switch (dayInt) {
            case 1: dayString = "Sunday";
                break;
            case 2: dayString = "Monday";
                break;
            case 3: dayString = "Tuesday";
                break;
            case 4: dayString = "Wednesday";
                break;
            case 5: dayString = "Thursday";
                break;
            case 6: dayString = "Friday";
                break;
            case 7: dayString = "Saturday";
                break;
            default: dayString = "   ";
                break;
        }

        // Month starts from ZERO. They couldn't #$%!@ing keep it consistent...
        int monthInt = calendar.get(Calendar.MONTH);
        String monthString;
        switch (monthInt) {
            case 0: monthString = "January";
                break;
            case 1: monthString = "February";
                break;
            case 2: monthString = "March";
                break;
            case 3: monthString = "April";
                break;
            case 4: monthString = "May";
                break;
            case 5: monthString = "June";
                break;
            case 6: monthString = "July";
                break;
            case 7: monthString = "August";
                break;
            case 8: monthString = "September";
                break;
            case 9: monthString = "October";
                break;
            case 10: monthString = "November";
                break;
            case 11: monthString = "December";
                break;
            default: monthString = "   ";
                break;
        }

        if (includeWeekDay) {
            return dayString + ", " + monthString + " " + calendar.get(Calendar.DAY_OF_MONTH) + ", " + calendar.get(Calendar.YEAR);
        } else {
            return monthString + " " + calendar.get(Calendar.DAY_OF_MONTH) + ", " + calendar.get(Calendar.YEAR);
        }
    }
}
