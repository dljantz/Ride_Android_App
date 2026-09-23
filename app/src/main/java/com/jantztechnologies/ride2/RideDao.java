package com.jantztechnologies.ride2;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RideDao {

    @Insert
    long insertRide(RideEntity ride);

    @Delete
    void deleteRide(RideEntity ride);

    @Query("DELETE FROM rides WHERE id = :rideId")
    void deleteRideById(long rideId);

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    LiveData<List<RideEntity>> getAllRides();

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    List<RideEntity> getAllRidesSync();

    @Query("SELECT * FROM rides WHERE id = :rideId LIMIT 1")
    RideEntity getRideById(long rideId);

    @Query("SELECT COUNT(*) as count, " +
            "COALESCE(SUM(distanceMeters), 0) as totalDistanceMeters, " +
            "COALESCE(SUM(durationMillis), 0) as totalDurationMillis, " +
            "COALESCE(MAX(maxSpeedMps), 0) as maxSpeedMps, " +
            "COALESCE(MAX(distanceMeters), 0) as longestRideMeters, " +
            "COALESCE(AVG(averageSpeedMps), 0) as avgSpeedMps " +
            "FROM rides WHERE timestamp >= :sinceTimestamp")
    RideStatsSummary getSummarySince(long sinceTimestamp);

    @Query("SELECT * FROM rides WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    List<RideEntity> getRidesSince(long sinceTimestamp);

    @Update
    void updateRide(RideEntity ride);
}
