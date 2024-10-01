package com.jantztechnologies.ride;

import android.location.Location;
import android.os.Build;

import java.io.Serializable;
import java.util.ArrayList;

// Locations HAD to be designed parcelable instead of serializable... so here we are
//      with a shitty "wrapper" class that doesn't actually wrap the Location objects, just
//      copies over the data
public class SerializableLocation implements Serializable {

    // variables are final so they can be safely accessed publicly I think
    public final double latitude;
    public final double longitude;
    private final double satelliteElevation;
    private double networkElevationMeters;
    private boolean isElevationDefining; // useful in ViewRideActivity to catch up on data we're missing... don't have to do all those calculations again to space locations out by 20 meters or whatever
    /////////////////private double smoothedElevation;
    public final double accuracyMeters;
    public double verticalAccuracyMeters;
    private double rawSpeedMps;
    private double smoothedSpeedMps;
    private double distanceFromStartMeters;
    public final long elapsedRealTimeNanos;
    public final double distanceToEarthCenterMeters;
    private double distanceToEarthAxisMeters;
    public final double[] cartesianCoordsMeters;
    // Below: used to find angles between location objects on 2d plane tangent to earth's surface to
    //      improve the location acceptance algorithm -- smaller angles (closer to continuing in
    //      straight line) require distance to be a smaller fraction of confidence radius away to
    //      be accepted.
    private final double metersPerDegreeLongitudeAtThisLatitude;
    private final double metersPerDegreeLatitudeAtThisLatitude;
    private double angle;
    private boolean wasManuallyPausedRightAfterThisLocation; // 5/22/2022 -- should be one of three values -- normal, paused here, or resumed here.
    //      ultimately to replace pauseIndices arraylist? That whole situation is ridiculous.
    private boolean wasManuallyResumedRightBeforeThisLocation;

    public SerializableLocation(Location l) {
        latitude = l.getLatitude();
        longitude = l.getLongitude();
        satelliteElevation = l.getAltitude();
        networkElevationMeters = App.NO_ELEVATION_DATA;
        isElevationDefining = false;
        accuracyMeters = l.getAccuracy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            verticalAccuracyMeters = l.getVerticalAccuracyMeters();
        else verticalAccuracyMeters = 0;
        rawSpeedMps = 0; // not using speed value from location object bc I want to calculate it myself from acceptedLocations.
                      //      this is because a location's speed from google can exceed maxSpeed from the route, leading to
                      //      a spike that goes off the graph entirely. Not good.
        elapsedRealTimeNanos = l.getElapsedRealtimeNanos();
        distanceToEarthCenterMeters = findDistanceToEarthCenter();
        // Now we have 3 geographic coordinates for each point -- latitude, longitude, radius (from earth's center)
        //     next, use trig to convert each point into 3-dimensional coordinates.
        cartesianCoordsMeters = find3DCartesianCoordinates(); // this also finds distanceToEarthAxisMeters
        metersPerDegreeLongitudeAtThisLatitude = findMetersPerDegreeLongitudeAtThisLatitude();
        metersPerDegreeLatitudeAtThisLatitude = findMetersPerDegreeLatitudeAtThisLatitude();
        angle = App.UNRELIABLE_ANGLE; // just a safe default value
        wasManuallyPausedRightAfterThisLocation = false; // default is obviously to assume it's a normal location
        wasManuallyResumedRightBeforeThisLocation = false;
    }

    // used to test if points are within each other's radii of 68% certainty. If not, this value
    //      will be added onto the running total ride distance.
    public double distanceTo(SerializableLocation pastLocation) {
        // Use 3-dimensional version of pythagorean theorem to calculate
        //      distance between the two points.
        double x = cartesianCoordsMeters[0] - pastLocation.cartesianCoordsMeters[0];
        double y = cartesianCoordsMeters[1] - pastLocation.cartesianCoordsMeters[1];
        double z = cartesianCoordsMeters[2] - pastLocation.cartesianCoordsMeters[2];
        return Math.sqrt(x*x + y*y + z*z);
    }

