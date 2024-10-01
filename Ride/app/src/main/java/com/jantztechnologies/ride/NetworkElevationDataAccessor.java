package com.jantztechnologies.ride;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

// This class is used for network connections. Just elevation queries to opentopodata.org for now.
public class NetworkElevationDataAccessor {

    // todo:
    //      figure out how to format url string for GET request
    //      include limit of 2000 characters to minimize errors
    //      make the most of requests... max 100 calls per request, 1 call per second, 1000 calls per day
    //      try maxing out calls per day / per second and see if I can get the app to break
    //      test for type of network connection and prioritize requests when wifi is available
    //      switch back and forth between ned10m and aster30m depending on location?
    //      allow backlog of elevation requests to accrue, then get caught up on, in long periods of no network availability

    private final Context context;
    private ArrayList<ElevationDatumPackage> elevations;
    private boolean isWaitingForNetworkReply;
    private long startTime; // for timing of network response
    private boolean isUserInsideUSA = true; // default assumption: user is in US, can use hyper-accurate NED.

    public NetworkElevationDataAccessor(Context context) {
        this.context = context;
        this.elevations = new ArrayList<>();
        this.isWaitingForNetworkReply = false;
    }

    // adds the coordinates listed to the url string that will be sent out.
    // Also adds a new ElevationDatumPackage to the arrayList that will be sent back once the network
    //      query is sent and received.
    public void addCoordinatesToQuery(double latitude, double longitude, int routeIndex) {

        // gotta keep track of the routeIndex of these for later reassignment back into acceptedLocations.
        // appending to the END of the list mirrors what's happening to the query url to minimize confusion (not reversed order from each other or anything)
        // Also, I added a filter to prevent duplicate elevationDatumPackages... pressing "STOP" calls this method
        //      regardless of whether the most recent location was already part of the query or not. This filter
        //      catches those situations so we don't ask for the elevation of the same coordinates twice. Though
        //      it should be noted that that's not the end of the world, it doesn't cause any errors.
        if (elevations.isEmpty() || // prevents IndexOutOfBoundsException if elevations is empty
                routeIndex != elevations.get(elevations.size() - 1).getLocationsListIndex()) {
            elevations.add(new ElevationDatumPackage(routeIndex, latitude, longitude));
        }
        // I chose not to call requestElevations here as it is occurring once every several seconds in RecordRideService.
        //      I'm calling it THERE because there needs to be a way to call it repeatedly, without needing
        //      to be triggered by a new set of coordinates, to enable a backlog of elevation requests
        //      to be cleared if necessary.
        System.out.println("The size of the list of coordinates to query network for elevation is " + elevations.size());
    }

    // This queries opentopodata.org's free api for elevation data. It stores that data in this
    //      NetworkElevationDataAccessor object until the service or activity running the show right now asks
    //      for it. Probably not till a few seconds after the query was sent to give the server
    //      a chance to respond.
    public void requestElevations(int minQueryLength) {
        // The whole chunk of code below is adapted from developer docs tutorial
        //      under NetworkElevationDataAccessor --> Transmit Network Data Using Volley --> Make a Standard Request.
        //      After copypasting their code, Android Studio suggested I replace the anonymous listeners
        //      with lambdas, so I did so.

        // three important filters before allowing a network request: Is the query length long enough?
        //      Is there already a network request zipping around the interwebz that we're waiting on?
        //      And is the first elevationDatum (and thus those that follow) empty of actual elevation data?
        //      If so, proceed.
        if (elevations.size() >= minQueryLength &&
            !isWaitingForNetworkReply &&
            elevations.get(0).getElevationMeters() == App.NO_ELEVATION_DATA) {

            ////////////////////////Toast.makeText(context, "Sending network elevation request", Toast.LENGTH_SHORT).show();

            isWaitingForNetworkReply = true; // prevent outgoing requests while waiting on this one
            String queryUrl = buildQueryUrl();
            System.out.println("query url is " + queryUrl); // todo: delete probably
            startTime = System.currentTimeMillis(); // can delete if I want, just here to find out how long queries take

            JsonObjectRequest jsonObjectRequest =
                    new JsonObjectRequest(Request.Method.GET,
                            queryUrl,
                            null,
                            this::handleElevationResponse,
                            this::handleElevationResponseError);

            RequestQueue requestQueue = Volley.newRequestQueue(context);
            requestQueue.add(jsonObjectRequest);
        }
    }

