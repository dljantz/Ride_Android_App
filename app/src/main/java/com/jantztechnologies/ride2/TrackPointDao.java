package com.jantztechnologies.ride2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TrackPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrackPoints(List<TrackPointEntity> points);

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY id ASC")
    List<TrackPointEntity> getTrackPointsForRide(long rideId);

    @Query("SELECT * FROM track_points WHERE rideId = :rideId AND isAccepted = 1 ORDER BY id ASC")
    List<TrackPointEntity> getAcceptedTrackPointsForRide(long rideId);

    @Query("DELETE FROM track_points WHERE rideId = :rideId")
    void deleteTrackPointsForRide(long rideId);
}
