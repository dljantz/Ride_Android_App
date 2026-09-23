package com.jantztechnologies.ride2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

// TODO: test graphview on a variety of virtual devices for robustness with different screen sizes
//      and pixel densities. There are still a couple of places where I hardcoded in margins of 10 pixels
//      that will probs end up looking weird on other dpis.

// a lot of this is based on https://developer.android.com/training/custom-views/create-view
public class GraphView extends View {

    private int themeColor;
    private final String title;
    private final String xAxisLabel;
    private final Paint brightPaint;
    private final Paint grayPaint;
    private final Paint whitePaint;
    private int viewWidthPx;
    private int viewHeightPx;
    private int graphLeftPx;
    private int graphTopPx;
    private int graphRightPx;
    private int graphBottomPx;
    private int graphWidthPx;
    private int graphHeightPx;
    private double maxXAxisValueDefaultUnits;
    private double maxYAxisValueDefaultUnits;
    private double minYAxisValueDefaultUnits;
    private ArrayList<SerializableLocation> acceptedLocations;
    private ArrayList<RideStats> pastRides;
    private int units; // imperial or metric
    private boolean isDataReady;
    private boolean isViewSized;
    private boolean hasOnDrawPrematurelyRun; // used in redraw to prevent duplicate method calls of onDraw. It's kinda weird.
    private Path path;

    public GraphView(Context context, AttributeSet attrs) {

        super(context, attrs);
        isDataReady = false;
        isViewSized = false;
        hasOnDrawPrematurelyRun = false;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.GraphView,
                0, 0);

        try {
            themeColor = a.getInteger(R.styleable.GraphView_themeColor, R.color.gray);
            title = a.getString(R.styleable.GraphView_title);
            xAxisLabel = a.getString(R.styleable.GraphView_xAxisLabel);
        } finally {
            a.recycle();
        }

        // creating Paint objects ahead of time makes redraw events less computationally expensive
        //      as they don't have to be recreated multiple times
        brightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brightPaint.setColor(themeColor);
        brightPaint.setStyle(Paint.Style.FILL);

        grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // white with low alpha for now
        grayPaint.setColor(Color.argb(255, 127, 127, 127));
        grayPaint.setStyle(Paint.Style.FILL);

        // used for text
        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.argb(255, 255, 255, 255));
        whitePaint.setStyle(Paint.Style.FILL);

        // backup values in case onDraw somehow gets called before onSizeChanged
        viewWidthPx = 0;
        viewHeightPx = 0;
        graphLeftPx = 0;
        graphTopPx = 0;
        graphRightPx = 0;
        graphBottomPx = 0;
        graphWidthPx = 0;
        graphHeightPx = 0;
    }

    // have to wait until a size is assigned to do layout stuff within the custom view.
    // onDraw() should always be called by the system after this one.
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        viewWidthPx = w;
        viewHeightPx = (int) (w * 0.75);
        setMeasuredDimension(viewWidthPx, viewHeightPx);

        graphLeftPx = (int) (viewWidthPx * 0.13);
        graphTopPx = (int) (viewHeightPx * 0.13);
        graphRightPx = (int) (viewWidthPx * 0.98);
        graphBottomPx = (int) (viewHeightPx * 0.87);
        graphWidthPx = graphRightPx - graphLeftPx;
        graphHeightPx = graphBottomPx - graphTopPx;
