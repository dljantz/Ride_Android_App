package com.jantztechnologies.ride;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class FileIO {

    private static final String rideFolderPrefix = "ride_folder_"; // there is an identical string in strings, but since this
            //      class does not extend an android object, it doesn't have access to getResources().getString(). There's probably
            //      a way around that, but why use good programming when it's faster to just hard code the same thing multiple places amirite

    public static String createNewRideFolder(Context context) {
        int suffix = 1 + getLastRideFolderSuffix(context); // add one so we don't overwrite the last ride
        String folderName = rideFolderPrefix + suffix;
        File newFolder = new File(context.getFilesDir(), folderName);
        if (newFolder.mkdir()) System.out.println("new ride folder created: " + folderName);

        return folderName;
    }

    public static boolean createSettingsFolder(Context context) {
        File appDirectory = context.getFilesDir();
        // check existing filenames in directory
        String[] appFiles = appDirectory.list();

        if (appFiles != null) {
            for (String fileName : appFiles) {
                if (fileName.equals(context.getResources().getString(R.string.settings_folder_name))) {
                    return false; // settings folder already created!!
                }
            }
            File newFolder = new File(appDirectory, context.getResources().getString(R.string.settings_folder_name));
            if (newFolder.mkdir()) { // hopefully always true

                saveInt(context,
                        context.getResources().getString(R.string.settings_folder_name),
                        context.getResources().getString(R.string.autopause_setting_filename),
                        App.AUTOPAUSE_ENABLED); // default is to enable autopause
                saveInt(context,
                        context.getResources().getString(R.string.settings_folder_name),
                        context.getResources().getString(R.string.keep_screen_on_setting_filename),
                        App.KEEP_SCREEN_ON_DURING_RIDES_DISABLED); // default is allow screen to turn off when recording
                saveInt(context,
                        context.getResources().getString(R.string.settings_folder_name),
                        context.getResources().getString(R.string.map_type_setting_filename),
                        App.MAP_STYLE_DARK); // default map type is dark mode.
                saveInt(context,
                        context.getResources().getString(R.string.settings_folder_name),
                        context.getResources().getString(R.string.distance_units_setting_filename),
                        App.IMPERIAL_UNITS); // default is imperial
                saveLong(context,
                        context.getResources().getString(R.string.settings_folder_name),
                        context.getResources().getString(R.string.last_launch_timestamp_filename),
                        System.currentTimeMillis());
            }
        }
        return true; // signal that settings folder was created
    }

    public static boolean deleteFolder(Context context, String folderName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, folderName);
        try {
            for (File file : rideFolder.listFiles()) {
                if (file.delete()) System.out.println("file deleted from " + rideFolder + ": " + file.toString());
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        // only returns true if the folder was emptied and deleted successfully
        return rideFolder.delete();
    }

    // called in StatisticsActivity
    public static int getRideFolderNumber(String rideFolderName) {
        return Integer.parseInt(rideFolderName.substring(rideFolderPrefix.length()));
    }

    // called when creating the next ride folder and when iterating backwards through rides
    //      to display ride stats in the different time range fragments in StatisticsActivity
    public static int getLastRideFolderSuffix(Context context) {
        File appDirectory = context.getFilesDir();
        // check existing filenames to get next number
        String[] appFiles = appDirectory.list();
        int suffix = 0;
        if (appFiles != null) {
            for (String fileName : appFiles) {
                try {
                    if (fileName.startsWith(rideFolderPrefix)) {
                        int testFolderNumber = getRideFolderNumber(fileName); // TODO: changed this
                        if (testFolderNumber >= suffix)
                            suffix = testFolderNumber + 1;// should work when we get into ride10, ride1000, etc.
                    }
                } catch (NumberFormatException e) {
                    // hopefully this never happens. just do nothing...
                    //      I guess that means ride 0 will get overwritten
                    e.printStackTrace();
                }
            }
        }
        return suffix - 1; // have to subtract one because the for loop produces a number one greater than the highest ride folder number
    }

    // used to generate the list of rides in MainActivity
    public static ArrayList<String> getRideFolderNames(Context context) {
        File appDirectory = context.getFilesDir();
        String[] appFiles = appDirectory.list();
        ArrayList<String> rideFolderNames = new ArrayList<>();

        try {
            for (String fileName : appFiles) {
                if (fileName.startsWith(rideFolderPrefix)) rideFolderNames.add(fileName);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        // used to be a Comparator calling its compare() method, but android studio suggested lambda instead
        Collections.sort(rideFolderNames, (o1, o2) -> {
            Integer o1Int = Integer.parseInt(o1.substring(rideFolderPrefix.length()));
            Integer o2Int = Integer.parseInt(o2.substring(rideFolderPrefix.length()));
            return o2Int.compareTo(o1Int); // switched around because I want most recent rides at the top
        });
        return rideFolderNames;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<Integer> loadArrayListIntegers(Context context, String subfolder, String fileName) {
        ArrayList<Integer> integerArrayList = null;

        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            integerArrayList = (ArrayList<Integer>) ois.readObject();

            ois.close();
            bis.close();
            fis.close();


        } catch (ClassCastException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return integerArrayList;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<SerializableLocation> loadArrayListLocations(Context context, String subfolder, String fileName) {
        ArrayList<SerializableLocation> locations = null;

        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            locations = (ArrayList<SerializableLocation>) ois.readObject();

            ois.close();
            bis.close();
            fis.close();

        } catch (ClassCastException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return locations;
    }

    public static Bitmap loadBitmap(Context context, String subfolder, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);
        Bitmap image = null;

        try {
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                ObjectInputStream ois = new ObjectInputStream(bis);

                // based on https://www.semicolonworld.com/question/49202/serializing-and-de-serializing-android-graphics-bitmap-in-java
                int bufferLength = ois.readInt();
                byte[] byteArray = new byte[bufferLength];

                // got this from the website above. APPARENTLY, ois.read() has a FUCKING LIMIT to the
                //      number of bytes it can read at a time. It seems to only read 1024 bytes per method
                //      call. Hence the ridiculous do-while loop below -- it's necessary to
                //      call read() over and over to get all the data out of the inputStream into the
                //      byte array. Sheesh.
                int position = 0;
                do {
                    int numBytesRead = ois.read(byteArray, position, bufferLength - position);
                    if (numBytesRead != -1) {
                        position += numBytesRead;
                    } else {
                        break;
                    }

                } while (position < bufferLength);

                image = BitmapFactory.decodeByteArray(byteArray, 0, bufferLength);

                ois.close();
                bis.close();
                fis.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return image;
    }

    public static int loadInt(Context context, String subfolder, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);
        int value = -1; // default value is a signal that something went sideways

        try {
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis);
                value = dis.readInt();
                dis.close();
                fis.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return value;
    }

    public static long loadLong(Context context, String subfolder, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);
        long value = -1; // default value is a signal that something went sideways

        try {
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(fis);
                value = dis.readLong();
                dis.close();
                fis.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return value;
    }

    public static RideStats loadRideStats(Context context, String subfolder, String fileName) {
        RideStats rideStats = null;

        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        ///////////System.out.println("absolute path: " + file.getAbsolutePath());

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            rideStats = (RideStats) ois.readObject();

            ois.close();
            bis.close();
            fis.close();

        } catch (ClassCastException | IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return rideStats;
    }

    public static void saveArrayListIntegers(Context context, String subfolder, ArrayList<Integer> integers, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(integers);

            oos.close();
            bos.close();
            fos.close();

        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static void saveArrayListLocations(Context context, String subfolder, ArrayList<SerializableLocation> locations, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(locations);

            oos.close();
            bos.close();
            fos.close();

        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static void saveBitmap(Context context, String subfolder, String fileName, Bitmap image) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            // based on https://www.semicolonworld.com/question/49202/serializing-and-de-serializing-android-graphics-bitmap-in-java
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] byteArray = baos.toByteArray();

            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(bos);

            oos.writeInt(byteArray.length);
            oos.write(byteArray);

            oos.close();
            bos.close();
            fos.close();
            // no need to close byte array output stream, apparently

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveInt(Context context, String subfolder, String fileName, int integer) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            FileOutputStream fos = new FileOutputStream(file);
            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeInt(integer);
            dos.close();
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveLong(Context context, String subfolder, String fileName, long number) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            FileOutputStream fos = new FileOutputStream(file);
            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeLong(number);
            dos.close();
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveRideStats(Context context, String subfolder, RideStats rideStats, String fileName) {
        File appDirectory = context.getFilesDir();
        File rideFolder = new File(appDirectory, subfolder);
        File file = new File(rideFolder, fileName);

        try {
            if (file.createNewFile()) System.out.println("file " + fileName + " created.");

            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(rideStats);

            oos.close();
            bos.close();
            fos.close();

        } catch (IOException i) {
            i.printStackTrace();
        }
    }
}
