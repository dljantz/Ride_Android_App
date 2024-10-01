package com.jantztechnologies.ride;

// The point of this class is to hold just a few different data types together (int and double)
//      to send elevation data from the elevation getter back to the service / activity that requested
//      it. Elevation queries happen in bundles of 100, so I need to send an Array back with 100 elevation
//      data points. Those points also need to have the proper index associated with them so they can
//      be reassociated with the proper location object within acceptedLocations. All of this because
//      I didn't want to send over the location object itself into the elevation getter class, as that
//      risks forking the location object if I ever come back and unwittingly add further refinements
//      to location object attributes in the lines following the elevation request in RecordRideService...
//      Those edits would be erased by the returned location object from the elevation getter when it
//      is inserted back into the acceptedLocations ArrayList.

public class ElevationDatumPackage {

    private final int locationsListIndex;
    private final double latitude;
    private final double longitude;
    private double elevationMeters;

    public ElevationDatumPackage(int acceptedLocationsIndex, double latitude, double longitude) {
        this.locationsListIndex = acceptedLocationsIndex;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationMeters = App.NO_ELEVATION_DATA;
    }

    public void setElevationMeters(double elevationMeters) {
        this.elevationMeters = elevationMeters;
    }

    public int getLocationsListIndex() {
        return locationsListIndex;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getElevationMeters() {
        return elevationMeters;
    }
}