    // Cycles through the list of elevation data packages to assign them the elevation value returned from
    //      the server.
    private void handleElevationResponse(JSONObject response) {
        long networkDelayMillis = System.currentTimeMillis() - startTime; // todo: delete later! no need to tell user about this.
        ///////////////////////Toast.makeText(context, "It took " + networkDelayMillis + " milliseconds to receive network response.", Toast.LENGTH_SHORT).show();

        JSONArray results;
        try {
            results = response.getJSONArray("results");
        } catch (JSONException e) {
            // uh, hopefully this never happens. But if it does, abort the hell out of this method
            e.printStackTrace();
            isWaitingForNetworkReply = false; // re-enable network requests, no need to wait for a reply anymore
            //////////////////////Toast.makeText(context, "An unknown problem occurred while handling received elevation data from the network", Toast.LENGTH_LONG).show();
            return;
        }

        // for loop iterates through the JSONArray instead of elevations because elevations could
        //      be much longer than the JSONArray due to a backlog of requests. This way, indexOutOfBoundsExceptions
        //      are avoided.
        for (int i = 0; i < results.length(); i++) {
            try {
                elevations.get(i).setElevationMeters(results.getJSONObject(i).getDouble("elevation"));
            } catch (JSONException e) {
                // If the coordinates queried fall outside the digital elevation model being queried, null is returned.
                //      A JSONException is thrown since null cannot be converted to a double when setting elevationMeters
                //      attribute. The first time this happens, we'll assume it's cuz the user is outside the USA.
                //      So we revise our locality assumption, break the for loop, and hope the next query
                //      solves the issue by using ASTER (a global DEM) instead of NED (from USGS).
                if (isUserInsideUSA) {
                    isUserInsideUSA = false;
                    System.out.println("Just switched from NED to ASTER for the next query and aborted interpreting current results");
                    break;
                }
                // The second time a JSONException is thrown, the user must be floating in bumfuck nowhere
                //      on the ocean somewhere in order to fall outside the ASTER DEM. So this time we handle
                //      the null value by just setting that elevation datum's elevation to 0, since last I checked
                //      the surface of the ocean is 0 meters above sea level. Then continue the for loop as it's
                //      possible the next set of coordinates falls back inside ASTER.
                else {
                    elevations.get(i).setElevationMeters(0);
                    System.out.println("Just set seafaring cyclist's elevation to zero meters");
                }
            }
        }
        isWaitingForNetworkReply = false; // re-enable network requests, no need to wait for a reply anymore
    }

    // triggered by network errors, not server errors, as far as I can tell... if server is active
    //      and can't supply data we get a JSONException in the other method. Only if we get NOTHING
    //      back do we trigger this method. So airplane mode, lost at sea, concrete bunker, etc.
    private void handleElevationResponseError(VolleyError volleyError) {
        // VolleyErrors are just a container for Exceptions, so I could print the stack trace
        //      if needed to troubleshoot
        /////////////////Toast.makeText(context, "No data received from network", Toast.LENGTH_SHORT).show();
        isWaitingForNetworkReply = false; // even though an error was received, gotta make note that we're not waiting for anything anymore
    }

    // Returns elevation data package to RecordRideService, or whatever class called it
    public ArrayList<ElevationDatumPackage> getNetworkElevations() {
        return elevations;
    }

    // I'm building this at the last second before making the network request to make sure there's no
    //      misalignment between the indices of the elevations ArrayList and the returned JSON Object.
    //      This way, the queryUrl is ALWAYS built from index 0 of the elevations ArrayList, even in
    //      backlog situations caused by no network access.
    private String buildQueryUrl() {
        // default assumption: user is in US. App attempts to use USGS's National Elevation Dataset
        //      first, then if that fails we must be outside the US and should use a global DEM instead.
        String usaBaseUrl = "https://api.opentopodata.org/v1/ned10m?locations=";
        String globalBaseUrl = "https://api.opentopodata.org/v1/aster30m?locations=";
        StringBuilder queryUrl;

        if (isUserInsideUSA) queryUrl = new StringBuilder(usaBaseUrl);
        else queryUrl = new StringBuilder(globalBaseUrl);

        for (int i = 0; i < elevations.size(); i++) {
            if (i >= 100) break; // opentopodata.org imposes a hard limit of 100 locations per query
            // The internet says GET requests are supposed to max out at 2048 characters, but
            //      I tested it and opentopodata.org handled almost 5000 characters no problem.
            //      I never actually found the limit... it turns out it depends on the browser you're
            //      using? kinda weird.
            queryUrl.append(elevations.get(i).getLatitude()).append(",").append(elevations.get(i).getLongitude()).append("|"); // coordinates for opentopodata.org have to be separated by pipe symbol. An extra pipe symbol on the very end does not seem to cause issues for the server.
        }
        return queryUrl.toString();
    }

    public void deleteElevationDatum(int index) {
        elevations.remove(index);
    }
}