    // altitude value returned by location.getAltitude() is height above / below WGS 84 reference ellipsoid.
    // I need distance from earth's center to have true 3D geographic coordinates, so... here we are
    private double findDistanceToEarthCenter() {
        // Equatorial axial radius of WGS 84 reference ellipsoid, in meters
        double E = 6378137.0;
        // Polar axial radius of WGS 84 reference ellipsoid, in meters
        double P = 6356752.314245; // source for both: https://en.wikipedia.org/wiki/World_Geodetic_System#WGS84
        // convert latitude to radians for math stuff. geocentric angle from earth's center, I THINK...
        //      wikipedia says WGS 84 is geocentric, anyway. So if google engineers give literally
        //      any fucks about consistency, then they should have ensured that Location latitude values
        //      adhere to that standard as altitude values do and it should not be geodetic.
        double theta = latitude / 180 * Math.PI;
        // x is short for equatorial (horizontal) distance from earth center, in meters.
        //      (following spheroid axis assignment conventions)
        // The derivation of this equation is floating around on some notebook paper somewhere,
        //      it's based on googled equation for ellipses, which conveniently generalizes to
        //      rotational ellipsoids since they're basically the same thing
        // Possible edge case where Math.tan(theta) returns infinity:
        //      you are exactly at the north or south pole. I am ignoring it because I assume this will never happen.
        double x = Math.sqrt(((E * E) * (P * P)) / ((P * P) + (E * E) * Math.pow(Math.tan(theta), 2)));
        // z is short for polar (vertical) distance from earth center, in meters
        //      (following spheroid axis assignment conventions)
        double z = x * Math.tan(theta);
        // reference ellipsoid radius at device location + altitude
        /////////////////////////////////////////double referenceEllipsoidRadius = Math.sqrt((x*x) + (z*z));
        //////////////////////////////////////////return referenceEllipsoidRadius + location.getAltitude();
        return Math.sqrt((x * x) + (z * z)); // excluding altitude for now, analysis with 2D autodesk drawing says it's more accurate given a few hopefully true assumptions
        // TO DO: Do I trust altitude values? for now, turned off in distance calculations... every location is assumed to be on the surface of WGS 84 ellipsoid
        //      Also, what if it's jumping between available and unavailable? then calculations could yield kilometer movements per second.
        //      to address, perhaps check for altitude changes faster or slower than like 10 m/s... if higher than that it's invalid or somthing.
        //      For later APIs I can implement logic to decide whether to accept new altitude values based on getVerticalAccuracyMeters and similar
        //      confirmation / rejection logic as implemented for horizontal movement.
    }

    // convert from geographic to cartesian 3d coordinates.
    private double[] find3DCartesianCoordinates() {
        // convert degrees to radians
        double latitudeRadians = latitude / 180 * Math.PI;
        double longitudeRadians = longitude / 180 * Math.PI;
        // calculations from perspective of side view slice of the earth
        double z = distanceToEarthCenterMeters * Math.sin(latitudeRadians);
        distanceToEarthAxisMeters = distanceToEarthCenterMeters * Math.cos(latitudeRadians);
        // calculations from perspective of top view slice of the earth
        double x = distanceToEarthAxisMeters * Math.cos(longitudeRadians);
        double y = distanceToEarthAxisMeters * Math.sin(longitudeRadians);

        return new double[]{x, y, z};
    }

    private double findMetersPerDegreeLongitudeAtThisLatitude() {
        double earthCircumferenceAtThisLatitudeMeters = 2 * Math.PI * distanceToEarthAxisMeters;
        return earthCircumferenceAtThisLatitudeMeters / 360;
    }

    private double findMetersPerDegreeLatitudeAtThisLatitude() {
        // pretend the earth forms a perfect circle in the XZ plane
        //      with a radius equal to the distance to the center at
        //      this latitude.
        return (distanceToEarthCenterMeters * 2 * Math.PI / 360);
    }

