package com.jantztechnologies.ride2;

import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

// allows saved rides list to have as many items as I want -- takes in data,
//      uses it to inflate xml layouts, and puts them in order how I want.
public class SavedRidesListAdapter extends RecyclerView.Adapter<SavedRidesListAdapter.ViewHolder>{

    private final ArrayList<String> listData;
    private final Context context;
    ////////private static int mapWidthPx;
    ////////////private static int mapHeightPx;
    private final int distanceUnits;

    // RecyclerView recyclerView;
    public SavedRidesListAdapter(Context context, ArrayList<String> listData) {
        this.listData = listData;
        this.context = context;
        // I'm doing this here so it doesn't have to run for every single MapView.
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        ////////////mapWidthPx = (int) (displayMetrics.widthPixels * 0.5); // map is close to half the width of the screen
        ///////////////mapHeightPx = (int) (200 * displayMetrics.density); // ALERT!! 200 dp is the hardcoded height
        //      of view objects. see saved_rides_list_item.xml. If it changes over there, I have to
        //      manually change it here.
        distanceUnits = FileIO.loadInt(context, context.getResources().getString(R.string.settings_folder_name), context.getResources().getString(R.string.distance_units_setting_filename));
    }

    // I think this is just inflating the xml layout for a list item, but doesn't
    //      put any of the data in
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View listItem= layoutInflater.inflate(R.layout.saved_ride_list_item, parent, false);
        return new ViewHolder(context, listItem);
    }

    // takes the inflated xml from onCreateViewHolder, puts data into the textview
    //      and imageview from a particular list item in listData.
    // also creates a click listener for that list item to perform whatever action I want
    //      (later make it open ViewRideActivity)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final String rideFolder = listData.get(position);
        RideStats rideStats = FileIO.loadRideStats(context, rideFolder, context.getResources().getString(R.string.ride_stats_filename));

        // I edited the rideStats class and now file IO is throwing errors because the objects
        //      saved to file earlier this week don't match the changed methods exactly... maybe just
        //      leave this try catch block in place though, it makes the app more resilient.
        try {
            // set up all the textviews, depending on desired speed / distance units
            holder.rideDateTextView.setText(rideStats.getDateText(false));
            String distanceString;
            String speedString;
            if (distanceUnits == App.METRIC_UNITS) {
                distanceString = UnitConversion.getDistanceStringKilometers(rideStats.distanceMeters) + " kilometers";
                speedString = UnitConversion.getSpeedStringKph(rideStats.averageSpeedMps) + " kph average";
            } else {
                distanceString = UnitConversion.getDistanceStringMiles(rideStats.distanceMeters) + " miles";
                speedString = UnitConversion.getSpeedStringMph(rideStats.averageSpeedMps) + " mph average";
            }
            holder.rideDistanceTextView.setText(distanceString);
            holder.rideAvgSpeedTextView.setText(speedString);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        // if image does not exist, all the below code does is set image to null. hopefully that doesn't break anything
        holder.imageView.setImageBitmap(FileIO.loadBitmap(context, rideFolder, context.getResources().getString(R.string.route_thumbnail_filename)));
        holder.constraintLayout.setOnClickListener(view -> {
            Intent intent = new Intent(context, ViewRideActivity.class);
            intent.putExtra(context.getResources().getString(R.string.ride_folder_name), rideFolder); // have to have two Strings, so it's a bit redundant...
            intent.putExtra(context.getResources().getString(R.string.enable_back_button), true);
            context.startActivity(intent);
        });
    }

    // ???
    @Override
    public int getItemCount() {
        return listData.size();
    }

    // this class basically creates object references to all the layout
    //      stuff defined in xml so I can mess with it programmatically
    public class ViewHolder extends RecyclerView.ViewHolder {

        private final Context context;
        public ImageView imageView;
        ////////////public MapView mapViewLite;
        public TextView rideDateTextView;
        public TextView rideDistanceTextView;
        public TextView rideAvgSpeedTextView;
        public ConstraintLayout constraintLayout;

        public ViewHolder(Context context, View itemView) {
            super(itemView);
            this.context = context; // this is actually an instance of MainActivity passed a few layers down... idk how else to call the methods I need
            this.rideDateTextView = (TextView) itemView.findViewById(R.id.rideDateTextView);
            this.rideDistanceTextView = (TextView) itemView.findViewById(R.id.distanceTextView);
            this.rideAvgSpeedTextView = (TextView) itemView.findViewById(R.id.averageSpeedTextView);
            this.constraintLayout = (ConstraintLayout) itemView.findViewById(R.id.rideListConstraintLayout);
            this.imageView = (ImageView) itemView.findViewById(R.id.imageView);
            /////////this.mapViewLite = (MapView) itemView.findViewById(R.id.routePreviewView);
            //////////mapViewLite.onCreate(null);
            /////////mapViewLite.getMapAsync(this::createMap);
        }
/*
        //Manipulates the map once available, triggered by OnMapReadyCallback interface
        public void createMap(@NonNull GoogleMap googleMap) {

            String rideFolder = "ride_folder_23"; // FIX LATER!!!
            ArrayList<Integer> manualPauseIndicesAllLocations = FileIO.loadArrayListIntegers(context, rideFolder, context.getResources().getString(R.string.manual_pause_indices_all_locations_filename));
            ArrayList<SerializableLocation> allLocations = FileIO.loadArrayListLocations(context, rideFolder, context.getResources().getString(R.string.all_locations_filename));
            ArrayList<Integer> manualPauseIndicesAcceptedLocations = FileIO.loadArrayListIntegers(context, rideFolder, context.getResources().getString(R.string.manual_pause_indices_accepted_locations_filename));
            ArrayList<SerializableLocation> acceptedLocations = FileIO.loadArrayListLocations(context, rideFolder, context.getResources().getString(R.string.accepted_locations_filename));

            MapIllustrator mapIllustrator = new MapIllustrator(
                    context,
                    mapWidthPx,
                    mapHeightPx,
                    googleMap,
                    "ride_folder_23",
                    manualPauseIndicesAllLocations,
                    manualPauseIndicesAcceptedLocations,
                    allLocations,
                    acceptedLocations);

            mapIllustrator.setMapStyle(); // light / dark / satellite, that sort of thing
            mapIllustrator.setMapSettings(false); // zoom controls enabled = false
            mapIllustrator.findCameraBounds(0.1);
            // below -- polyline options method call is sloppy with just zIndex parameter. Will be removed when I
            //      switch to just drawing accepted locations.
            mapIllustrator.generatePolylineOptions(0);
            mapIllustrator.generatePolylineOptions(1);
            mapIllustrator.drawPolylines();
        } */
    }
}

