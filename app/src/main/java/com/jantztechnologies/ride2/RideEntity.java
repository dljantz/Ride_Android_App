package com.jantztechnologies.ride2;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "rides")
public class RideEntity implements Serializable {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public double distanceMeters;
    public long durationMillis;
    public double averageSpeedMps;
    public double maxSpeedMps;
    public double maxElevationMeters;
    public double minElevationMeters;
    public double elevationGainMeters;
    public double elevationLossMeters;
    public String thumbnailPath;

    public RideEntity() {
    }

    @androidx.room.Ignore
    public RideEntity(long timestamp,
                      double distanceMeters,
                      long durationMillis,
                      double averageSpeedMps,
                      double maxSpeedMps,
                      double maxElevationMeters,
                      double minElevationMeters,
                      double elevationGainMeters,
                      double elevationLossMeters) {
        this.timestamp = timestamp;
        this.distanceMeters = distanceMeters;
        this.durationMillis = durationMillis;
        this.averageSpeedMps = averageSpeedMps;
        this.maxSpeedMps = maxSpeedMps;
        this.maxElevationMeters = maxElevationMeters;
        this.minElevationMeters = minElevationMeters;
        this.elevationGainMeters = elevationGainMeters;
        this.elevationLossMeters = elevationLossMeters;
    }
}
