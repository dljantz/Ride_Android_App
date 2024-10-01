package com.jantztechnologies.ride;

import android.content.Context;
import android.graphics.Color;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.util.ArrayList;

// Reusable POJO class for various map creations that have to happen at various points
public class MapIllustrator {

    public final Context context;
    public final int mapWidthPx;
    public final int mapHeightPx;
    public final String rideFolder; // may not need this one
    private final GoogleMap googleMap;
    private LatLngBounds latLngBounds;
    private ArrayList<PolylineOptions> allLocationsPolylines;
    private ArrayList<PolylineOptions> acceptedLocationsPolylines;
    private final ArrayList<SerializableLocation> acceptedLocations;
    private final ArrayList<Integer> manualPauseIndicesAcceptedLocations; // lists indices of every location accepted right before the user hit pause
    private final ArrayList<SerializableLocation> allLocations;
    private final ArrayList<Integer> manualPauseIndicesAllLocations; // indices of every very last location, accepted or not, before user hit pause
    private final RideStats rideStats;
    private CameraUpdate recenter;

    public MapIllustrator(Context context,
                          int mapWidthPx,
                          int mapHeightPx,
                          GoogleMap googleMap,
                          String rideFolder,
                          ArrayList<Integer> manualPauseIndicesAllLocations,
                          ArrayList<Integer> manualPauseIndicesAcceptedLocations,
                          ArrayList<SerializableLocation> allLocations,
                          ArrayList<SerializableLocation> acceptedLocations,
                          RideStats rideStats) {

        this.context = context;
        this.mapWidthPx = mapWidthPx;
        this.mapHeightPx = mapHeightPx;
        this.googleMap = googleMap;
        this.rideFolder = rideFolder;
        this.manualPauseIndicesAllLocations = manualPauseIndicesAllLocations;
        this.manualPauseIndicesAcceptedLocations = manualPauseIndicesAcceptedLocations;
        this.allLocations = allLocations;
        this.acceptedLocations = acceptedLocations;
        this.rideStats = rideStats;
    }

