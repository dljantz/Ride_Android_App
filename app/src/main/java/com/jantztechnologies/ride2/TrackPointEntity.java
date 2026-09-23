package com.jantztechnologies.ride2;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(
        tableName = "track_points",
        foreignKeys = @ForeignKey(
                entity = RideEntity.class,
                parentColumns = "id",
                childColumns = "rideId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("rideId")}
)
public class TrackPointEntity implements Serializable {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long rideId;
    public double latitude;
    public double longitude;
    public double altitudeMeters;
    public double networkElevationMeters = App.NO_ELEVATION_DATA;
    public boolean isElevationDefining;
    public double accuracyMeters;
    public float rawSpeedMps;
    public float smoothedSpeedMps;
    public double distanceFromStartMeters;
    public long timestampNanos;
    public boolean isAccepted;
    public boolean isManualPauseBreak;
    public boolean wasManuallyResumed;

    public TrackPointEntity() {
    }

    @androidx.room.Ignore
    public TrackPointEntity(long rideId,
                            double latitude,
                            double longitude,
                            double altitudeMeters,
                            double networkElevationMeters,
                            boolean isElevationDefining,
                            double accuracyMeters,
                            float rawSpeedMps,
                            float smoothedSpeedMps,
                            double distanceFromStartMeters,
                            long timestampNanos,
                            boolean isAccepted,
                            boolean isManualPauseBreak,
                            boolean wasManuallyResumed) {
        this.rideId = rideId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeMeters = altitudeMeters;
        this.networkElevationMeters = networkElevationMeters;
        this.isElevationDefining = isElevationDefining;
        this.accuracyMeters = accuracyMeters;
        this.rawSpeedMps = rawSpeedMps;
        this.smoothedSpeedMps = smoothedSpeedMps;
        this.distanceFromStartMeters = distanceFromStartMeters;
        this.timestampNanos = timestampNanos;
        this.isAccepted = isAccepted;
        this.isManualPauseBreak = isManualPauseBreak;
        this.wasManuallyResumed = wasManuallyResumed;
    }
}