    // attempting to use this to refine location acceptance algorithm. returns 0 for straight path,
    //      180 for complete direction reversal.
    public void calculateAngle(SerializableLocation start, SerializableLocation end) {
        // calculations from the perspective of a plane tangent to the WGS 84 ellipsoid touching at vertex.
        // start and end are technically a few tenths of a millimeter or something off the plane, since
        //      the earth curves away. But close enough.
        // all coordinates in this tangent plane are in meters, though that is omitted from variable names.

        double startX = (start.longitude - this.longitude) * metersPerDegreeLongitudeAtThisLatitude;
        ///////////System.out.println("StartX: " + startX);
        double startY = (start.latitude - this.latitude) * metersPerDegreeLatitudeAtThisLatitude;
        ////////////////System.out.println("StartY: " + startY);
        double endX = (end.longitude - this.longitude) * metersPerDegreeLongitudeAtThisLatitude;
        ///////////////System.out.println("EndX: " + endX);
        double endY = (end.latitude - this.latitude) * metersPerDegreeLatitudeAtThisLatitude;
        //////////////System.out.println("EndY: " + endY);

        double startPathDegrees = getAngleToOrigin(-startX, -startY);
        /////////////System.out.println("start path degrees: " + startPathDegrees);
        double endPathDegrees = getAngleToOrigin(endX, endY);
        /////////////System.out.println("end path degrees: " + endPathDegrees);
        // check for equivalency with vertex, return safety value if so (should trigger cautious
        //      location acceptance back in RecordRideService)
        if (startPathDegrees == App.UNRELIABLE_ANGLE || endPathDegrees == App.UNRELIABLE_ANGLE) {
            angle = App.UNRELIABLE_ANGLE;
            return;
        }

        // right turns should be negative, left turns positive following this calculation
        angle = endPathDegrees - startPathDegrees;
        // weird stuff when the angle crosses from 270 to -90 and vice versa... have to flip the sign
        //      and get the value less than 180 again. Can also think about this intuitively -- a 190
        //      degree left turn is better expressed as a -170 degree right turn (needs to flip to
        //      negative to communicate that it is now being thought of as a right turn)
        if (angle > 180) {
            angle = (360 - angle) * (-1); // flip to negative right angle
            ///////////System.out.println("flipped to right angle (negative)");
        } else if (angle < -180) {
            angle = 360 + angle; // flip to positive left angle
            //////////////System.out.println("flipped to left angle (positive)");
        }

        // now that flippage has happened, we can check to see if the angle is extreme.
        //      if it's more than 90 degrees to the right or left, just mark that shit
        //      as unreliable
        if (Math.abs(angle) > 90) {
            angle = App.UNRELIABLE_ANGLE;
            ////////////////System.out.println("large angle marked as unreliable");
        }
        /////////////////System.out.println("end of calculations for this point");
    }

    // gets angle between a point and the origin. Used by getAngleBetween. Note: to find the angle
    //      of the starting path, I negate startX and startY before feeding them in here. That
    //      essentially treats (startX, startY) as the origin and shifts the vertex the appropriate
    //      x and y distance away, flipping the angle 180 degrees from what it would have been if I
    //      hadn't negated the values. So, I'm always measuring angles FORWARD in time -- the oldest
    //      point always gets to be the origin.
    private double getAngleToOrigin(double pointX, double pointY) {
        double angleDegrees = App.UNRELIABLE_ANGLE; // default to safe assumption
        // atan can't tell whether we're on positive or negative x side of graph, so we have two cases
        // result can be positive or negative, from -90 to 90 degrees
        if (pointX > 0) {
            angleDegrees = Math.atan(pointY / pointX) * 180 / Math.PI;
        } else if (pointX < 0) {
            angleDegrees = Math.atan(pointY / pointX) * 180 / Math.PI + 180;
        } else if (pointY > 0) {
            // x must be equal to zero, avoid divide by zero error
            angleDegrees = 90;
        } else if (pointY < 0) {
            angleDegrees = 270;
        }
        // if we never executed any code in the if else block, start location must have exactly
        //      the same latitude and longitude as vertex. just return
        //      the safety default value of 361 to signal that accuracy radius
        //      should not be reduced

        // returned angle, if it was calculated, should fall in the range -90 to 270. A bit strange,
        //      but it's still a 360 degree range and doesn't require extra math
        return angleDegrees;
    }