    //Manipulates the map once available, triggered by OnMapReadyCallback interface
    public void setMapStyle() {

        int preferredMapType = FileIO.loadInt(context, context.getResources().getString(R.string.settings_folder_name), context.getResources().getString(R.string.map_type_setting_filename));

        if (preferredMapType == App.MAP_STYLE_LIGHT) {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_light));
        } else if (preferredMapType == App.MAP_STYLE_SATELLITE) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (preferredMapType == App.MAP_STYLE_HYBRID) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else { // default to dark in the case of file i/o error
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark));
            System.out.println("map styled");
        }
    }

    public void setMapSettings(boolean zoomControlsEnabled) {
        UiSettings uiSettings = googleMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(zoomControlsEnabled);
        uiSettings.setIndoorLevelPickerEnabled(false); // no need for airport navigation
        uiSettings.setMapToolbarEnabled(false); // disallow marker clicks from opening Maps app
        uiSettings.setTiltGesturesEnabled(false);
    }

    // finds coordinate bounds to tell the camera where to focus
    public void establishCameraBounds(double zoomOut) {
        double minLat;
        double minLong;
        double maxLat;
        double maxLong;

        try {
            minLat = acceptedLocations.get(0).latitude;
            minLong = acceptedLocations.get(0).longitude;
            maxLat = minLat;
            maxLong = minLong;
        } catch (IndexOutOfBoundsException | NullPointerException e) { // finished so fast arrayList of locations is empty OR allLocations can't be pulled from file for some reason
            // zoom out to the entire world if no ride data exists
            minLat = -25;
            minLong = -130;
            maxLat = 55;
            maxLong = 60;
            LatLngBounds cameraBounds = new LatLngBounds(new LatLng(minLat, minLong), new LatLng(maxLat, maxLong));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds, mapWidthPx, mapHeightPx, 0));
            return;
        }

        double latRange; // used to expand camera bounds so parts of the ride aren't right on the edge
        double longRange;

        for (SerializableLocation location : acceptedLocations) {
            if (location.latitude < minLat) minLat = location.latitude;
            else if (location.latitude > maxLat) maxLat = location.latitude;
            if (location.longitude < minLong) minLong = location.longitude;
            else if (location.longitude > maxLong) maxLong = location.longitude;
        }

        // extremely rare edge case -- path crossed 180 degrees longitude.
        //      determining westernmost / easternmost points gets a bit weird
        if (Math.abs(maxLong - minLong) > 270) { // no way to achieve this kind of distance without crossing 180 degrees longitude
            minLong = 180; // flip them around so min is always farther west
            maxLong = -180;
            // redo for loop with different logic
            for (SerializableLocation location : acceptedLocations) {
                if (location.longitude > 0 && location.longitude < minLong) minLong = location.longitude;
                else if (location.longitude < 0 && location.longitude > maxLong) maxLong = location.longitude;
            }
            longRange = (180 - Math.abs(maxLong)) + (180 - minLong);
        } else { // longitude wasn't weird, so expand bounds the normal way
            longRange = maxLong - minLong;
        }

        latRange = maxLat - minLat;
        maxLat += latRange * zoomOut;
        minLat -= latRange * zoomOut;

        maxLong += longRange * zoomOut;
        minLong -= longRange * zoomOut;

        LatLngBounds cameraBounds = new LatLngBounds(new LatLng(minLat, minLong), new LatLng(maxLat, maxLong)); // SW then NE bounds
        recenter = CameraUpdateFactory.newLatLngBounds(cameraBounds, mapWidthPx, mapHeightPx, 0);
        googleMap.moveCamera(recenter);
    }

    public void animateCameraRecenter() {
        try { googleMap.animateCamera(recenter);
        } catch (NullPointerException ignored) { } // occurs on ultra-short rides that have no latlng bounds because acceptedLocations has no locations.
    }

    // NOTE: If I ever need to troubleshoot location values by tracking allLocations and acceptedLocations
    //      simultaneously, go back to version_7 or before for the version of this method that includes
    //      a zIndex parameter to get them layered and drawn right. I deleted everything from this method in
    //      version_8 and beyond that pertains to allLocations polylines for reading clarity.
    public void generatePolylineOptions(int numSpeedColorCategories) {
        // The try / catch block below creates a gradient along the route path -- purple segments are
        //      slower, red segments are faster. It was originally creating new polylines for every single
        //      segment... but it turns out Google Maps objects don't accommodate thousands of polylines
        //      very well :(. Instead, the code below divides it up into 6 speed categories that each
        //      get a different color, which drops the number of polylines by a factor of 8 or so.

        PolylineOptions polyline = new PolylineOptions(); // only initializing here because android studio complained... could have just declared the variable
        ArrayList<PolylineOptions> polylines = new ArrayList<>();
        int minColorInt = 144; // equal to hex value coded in color resource file
        int maxColorInt = 255; // same as above
        int gradientRangeInt = maxColorInt - minColorInt; // just syntactic sugar
        double currentColorGradientMultiplier = 0.0; // only initialized because Android Studio complained, default value should never be used
        boolean pauseEventDetected = false;

        try {
            // There were two options -- draw the proper speed color ahead or behind the point where
            //      the speed was measured. I chose the more anticipatory one (drawn behind) as it's more
            //      conducive to accurate representation of speed post-smoothing algorithm, which I could
            //      choose to implement rather than raw speed in the future.
            for (int i = 1; i < acceptedLocations.size(); i++) {
                double nextColorGradientMultiplier = getSpeedColorGradientMultiplier(acceptedLocations.get(i), numSpeedColorCategories);

                if (manualPauseIndicesAcceptedLocations.contains(i - 1)) {
                    pauseEventDetected = true; // notify the next iteration of the for loop to create a new polyline
                }
                // if it's right after a pause event OR this is the first location in the list OR the raw
                //      speed of this location object falls outside of the quartile / quintile / whatever
                //      of the last location, make a new polylineOptions object and add this location as the
                //      first point. Also add this polyline to the ArrayList of polylines.
                else if (i == 1 ||
                    pauseEventDetected ||
                    nextColorGradientMultiplier != currentColorGradientMultiplier) {

                    currentColorGradientMultiplier = nextColorGradientMultiplier; // prep for next speed category comparison
                    pauseEventDetected = false; // switch off pause event flag so we don't get stuck generating new polylines needlessly
                    polyline = new PolylineOptions();
                    polyline.startCap(new ButtCap());
                    polyline.endCap(new RoundCap());
                    polyline.jointType(JointType.ROUND);
                    polyline.width(mapHeightPx / (float) 80.0); // make this depend on a dp value instead of a px value so the density of the screen has no impact on width
                    int adjustmentFromPurple = (int) Math.round(gradientRangeInt * currentColorGradientMultiplier); // purple is the default color
                    polyline.color(Color.argb(255,
                            minColorInt + adjustmentFromPurple,
                            96, // hardcoded to match color resource file green hex value
                            maxColorInt - adjustmentFromPurple));
                    polyline.add(new LatLng(acceptedLocations.get(i - 1).latitude, acceptedLocations.get(i - 1).longitude)); // previous point
                    polyline.add(new LatLng(acceptedLocations.get(i).latitude, acceptedLocations.get(i).longitude)); // this point
                    polylines.add(polyline);
                } else {
                    // not the first point, nor is it in the same speed category as the previous point, nor is it
                    // immediately following a pause event. so just add it to the current polyline and that's it.
                    polyline.add(new LatLng(acceptedLocations.get(i).latitude, acceptedLocations.get(i).longitude)); // this point
                }
            }
        } catch (NullPointerException ignored) {
            // expected result if ride length of zero, just pretend it never happened and move on in life
        }

/*

temporarily(?) disabling this to try to get route line gradient working

        try {
            for (int i = 0; i < locations.size(); i++) {
                polyline.add(new LatLng(locations.get(i).latitude, locations.get(i).longitude));
                if (pauseIndices.contains(i)) {
                    polylines.add(polyline);
                    polyline = new PolylineOptions(); // point variable at a new object
                    // then reset all the attributes
                    polyline.color(color);
                    polyline.jointType(JointType.ROUND);
                    polyline.endCap(new RoundCap());
                    polyline.width(mapHeightPx / (float) 100.0); // make this depend on a dp value instead of a px value so the density of the screen has no impact on width
                }
            }
        } catch (NullPointerException ignored) {
            // couldn't get locations arraylist from file,
            //      so we will return an empty arraylist of polylines
        }
        */

        acceptedLocationsPolylines = polylines;
    }

    // calculates the "speed category" of any given location's speed to determine if it needs to be
    //      added to preexisting polyline or start a new polyline of different color. There are a few
    //      interesting mathematical shenanigans in here to spit out a value that can be plugged straight
    //      into polyline color assignment -- if you have 5 categories, you don't want the color multipliers
    //      to be 0.1, 0.3, 0.5, 0.7, 0.9 because using those midpoints chops off a full 20% of possible
    //      gradient range. Instead you want 0.0, 0.25, 0.5, 0.75, 1.0 for better contrast.
    private double getSpeedColorGradientMultiplier(SerializableLocation l, int numSpeedCategories) {
        double percentOfMaxSpeed = l.getRawSpeedMps() / rideStats.maxSpeedMps; // is possible that raw speed exceeds maxSpeedMps, shouldn't be a problem as it will just be colored red
        double colorMultiplier = 1.0; // set the default speed. If the repeating if statements below
        //      don't find the right category, the only one left is the maximum speed category.
        // for loop below doesn't need to test for below 0% or beyond 80% -- below zero is the same color as
        //      below 20%, and beyond 80% is the default value
        for (int i = 1; i < numSpeedCategories; i++) {
            if (percentOfMaxSpeed < (double) i / numSpeedCategories) {
                colorMultiplier = (double) (i - 1) / (numSpeedCategories - 1); // see method description -- this decreases the denominator of the fraction to achieve that goal of higher contrast polylines.
                return colorMultiplier; // break the for loop, correct speed category is found
            }
        }
        return colorMultiplier; // functions as an else statement -- category never found, must be in top speed category
    }

    public void drawPolylines() {
        ///////////////for (PolylineOptions polyline : allLocationsPolylines) googleMap.addPolyline(polyline);
        for (PolylineOptions polyline : acceptedLocationsPolylines) googleMap.addPolyline(polyline);
        // todo: delete print statement below
        System.out.println("The number of polylines is: " + acceptedLocationsPolylines.size());
    }
}
