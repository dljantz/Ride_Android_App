package com.jantztechnologies.ride;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

// The interwebz say AppCompatActivity is better for compatibility with future API releases
public class MainActivity extends AppCompatActivity {

    private View titleScreenBackgroundGradient;
    private ImageView logo;
    private TextView companyNameTextView;
    private TextView tapToContinueTextView;
    ImageView titleRImageView;
    ImageView titleIImageView;
    ImageView titleDImageView;
    ImageView titleEImageView;
    TitleScreenAnimationArrays animationArrays; // delete if this doesn't work
    /////////////////////////private AnimationDrawable animationR;
    /////////////////////////private AnimationDrawable animationI;
    ///////////////////////////private AnimationDrawable animationD;
    /////////////////////////private AnimationDrawable animationE;

    // purpose: brief title screen
    Handler handler = new Handler();
    Runnable titleScreenRunnable = new Runnable() {
        long startTimeMillis = System.currentTimeMillis();
        int millisCounter = 0;
        int animationFrame = 0; // used as an index to pull correct drawable from animationArray.
        final int animationStartTimeMillis = 400;
        final int companyNameFadeInTimeMillis = 2500;
        final int cutOffMillis = 6000;
        @Override
        public void run() {
            if (millisCounter == 0) startTimeMillis = System.currentTimeMillis(); // was assigned already, but this ensures everything works right if there was a lag between then and now
            // keep track of how long the runnable has been going
            millisCounter = (int) (System.currentTimeMillis() - startTimeMillis);
            if (millisCounter >= animationStartTimeMillis) {
                animationFrame = (millisCounter - animationStartTimeMillis) / 30; // dividing by 20 should make each frame take 20 milliseconds
                try { titleRImageView.setBackgroundResource(animationArrays.getRDrawableAtIndex(animationFrame));
                } catch (IndexOutOfBoundsException ignored){ }
                try { titleIImageView.setBackgroundResource(animationArrays.getIDrawableAtIndex(animationFrame - 12)); // subtracting causes letters after R to animate a split second behind
                } catch (IndexOutOfBoundsException ignored){ }
                try { titleDImageView.setBackgroundResource(animationArrays.getDDrawableAtIndex(animationFrame - 24));
                } catch (IndexOutOfBoundsException ignored){ }
                try { titleEImageView.setBackgroundResource(animationArrays.getEDrawableAtIndex(animationFrame - 36));
                } catch (IndexOutOfBoundsException ignored){ }
                /////////////////////////animationR.start();
                /////////////////////////animationI.start();
                //////////////////////////animationD.start();
                /////////////////////////animationE.start();
                /////////////////////////animationFrame++; // not bothering to stop incrementing, simpler to just catch the inevitable indexOutOfBoundsException above
            }

            if (millisCounter >= companyNameFadeInTimeMillis) {
                companyNameTextView.setAlpha((float) ((millisCounter - companyNameFadeInTimeMillis) / 1000.0));
                companyNameTextView.setVisibility(View.VISIBLE);
            }

            // cut things off as fast as possible
            if (millisCounter < cutOffMillis) {
                handler.post(this); // no delay, just post as fast as possible for animation smoothness
            } else {
                removeTitleScreen();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // below -- only show user the kick-ass title screen if it's been greater than 18 hours
        //      since they last launched it. Otherwise just leave them alone.
        // first app run -- should return -1 since file doesn't exist yet
        long lastLaunchTimestamp = FileIO.loadLong(this, getResources().getString(R.string.settings_folder_name), getResources().getString(R.string.last_launch_timestamp_filename));
        // below if statement includes case where -1 was returned -- value will be greater
        //      than 18 hours fo shizzle
        int eighteenHours = 1000 * 3600 * 18;
        //////////////int fiveMinutes = 1000 * 60 * 5;
        ////////////////int fiveSeconds = 5000;
        if (System.currentTimeMillis() - lastLaunchTimestamp > eighteenHours) {
            animateTitleScreen();
        }

        Toolbar toolbar = findViewById(R.id.toolbar_main);
        toolbar.setTitle(R.string.past_rides_title);
        toolbar.setTitleTextColor(getResources().getColor(R.color.black));
        setSupportActionBar(toolbar);

        ImageView statisticsIcon = (ImageView) findViewById(R.id.statisticsIcon);
        statisticsIcon.setOnClickListener(this::startStatisticsActivity);

        ImageView settingsIcon = (ImageView) findViewById(R.id.settingsIcon);
        settingsIcon.setOnClickListener(this::startSettingsActivity);

        ImageView newRideButton = (ImageView) findViewById(R.id.riderIconImageView);
        newRideButton.setOnClickListener(this::startRecordRideActivity); // android studio suggested lambda, then "method reference" to replace onClickListener

        // if the settings folder doesn't exist (ie this is the first time the app is run), create settings folder with default values.
        FileIO.createSettingsFolder(this);
        // update last launch timestamp to reset 18 hour title screen time bomb
        FileIO.saveLong(this,
                        getResources().getString(R.string.settings_folder_name),
                        getResources().getString(R.string.last_launch_timestamp_filename),
                        System.currentTimeMillis());
    }

    @Override
    protected void onStart() {

        super.onStart();

        // create saved rides list... may be computation heavy with bitmap creation, multi thread later?
        // placed in onStart so units setting / added ride update on home screen right away, regardless of which
        //      back button is used to get back there.
        ArrayList<String> rideFolderNames = FileIO.getRideFolderNames(this);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.savedRidesRecyclerView);
        SavedRidesListAdapter adapter = new SavedRidesListAdapter(this, rideFolderNames);
        recyclerView.setHasFixedSize(true); // allows recyclerview to operate more efficiently since the view size doesn't depend on the data within.
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // ???
        recyclerView.setAdapter(adapter);
    }

    // called by onClickListener
    public void startStatisticsActivity(View view) {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    // called by onClickListener
    public void startSettingsActivity(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    // called by onClickListener
    public void startRecordRideActivity(View view) {
        Intent intent = new Intent(this, RecordRideActivity.class);
        startActivity(intent);
    }

    // called if it's been a hot second since last launch to remind user how freaking cool the app is
    private void animateTitleScreen() {
        animationArrays = new TitleScreenAnimationArrays(); // animation info already stored over in that class. delete this if it doesn't work.

        titleScreenBackgroundGradient = findViewById(R.id.titleScreenGradient);
        logo = findViewById(R.id.logoImageView);
        companyNameTextView = findViewById(R.id.companyNameTextView);
        tapToContinueTextView = findViewById(R.id.tapToContinueTextView);

        // a couple of things immediately shown
        titleScreenBackgroundGradient.setVisibility(View.VISIBLE);
        logo.setVisibility(View.VISIBLE);
        tapToContinueTextView.setVisibility(View.VISIBLE);

        // allow user to kick out of title screen early
        titleScreenBackgroundGradient.setOnClickListener(view -> removeTitleScreen());

        titleRImageView = findViewById(R.id.titleRImageView);
        titleIImageView = findViewById(R.id.titleIImageView);
        titleDImageView = findViewById(R.id.titleDImageView);
        titleEImageView = findViewById(R.id.titleEImageView);

        /////////////////////////titleRImageView.setBackgroundResource(R.drawable._title_animation_r_fast);
        /////////////////////////titleIImageView.setBackgroundResource(R.drawable._title_animation_i_fast);
        //////////////////////////titleDImageView.setBackgroundResource(R.drawable._title_animation_d_fast);
        /////////////////////////titleEImageView.setBackgroundResource(R.drawable._title_animation_e_fast);

        /////////////////////////animationR = (AnimationDrawable) titleRImageView.getBackground();
        /////////////////////////animationI = (AnimationDrawable) titleIImageView.getBackground();
        /////////////////////////animationD = (AnimationDrawable) titleDImageView.getBackground();
        /////////////////////////animationE = (AnimationDrawable) titleEImageView.getBackground();

        handler.post(titleScreenRunnable);
    }

    private void removeTitleScreen() {
        handler.removeCallbacks(titleScreenRunnable);
        logo.setVisibility(View.INVISIBLE);
        companyNameTextView.setVisibility(View.INVISIBLE);
        titleRImageView.setVisibility(View.INVISIBLE);
        titleIImageView.setVisibility(View.INVISIBLE);
        titleDImageView.setVisibility(View.INVISIBLE);
        titleEImageView.setVisibility(View.INVISIBLE);
        tapToContinueTextView.setVisibility(View.INVISIBLE);
        titleScreenBackgroundGradient.setVisibility(View.INVISIBLE);
    }

    // Get a MemoryInfo object for the device's current memory status. This is needed
    //      due to app crashing on device with low memory allocation (old samsung)
    private ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}