    // Expands the time span and distance span used to calculate current speed. Time is the deciding
    //      factor determining how far back in the arraylist to reach to pull time / distance data in the
    //      speed calculation.
    public void smoothSpeed(ArrayList<SerializableLocation> locations,
                            ArrayList<Integer> manualPauseIndices,
                            int targetTimeSeconds, // the amount of time to aim for when reaching backwards for previous location values to smooth speed with.
                            int minTimeSeconds) { // the minimum denominator that's considered trustworthy -- speeds calculated with times less
                                                  //      than this are replaced by speeds divided by minTimeSeconds instead to bias their value
                                                  //      downward to avoid erroneous max speeds.

        // Below: used to keep from smoothing speed using data from before the last pause event, as that can cause
        //      some seriously weird stuff to happen... like if you pause and then drive somewhere fast, then resume,
        //      there's a big speed spike
        int lastManualPauseIndex = -1; // give it a default value in case there were no pauses this ride.
        if (!manualPauseIndices.isEmpty()) lastManualPauseIndex = manualPauseIndices.get(manualPauseIndices.size() - 1);

        double lastSegmentDistanceMeters = 0;

        // for loop iterates backwards, starting with the last non-current location in the list (this location
        //      object HAS been added to the list already)
        for (int i = locations.size() - 2; i >= 0; i--) { // very first time it's run, locations.size is so small i is set to -1. This does not cause an error, the for loop just doesn't run

            // if this location is right after a manual pause, just set its speed to zero and be done
            //      as there is no valid point prior to this one to smooth speed with.
            if (i == lastManualPauseIndex) {
                this.smoothedSpeedMps = 0;
                return;
            }

            double lastSegmentTimeSeconds = (this.elapsedRealTimeNanos - locations.get(i).elapsedRealTimeNanos) / 1000000000.0;
            // lastSegmentDistanceMeters should still reflect the value from the
            //      previous location, but lastSegmentTimeSeconds does not. So we recalculate the denominator
            //      based on the previous index in the variable defined here:
            double recalculatedLastSegmentTimeSeconds = (this.elapsedRealTimeNanos - locations.get(i + 1).elapsedRealTimeNanos) / 1000000000.0;

            // if duration of end path segment exceeds targetTimeSeconds, that's too much. In fact, directly following autopause
            //      or manual pause events, targetTimeSeconds may be greatly exceeded -- even by minutes. This results
            //      in a very dumb-looking speed graph -- the huge denominator biases the smoothed speed downwards
            //      like crazy. So this for loop is iterating through longer and longer possible path segments
            //      to end the ride until it finds one that took longer than targetTimeSeconds, but then immediately
            //      hops back to the PREVIOUS possible end path segment as that one is known to produce
            //      reasonable time values for the denominator in the speed calculating division.
            if (lastSegmentTimeSeconds > targetTimeSeconds &&
                recalculatedLastSegmentTimeSeconds != 0) {
                // second part of if statement above: also ensure we do not divide by zero, as that is very possible!!
                //      the recalculatedLastSegmentTimeSeconds variable can sometimes use the same location
                //      object twice, which results in a value of zero. not good.

                // if the time value in the denominator is too short, a simple distance over time
                //      calculation is not a trustworthy smoothed speed as it does not actually encompass
                //      a very large data range. We instead use an artificially large denominator
                //      to protect against speed spikes.
                if (recalculatedLastSegmentTimeSeconds < minTimeSeconds) {
                    this.smoothedSpeedMps = lastSegmentDistanceMeters / minTimeSeconds;
                }
                // on the other hand, if the time value is large enough, it is trusted so we just
                //      use that one in the denominator.
                else {
                    this.smoothedSpeedMps = lastSegmentDistanceMeters / recalculatedLastSegmentTimeSeconds;
                }
                return; // job is done, no need to waste more computational resources on this for loop
            }

            // we have not yet found a point greater than 15 seconds ago, so keep adding distance
            //      onto the running total to be used when we do find it.
            lastSegmentDistanceMeters += locations.get(i + 1).distanceTo(locations.get(i));

            // ensure that we actually assign a smoothed speed to points in the first 15 seconds.
            //      Those points will be protected against false speed spikes by the if statement
            //      inside this one. This if statement takes place AFTER we update
            //      lastSegmentDistanceMeters, and uses lastSegmentTimeSeconds RATHER than
            //      recalculatedLastSegmentTimeSeconds, because we are not "stepping past"
            //      targetTimeSeconds and then "stepping back" as in . Instead, we want to simply
            //      use the data pertaining to the location object referred to by i in this
            //      iteration.
            if (i == 0 || i == lastManualPauseIndex + 1) { // second part if statement: find the most recent manual pause "barrier" we shouldn't smooth speed across.
                if (lastSegmentTimeSeconds < minTimeSeconds) {
                    this.smoothedSpeedMps = lastSegmentDistanceMeters / minTimeSeconds;
                } else {
                    this.smoothedSpeedMps = lastSegmentDistanceMeters / lastSegmentTimeSeconds;
                }
                return;
            }
        }
    }
/*
    // my last attempt to use elevation data gleaned from satellite data... same idea / structure
    //      as smoothSpeed, but the factor that determines a location object's inclusion in the smoothing
    //      segment is determined by distance, not time (as distance is not a time-dependent property
    //      the way speed is).
    public void smoothElevation(ArrayList<SerializableLocation> locations) {
        // for loop iterates backwards, starting with the last non-current location in the list (this location
        //      object HAS been added to the list already)
        double lastSegmentDistanceMeters = 0;
        double lastSegmentElevationSum = 0;
        int numLocationsInLastSegment = 1;
        for (int i = locations.size() - 2; i >= 0; i--) {
            lastSegmentDistanceMeters += locations.get(i + 1).distanceTo(locations.get(i));
            lastSegmentElevationSum += locations.get(i + 1).satelliteElevation;
            numLocationsInLastSegment++;
            if (lastSegmentDistanceMeters > 400 || i == 0) { // take average of last quarter mile, seems like the absolute maximum useful
                //      range without just completely smoothing the whole ride
                this.smoothedElevation = lastSegmentElevationSum / numLocationsInLastSegment;
                return;
            }
        }
        // don't think I have to add something here to make sure a value is outputted, should be guaranteed
        //      given that or statement includes a check for i == 0, the end of the for loop
    }

 */

