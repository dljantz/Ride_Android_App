package com.jantztechnologies.ride2;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jantztechnologies.ride2.databinding.SavedRideListItemBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SavedRidesListAdapter extends RecyclerView.Adapter<SavedRidesListAdapter.ViewHolder> {

    private final List<RideEntity> listData;
    private final Context context;
    private final int distanceUnits;

    public SavedRidesListAdapter(Context context, List<RideEntity> listData) {
        this.context = context;
        this.listData = listData != null ? listData : new ArrayList<>();
        this.distanceUnits = PreferencesManager.getInstance(context).getDistanceUnits();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SavedRideListItemBinding binding = SavedRideListItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RideEntity ride = listData.get(position);

        holder.binding.rideDateTextView.setText(UnitConversion.getDateText(ride.timestamp, false));

        String distanceString;
        String speedString;
        if (distanceUnits == App.METRIC_UNITS) {
            distanceString = UnitConversion.getDistanceStringKilometers(ride.distanceMeters) + " km";
            speedString = UnitConversion.getSpeedStringKph(ride.averageSpeedMps) + " kph avg";
        } else {
            distanceString = UnitConversion.getDistanceStringMiles(ride.distanceMeters) + " mi";
            speedString = UnitConversion.getSpeedStringMph(ride.averageSpeedMps) + " mph avg";
        }
        holder.binding.distanceTextView.setText(distanceString);
        holder.binding.averageSpeedTextView.setText(speedString);

        if (ride.thumbnailPath != null && new File(ride.thumbnailPath).exists()) {
            Glide.with(context)
                    .load(new File(ride.thumbnailPath))
                    .placeholder(R.drawable._title_screen_app_name)
                    .error(R.drawable._title_screen_app_name)
                    .into(holder.binding.imageView);
        } else {
            holder.binding.imageView.setImageResource(R.drawable._title_screen_app_name);
        }

        holder.binding.clickableView.setOnClickListener(view -> {
            Intent intent = new Intent(context, ViewRideActivity.class);
            intent.putExtra("ride_id", ride.id);
            intent.putExtra(context.getResources().getString(R.string.enable_back_button), true);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final SavedRideListItemBinding binding;

        public ViewHolder(SavedRideListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