/*
        // speed graph in View Ride activity gets a nice gradient look overriding theme color
        if (title.equals(getResources().getString(R.string.speed_graph_title))) {
            brightPaint.setShader(
                    new LinearGradient(
                            0,
                            graphBottomPx,
                            0,
                            graphTopPx,
                            getResources().getColor(R.color.purple),
                            getResources().getColor(R.color.red),
                            MIRROR)); // as far as I can tell the tile mode doesn't really matter in this case
        }
 */

        isViewSized = true;

        // Unclear whether redraw() or onSizeChanged() runs first, so I'm just calling createPath()
        //      in both places and going with whichever comes last using an if statement to check if
        //      the other method has adjusted one of its variables yet.
        if (isDataReady) createPath();
    }

    // at the moment, using this to redraw the canvas after data is available. I was going to
    //      call onDraw myself, but this might be more "correct"? Plus I don't know how to get
    //      access to the Canvas object, that seems to be magically supplied by the system.
    protected void redraw(double maxXAxisValue,
                          double maxYAxisValue,
                          double minYAxisValue,
                          ArrayList<SerializableLocation> acceptedLocations,
                          ArrayList<RideStats> pastRides,
                          int units,
                          int themeColor,
                          boolean isLateRedraw) {
        // finally get to assign values to these bad boys
        // acceptedLocations and pastRides are never used at the same time, different data sources for the graph.
        this.maxXAxisValueDefaultUnits = maxXAxisValue;
        if (maxYAxisValue != 0) {
            this.maxYAxisValueDefaultUnits = maxYAxisValue;
        } else {
            this.maxYAxisValueDefaultUnits = 1; // make the speed graph draw some gridlines even when there's no route data
        }
        this.minYAxisValueDefaultUnits = minYAxisValue;
        System.out.println("max Y axis value (meters or mps): " + maxYAxisValue);
        System.out.println("min Y axis value (meters or mps): " + minYAxisValue);
        this.acceptedLocations = acceptedLocations;
        this.pastRides = pastRides;
        this.units = units;
        if (themeColor != 0) brightPaint.setColor(themeColor);

        // Unclear whether redraw() or onSizeChanged() runs first, so I'm just calling createPath()
        //      in both places and going with whichever comes last using an if statement to check if
        //      the other method has adjusted one of its variables yet.
        if (isViewSized) createPath();
        isDataReady = true;

        // below: if true, onSizedChanged occurred before redraw, so we need to invalidate and request a
        //      new layout. However, if we don't make it inside this if statement, redraw beat onSizedChanged.
        //      Therefore, the system is about to call onDraw after data is ready and everything in the view will
        //      be laid out correctly. No need to ask the system to call onDraw a second time.
        // To complicate matters slightly, months later I added the "isLateRedraw" boolean to allow
        //      for redraw to be performed when network elevation is received many milliseconds after
        //      the GraphView is finished being created / drawn.
        if (hasOnDrawPrematurelyRun || isLateRedraw) {
            invalidate();
            requestLayout();
        }
    }

    // determine the x y coordinates of every point on the graph of speed, elevation, OR for distance
    //      data pertaining to multiple rides over a certain time range
    private void createPath() {

        path = new Path();

        // graph starts at origin by default, but this may be an issue with elevation
        path.moveTo(graphLeftPx, graphBottomPx);
        // default value, hopefully never used. I wanted to declare this variable with a wider scope
        //      so it could be used later to drop the path straight down to the bottom of the graph
        //      rather than the bottom right corner. If the path is complete, that ends up being
        //      the bottom right corner anyway. If the path is incomplete, e.g. elevation data is
        //      still being downloaded, it makes the graph look better while downloading happens.
        float x = graphRightPx;

        // there are two cases -- this one is for ViewRideActivity in which we are graphing speed or elevation data.
        if (acceptedLocations != null) {
            try {
                // all the graph data points
                for (int i = 0; i < acceptedLocations.size(); i++) {
                    // using floats rather than ints to prevent rounding errors -- must multiply variables
                    //      by a decimal below
                    x = graphLeftPx + graphWidthPx *
                            (float) (acceptedLocations.get(i).getDistanceFromStartMeters() / maxXAxisValueDefaultUnits);
                    float y = graphBottomPx; // backup variable assignment, should never retain this value
                    // I'm running this if statement umpteen times. Is there a way to to only run it once without
                    //      having to essentially duplicate this whole method?
                    if (title.equals(getResources().getString(R.string.speed_graph_title))) {
                        y = graphBottomPx - graphHeightPx * (float) (acceptedLocations.get(i).getSmoothedSpeedMps() / maxYAxisValueDefaultUnits);
                    } else if (title.equals(getResources().getString(R.string.elevation_graph_title))) {
                        // some extra math fun in here to compensate for the fact that zero is not our baseline value as it is for speed
                        y = graphBottomPx - graphHeightPx * (float) ((acceptedLocations.get(i).getNetworkElevationMeters() - minYAxisValueDefaultUnits) / (maxYAxisValueDefaultUnits - minYAxisValueDefaultUnits));
                    }
                    path.lineTo(x, y);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        // second case -- distance data over multiple rides to see weekly, monthly, yearly etc. distance progress
        else if (pastRides != null){
            try {
                double cumulativeDistanceMeters = 0;
                float y = graphBottomPx;
                for (int i = 0; i < pastRides.size(); i++) {
                    // using floats rather than ints to prevent rounding errors -- must multiply variables
                    //      by a decimal below
                    System.out.println("maxX axis value millis: " + maxXAxisValueDefaultUnits);
                    double timeRangeStartMillis = System.currentTimeMillis() - maxXAxisValueDefaultUnits;
                    double millisAfterTimeRangeStart = pastRides.get(i).timestamp - timeRangeStartMillis;
                    double fractionOfTheWayAcrossTheGraph = millisAfterTimeRangeStart / maxXAxisValueDefaultUnits;
                    System.out.println("x axis fraction:  " + fractionOfTheWayAcrossTheGraph);
                    x = (float) (graphLeftPx + graphWidthPx * fractionOfTheWayAcrossTheGraph);
                    path.lineTo(x, y); // go straight horizontal to underneath the next point to create stair step look

                    cumulativeDistanceMeters += pastRides.get(i).distanceMeters;
                    y = graphBottomPx - graphHeightPx *
                            (float) (cumulativeDistanceMeters / maxYAxisValueDefaultUnits);
                    path.lineTo(x, y);
                }
                // extend the line over to the right edge of the graph so it doesn't look like the
                //      cumulative distance dropped to zero after the last ride2. I added an if statement
                //      filter to handle the edge case where we are displaying an empty graph (no rides
                //      exist in this time range). That prevents a weird triangle graph from being drawn.
                if (cumulativeDistanceMeters > 0) {
                    path.lineTo(graphRightPx, graphTopPx);
                    // push the x value all the way to the right so when it's called a few lines from
                    //      now it drops us down to the bottom right corner.
                    x = graphRightPx;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        // graph ends at bottom right corner to finish the polygon and get a flat bottom
        path.lineTo(x, graphBottomPx); // see earlier comment on the purpose of "x" in this method
    }

    // TODO: So we have this issue with onDraw being run in full twice. Apparently the system is
    //      calling it twice, not me. The fuck is going on
    @Override
    protected void onDraw(Canvas canvas) {
        // I don't know how to stop onDraw from getting called by the system so at the moment I'm
        //      resigned to spending a tiny bit of computation checking to see if the data is ready
        //      to be graphed or not
        if (isDataReady) {
            super.onDraw(canvas);

            // centered title
            whitePaint.setTextSize(graphTopPx * (float) 0.6);
            String yAxisUnitString = findAxisUnitString(title);
            canvas.drawText(title + yAxisUnitString,
                        (float) ((graphLeftPx + graphRightPx) / 2.0 - whitePaint.getTextSize() * 0.47 * (title.length() + 6) * 0.5), // multiplier for text size was found with guess and check, 6 represents 6 characters for units
                        whitePaint.getTextSize(),
                        whitePaint);

            canvas.drawPath(path, brightPaint);

            // reduce text size by half for x axis title
            whitePaint.setTextSize(whitePaint.getTextSize() * (float) 0.5);
            drawAndNumberGridlines(canvas); // perhaps this should return a value to be used by createPath?
            String xAxisUnitString = findAxisUnitString(xAxisLabel);
            canvas.drawText(xAxisLabel + xAxisUnitString,
                    (float) ((graphLeftPx + graphRightPx) / 2.0 - whitePaint.getTextSize() * 0.47 * (xAxisLabel.length() + 5) * 0.5), // multiplier for text size was found with guess and check
                    (float) (viewHeightPx - whitePaint.getTextSize() * 0.1),
                    whitePaint);
        } else {
            // fairly lengthy comment on the purpose of this variable in redraw()
            hasOnDrawPrematurelyRun = true;
        }
    }

    // handles horizontal and vertical gridlines. LOTS of duplication for now, I may try to actually be
    //      a good programmer later and condense. Or maybe horizontal and vertical are just different
    //      enough to justify separating?
    private void drawAndNumberGridlines(Canvas canvas) {
        double maxXAxisValueInCorrectUnits = findAxisValueInCorrectUnits(maxXAxisValueDefaultUnits, xAxisLabel);
        double maxYAxisValueInCorrectUnits = findAxisValueInCorrectUnits(maxYAxisValueDefaultUnits, title);
        double minYAxisValueInCorrectUnits = findAxisValueInCorrectUnits(minYAxisValueDefaultUnits, title);
        //////////////////System.out.println("min y axis value, correct units: " + minYAxisValueInCorrectUnits);

        // below: works well for everything except for inputs of zero as max axis values, then things get funky.
        double xIncrement = findGridlines(maxXAxisValueInCorrectUnits, 0, 4.0);
        double yIncrement = findGridlines(maxYAxisValueInCorrectUnits, minYAxisValueInCorrectUnits, 10.0);

        int gridLineThickness = 2;

        int numVerticalGridlines = (int) (maxXAxisValueInCorrectUnits / xIncrement); // casting to int truncates
        // below: "if the number marked by the last gridline is too close to the end of the graph, remove it."
        if (numVerticalGridlines * xIncrement > maxXAxisValueInCorrectUnits - xIncrement / 2.0) numVerticalGridlines -= 1;

        double yAxisRange = maxYAxisValueInCorrectUnits - minYAxisValueInCorrectUnits;
        int numHorizontalGridlines = (int) (yAxisRange / yIncrement); // casting to int truncates
        // below: "if the number marked by the last gridline is too close to the top of the graph, remove it."
        if (numHorizontalGridlines * yIncrement > yAxisRange - yIncrement / 2.0) numHorizontalGridlines -= 1;


        // y axis line and all VERTICAL gridlines (x axis increments). These are funky because they are
        //      driven by xIncrement / distance, leaving a weird wide or narrow gap at the end so the
        //      x axis can increment up by nice numbers.
        for (int i = 1; i <= numVerticalGridlines; i++) {
            float left = (float) (graphLeftPx + graphWidthPx * i * xIncrement / maxXAxisValueInCorrectUnits - gridLineThickness / 2.0);
            float top = (float) (graphTopPx - gridLineThickness * 0.5);
            float right = left + gridLineThickness;
            float bottom = (float) (graphBottomPx + gridLineThickness * 0.5);
            canvas.drawRect(left, top, right, bottom, grayPaint);

            // handle axis numbering at the same time
            String numberString = String.valueOf(Math.round(xIncrement * i * 10000) / 10000.0); // had to do some rounding stuff bc we sometimes get minute math errors, ending up with gridline label of 0.150000000002 for example
            // some fancy calculations for x placement just copied and pasted from above -- just centering the number on the gridline.
            canvas.drawText(numberString,
                            (float) (right - whitePaint.getTextSize() * 0.47 * (numberString.length()) * 0.5),
                         graphBottomPx + whitePaint.getTextSize() + 10,
                            whitePaint);
        }

        // far left gridline taken care of manually. Not really necessary, could just have for loop
        //      above start at 0 instead, but this allows an empty graph to be drawn (including the
        //      left gridline) when the distance is zero.
        canvas.drawRect(graphLeftPx - gridLineThickness * (float) 0.5,
                        graphTopPx - gridLineThickness * (float) 0.5,
                        graphLeftPx + gridLineThickness * (float) 0.5,
                      graphBottomPx + gridLineThickness * (float) 0.5,
                        grayPaint);
        String minXString = "0.0";
        canvas.drawText(minXString,
                (float) (graphLeftPx - whitePaint.getTextSize() * 0.47 * (minXString.length()) * 0.5), // centered on gridline
                graphBottomPx + whitePaint.getTextSize() + 10,
                whitePaint);

        // far right gridline also taken care of manually
        canvas.drawRect(graphRightPx - gridLineThickness * (float) 0.5,
                        graphTopPx - gridLineThickness * (float) 0.5,
                        graphRightPx + gridLineThickness * (float) 0.5,
                        graphBottomPx + gridLineThickness * (float) 0.5,
                         grayPaint);
        // handle edge case where distance is zero -- it's nonsensical to label both left and right gridlines "0.0"
        if (maxXAxisValueInCorrectUnits > 0) {
            String maxXString = String.valueOf(Math.round(maxXAxisValueInCorrectUnits * 100) / 100.0); // make maxXString go to 2 decimal places
            canvas.drawText(maxXString,
                    (float) (graphRightPx - whitePaint.getTextSize() * 0.47 * (maxXString.length())), // not centered on gridline like others so it doesn't stick out past graph
                    graphBottomPx + whitePaint.getTextSize() + 10,
                    whitePaint);
        }

        // bottom gridline and all HORIZONTAL gridlines (All y axis increments other than the top and bottom ones).
        // This is different from vertical gridlines because we don't necessarily start at zero. So it's more complex.
        // we start with the following steps:
        //      1. check if minYaxisValueInCorrectUnits is greater than zero.
        //              if it IS greater: start incrementing up from zero until we hit a value within desired min and max to be used as bottom gridline.
        //              if it is NOT greater: start incrementing down until we hit a value less than min. keep the previous one as the bottom gridline.
        //      2. now we have bottom gridline value. Start incrementing up, drawing gridlines and numbers as we go.
        //      3. draw both bottom and top gridlines manually
        double minHorizontalGridlineValueInCorrectUnits = 0; // default, hopefully never used
        if (minYAxisValueInCorrectUnits >= 0) {
            boolean aboveMinY = false;
            int counter = 0;
            while (!aboveMinY) {
                if (counter * yIncrement >= minYAxisValueInCorrectUnits) {
                    aboveMinY = true;
                    minHorizontalGridlineValueInCorrectUnits = counter * yIncrement;
                } else {
                    counter++;
                }
            }
        } else {
            boolean aboveMinY = true;
            int counter = 0;
            while (aboveMinY) {
                if (counter * yIncrement < minYAxisValueInCorrectUnits) {
                    aboveMinY = false;
                    minHorizontalGridlineValueInCorrectUnits = (counter + 1) * yIncrement;
                } else {
                    counter--;
                }
            }
        }
        int lowestGridlinePx = (int) (graphBottomPx - (((minHorizontalGridlineValueInCorrectUnits - minYAxisValueInCorrectUnits) / yAxisRange) * graphHeightPx));

        // actually draw the gridlines and numbers
        for (int i = 0; i <= numHorizontalGridlines; i++) {
            double gridlineValueInCorrectUnits = minHorizontalGridlineValueInCorrectUnits + yIncrement * i;
            // below: "if the gridline currently being drawn isn't too close to the bottom or top gridline,
            //      go ahead and draw it."
            if (gridlineValueInCorrectUnits - minYAxisValueInCorrectUnits >= yIncrement / 2 &&
                maxYAxisValueInCorrectUnits - gridlineValueInCorrectUnits >= yIncrement / 2) {
                float left = (float) (graphLeftPx - gridLineThickness * 0.5);
                float top = (float) (lowestGridlinePx - graphHeightPx * i * yIncrement / yAxisRange - gridLineThickness / 2.0);
                float right = (float) (graphRightPx + gridLineThickness * 0.5);
                float bottom = top + gridLineThickness;
                canvas.drawRect(left, top, right, bottom, grayPaint);

                // handle axis numbering at the same time
                String numberString = String.valueOf(Math.round(gridlineValueInCorrectUnits * 10000) / 10000.0);
                // some fancy calculations for x placement just copied and pasted from above -- just centering the number on the gridline.
                canvas.drawText(numberString,
                        (float) (graphLeftPx - whitePaint.getTextSize() - whitePaint.getTextSize() * numberString.length() * 0.4), // multiply by numberString length to try to get numbers to be the same distance from y axis regardless of number of digits
                        //      above, I also had to decrease the distance from the Y axis because numberString.length by itself was too much.
                        (float) (bottom + whitePaint.getTextSize() * 0.3), // found y adjustment value with some guess and check
                        whitePaint);
            }
        }

        // top horizontal gridline taken care of manually
        canvas.drawRect(graphLeftPx - gridLineThickness * (float) 0.5,
                graphTopPx - gridLineThickness * (float) 0.5,
                graphRightPx + gridLineThickness * (float) 0.5,
                graphTopPx + gridLineThickness * (float) 0.5,
                grayPaint);
        // the if statement below handles the edge case where we are displaying the cumulative distance graph
        //      but there are no rides for this time range. Without this filter, both the top and bottom gridlines
        //      are labeled "0.0", which is nonsensical.
        if (maxYAxisValueInCorrectUnits - minYAxisValueInCorrectUnits != 0) {
            String maxYString = String.valueOf(Math.round(maxYAxisValueInCorrectUnits * 100) / 100.0); // make maxYString go to 4 decimal places
            canvas.drawText(maxYString,
                    (float) (graphLeftPx - whitePaint.getTextSize() - whitePaint.getTextSize() * maxYString.length() * 0.4),
                    (float) (graphTopPx + whitePaint.getTextSize() * 0.6), // found y adjustment value with some guess and check
                    whitePaint);
        }

        // bottom horizontal gridline also taken care of manually
        canvas.drawRect(graphLeftPx - gridLineThickness * (float) 0.5,
                graphBottomPx - gridLineThickness * (float) 0.5,
                graphRightPx + gridLineThickness * (float) 0.5,
                graphBottomPx + gridLineThickness * (float) 0.5,
                grayPaint);
        String minYString = String.valueOf(Math.round(minYAxisValueInCorrectUnits * 10) / 10.0); // make minYString go to 1 decimal place
        canvas.drawText(minYString,
                (float) (graphLeftPx - whitePaint.getTextSize() - whitePaint.getTextSize() * minYString.length() * 0.4),
                graphBottomPx,
                whitePaint);
    }

    // find nice gridline increments for any desired length of x or y axis
    // A very annoying edge case: if you input large enough doubles, greater than 7 digits before the decimal,
    //      the system flips to storing them in scientific notation. So this method breaks for values
    //      greater than 9999999.0. I tracked down the issue but didn't fix it because I don't think it's
    //      an issue if you actually input any of the values this app uses into the method in correct units.
    private double findGridlines(double maxAxisValue, double minAxisValue, double numDesiredGridlines) {
        // STEP 0: find the value we want to test
        double value = maxAxisValue - minAxisValue;

        // STEP 1: make a two digit number out of value we are testing
        String valueString = String.valueOf(value);
        int threeDigitNumber = 0; // rounded to become threeDigitNumber
        int twoDigitNumber; // e.g. 0.056 becomes 56, 48670.35 becomes 49
        int digitsRemaining = 3; // the number of digits still to be added to twoDigitNumber
        int firstNonZeroDigitIndex = 0;
        int decimalIndex = 0;

        // make the 3 digit number out of the original value
        for (int i = 0; i < valueString.length(); i++) {
            char c = valueString.charAt(i);
            if (c == '.') {
                decimalIndex = i;
            } else if (digitsRemaining == 3 && c != '0') {
                firstNonZeroDigitIndex = i;
                digitsRemaining -= 1;
                threeDigitNumber = (c - '0') * 100; // ASCII table arranged so '9' - '0' gives a 9 int difference
            } else if (digitsRemaining == 2) {
                digitsRemaining -= 1;
                threeDigitNumber += (c - '0') * 10;
            } else if (digitsRemaining == 1) {
                digitsRemaining -=  1;
                threeDigitNumber += c - '0';
                // originally I was going to break here for efficiency but it turns out for 3 digit
                //      distances you have to let it keep going so it can actually find the decimalIndex. whoops
            }
        }
        System.out.println("value: " + value);
        System.out.println("value string: " + valueString);
        System.out.println("decimal index: " + decimalIndex + " first nonzero digit index: " + firstNonZeroDigitIndex);

        twoDigitNumber = Math.round(threeDigitNumber / (float) 10.0);
        System.out.println("three digit number: " + threeDigitNumber);
        System.out.println("two digit number: " + twoDigitNumber);


        // STEP 2: find the closest number to twoDigitNumber that has a nice round factor when divided by 4
        boolean isFactorFound = false;
        int jumpToNext = 0;
        // a list of nice round numbers I would like to see as axis increments
        double[] preferredFactors = new double[]{1,  1.25, 1.5, 2,  2.5, 3,  4,  5,  6,  7,  8,  9,
                                                 10, 12.5, 15,  20, 25,  30, 40, 50, 60, 70, 80, 90};
        double gridlineIncrement = 0; // initializing at zero to be sure it gets a value. Although it should 100% of the time...

        while (!isFactorFound) {
            // jump back and forth over the original 2 digit number, getting
            //      farther and farther away as we search for the closest number
            //      that has one of the preferred factors.
            if (jumpToNext % 2 == 1) twoDigitNumber += jumpToNext; // add odd numbers
            else twoDigitNumber -= jumpToNext; // subtract even numbers
            jumpToNext++;

            for (double potentialFactor : preferredFactors) {
                if (twoDigitNumber / numDesiredGridlines == potentialFactor) {
                    isFactorFound = true;
                    gridlineIncrement = potentialFactor;
                    break;
                }
            }
        }
        System.out.println("adjusted two digit number: " + twoDigitNumber);
        System.out.println("gridline increment: " + gridlineIncrement);

        // STEP 3: Scooch gridline increment decimal back to its proper spot (e.g. 5 could go back to 0.05 or 50)
        //      do this by multiplying it by the proper power of 10

        // putting decimal back in the right spot for gridLineIncrement has to be done differently depending
        //      on whether the initial distance was greater or less than 1, due to how the characters end up
        //      being arranged in valueString (e.g. from 1.0 to 0.1, the difference in indices changes by 2)
        int indexDifference = decimalIndex - firstNonZeroDigitIndex;
        if (decimalIndex > firstNonZeroDigitIndex) {
            gridlineIncrement *= Math.pow(10, indexDifference - 2);
        } else {
            gridlineIncrement *= Math.pow(10, indexDifference - 1);
        }
        System.out.println("adjusted gridline increment: " + gridlineIncrement);

        return gridlineIncrement;
    }

    // called by onDraw to determine units for any title or axis I want to graph.
    private String findAxisUnitString(String axisLabel) {

        if (axisLabel.equals(getResources().getString(R.string.time_graph_title))) {
            return getResources().getString(R.string.day_units_parenthesized);
        }

        if (units == App.IMPERIAL_UNITS) {
            // check if it says "Distance"
            if (axisLabel.equals(getResources().getString(R.string.distance_graph_title)) ||
                    axisLabel.equals(getResources().getString(R.string.cumulative_distance_graph_title))) {
                return getResources().getString(R.string.imperial_distance_units_parenthesized);
            }
            // check if it says "Speed"
            else if (axisLabel.equals(getResources().getString(R.string.speed_graph_title))) {
                return getResources().getString(R.string.imperial_speed_units_parenthesized);
            }
            // check if it says "Elevation"
            else if (axisLabel.equals(getResources().getString(R.string.elevation_graph_title))) {
                return getResources().getString(R.string.imperial_elevation_units_parenthesized);
            }
            // can add any other titles to check as needed!
        }
        else {
            // check if it says "Distance"
            if (axisLabel.equals(getResources().getString(R.string.distance_graph_title)) ||
                    axisLabel.equals(getResources().getString(R.string.cumulative_distance_graph_title))) {
                return getResources().getString(R.string.metric_distance_units_parenthesized);
            }
            // check if it says "Speed"
            else if (axisLabel.equals(getResources().getString(R.string.speed_graph_title))) {
                return getResources().getString(R.string.metric_speed_units_parenthesized);
            }
            // check if it says "Elevation"
            else if (axisLabel.equals(getResources().getString(R.string.elevation_graph_title))) {
                return getResources().getString(R.string.metric_elevation_units_parenthesized);
            }
            // can add any other titles to check as needed!
        }
        return ""; // hopefully this never happens
    }

    // maxXAxisValue and maxYAxisValue are already global variables, but they are in meters / mps.
    //      to find nice gridline increments, we need them in the correct units. Hence the function below.
    private double findAxisValueInCorrectUnits(double axisValue, String axisLabel) {

        if (axisLabel.equals(getResources().getString(R.string.time_graph_title))) {
            return UnitConversion.millisToDays(axisValue);
        }

        else if (units == App.IMPERIAL_UNITS) {
            // check if it says "Distance"
            if (axisLabel.equals(getResources().getString(R.string.distance_graph_title)) ||
                    axisLabel.equals(getResources().getString(R.string.cumulative_distance_graph_title))) {
                return UnitConversion.metersToMiles(axisValue);
            }
            // check if it says "Speed"
            else if (axisLabel.equals(getResources().getString(R.string.speed_graph_title))) {
                return UnitConversion.mpsToMph(axisValue);
            }
            // check if it says "Elevation"
            else if (axisLabel.equals(getResources().getString(R.string.elevation_graph_title))) {
                return UnitConversion.metersToFeet(axisValue);
            }
            // can add any other titles to check as needed!
        }
        else {
            // check if it says "Distance"
            if (axisLabel.equals(getResources().getString(R.string.distance_graph_title)) ||
                    axisLabel.equals(getResources().getString(R.string.cumulative_distance_graph_title))) {
                return UnitConversion.metersToKilometers(axisValue);
            }
            // check if it says "Speed"
            else if (axisLabel.equals(getResources().getString(R.string.speed_graph_title))) {
                return UnitConversion.mpsToKph(axisValue);
            }
            // check if it says "Elevation"
            else if (axisLabel.equals(getResources().getString(R.string.elevation_graph_title))) {
                return axisValue;
            }
            // can add any other titles to check as needed!
        }
        return 0; // hopefully this never happens
    }

/*
    // example below is for dynamic adjustments to view appearance.
    //      I may not need it, but it's good to have in my back pocket.
    public int getLineColor() {
        return lineColor;
    }
    // also part of dynamic view changes example
    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
        // below two are apparently v important
        invalidate();
        requestLayout();
    }
 */
}