    public boolean getWasManuallyPausedRightAfterThisLocation() {
        return wasManuallyPausedRightAfterThisLocation;
    }

    public void setWasManuallyPausedRightAfterThisLocation(boolean b) {
        this.wasManuallyPausedRightAfterThisLocation = b;
    }

    public boolean getWasManuallyResumedRightBeforeThisLocation() {
        return wasManuallyResumedRightBeforeThisLocation;
    }

    public void setWasManuallyResumedRightBeforeThisLocation(boolean b) {
        this.wasManuallyResumedRightBeforeThisLocation = b;
    }

    public double getSmoothedSpeedMps() {
        return smoothedSpeedMps;
    }
/*
    public double getSmoothedElevation() {
        return smoothedElevation;
    }

 */

    public double getSatelliteElevation() {
        return satelliteElevation;
    }

    public double getNetworkElevationMeters() {
        return networkElevationMeters;
    }

    // using a getter since pathChangeAngle isn't final; wanted to make it private
    public double getAngle() {
        return angle;
    }

    public double getRawSpeedMps() {
        return rawSpeedMps;
    }

    public void setRawSpeedMps(double newRawSpeedMps) {
        this.rawSpeedMps = newRawSpeedMps;
    }

    public void setSmoothedSpeedMps(double smoothedSpeedMps) {
        this.smoothedSpeedMps = smoothedSpeedMps;
    }

    public void setDistanceFromStartMeters(double currentRideDistanceMeters) {
        distanceFromStartMeters = currentRideDistanceMeters;
    }

    // used for graphing purposes
    public double getDistanceFromStartMeters() {
        return distanceFromStartMeters;
    }

    public void setNetworkElevationMeters(double networkElevationMeters) {
        this.networkElevationMeters = networkElevationMeters;
    }

    public void setIsElevationDefining(boolean isElevationDefining) {
        this.isElevationDefining = isElevationDefining;
    }

    public boolean getIsElevationDefining() {
        return isElevationDefining;
    }
}