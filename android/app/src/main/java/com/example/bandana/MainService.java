/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;

import uk.me.berndporr.iirj.ChebyshevII;

public class MainService extends Service {

    Context context;
    int mStartMode;
    IBinder mBinder;
    boolean mAllowRebind;

    /**
     * Booleans for testing
     */
    boolean dataExists = false; // True if pre-collected data will be used instead of sensor data
    boolean useBluetooth = true; //True if bluetooth is going to be used
    boolean sendReliability = true; // True if reliability will be sent

    BluetoothManager bManager;

    private final int NUMBER_OF_GAIT_CYCLES = 12; // The number of gait cycles to be extracted for one fingerprint
    private final int BITS_PER_CYCLE = 4; // Number of bits to be generated from every cycle
    private final int FP_DURATION = 18; // The duration of sensor data to be processed for one fingerprint
    private final int TOTAL_DURATION = 18; // Total duration of sensor reading
    private final int OFFSET = 9; // The offset to be shifted to get the next slice of sensor readings

    Intent notificationIntent;
    PendingIntent pendingIntent;
    PowerManager.WakeLock wakeLock;

    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private boolean registered;

    private final static File PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Values/");

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    private void startForeground() {
        String channelId = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel("my_service", "My Background Service");
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            channelId = "";
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.ic_walk_black_48dp)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(101, notification);
    }

    @Override
    public void onCreate() {
        context = this;

        EventBus.getDefault().register(this);

        // Make the service foreground
        notificationIntent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        startForeground();

        // Acquire wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bandana:wakelock");
        wakeLock.acquire();

        IntentFilter filter = new IntentFilter(Constants.ACTION_CONNECTED);
        filter.addAction(Constants.ACTION_SENSOR_COMPLETED);
        registerReceiver(mReceiver, filter);
        registered = true;


        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        bManager = new BluetoothManager(this);

        // Start bluetooth connection
        if (useBluetooth) {
            bManager.startConnection();
        }
        // Start reading sensor data
        else {
            readSensor();
        }
    }

    /**
     * The service is starting, due to a call to startService()
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return mStartMode;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Constants.ACTION_CONNECTED.equals(action)) {
                // Sent by the BluetoothManager after successful connection
                EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.BLUETOOTH_CONNECTED, null));

                readSensor();
            } else if (Constants.ACTION_SENSOR_COMPLETED.equals(action)) {
                // Sent by the SensorListener after sensor reading is completed
                processData();
            }
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        String text = event.value != null ? getString(event.state.stringId, event.value) : getString(event.state.stringId);


        Notification notification = new Notification.Builder(context)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_walk_black_48dp)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.ticker_text))
                .build();

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1212, notification);
    }

    private void readSensor() {
        // Start sensor listening
        if (!dataExists) {
            SensorListener sensorListener = new SensorListener(this, TOTAL_DURATION);
            sensorListener.listen();
        }

        // Read sensor data from file for testing
        else {
            processData();
        }
    }

    private void processData() {
        ArrayList<Double> otherReliability = null;
        boolean continueBandana = false;


        //this loop goes through only one iteration on current setting
        for (int i = 0; i < (TOTAL_DURATION - FP_DURATION) / OFFSET + 1; i++) {
            continueBandana = false; //for next loop if that happened some day

            ArrayList<Double> rotatedData = new ArrayList<>();

            // This is how the LinearAcceleration class is used
            /*ArrayList<ArrayList<Double>> accData = new ArrayList<>();
            ArrayList<ArrayList<Double>> gyroData = new ArrayList<>();
            ArrayList<Long> timestamps = new ArrayList<>();
            readSliceRaw(accData, gyroData, timestamps, OFFSET * i, FP_DURATION);
            LinearAcceleration linearAcceleration = new LinearAcceleration();
            rotatedData = linearAcceleration.calculateClean(accData, gyroData, timestamps);*/

            // Getting Android's rotated results
            readSliceRotated(rotatedData, OFFSET * i, FP_DURATION);

            Log.d(Constants.TAG, "rotatedData " + rotatedData);

            // Filter the data
//            Filter filter = new Filter();
//            ArrayList<Double> filteredValues = new ArrayList<>(filter.chebyBandpass(rotatedData));

            ChebyshevII chebyshevII = new ChebyshevII();

            ///center freq:
            // https://electronics.stackexchange.com/a/234976
            //sqrt(12/0.5) = 4.898979485566356
            //0.5*4.8990 = 2.4495

//            chebyshevII.bandPass(5, 50, 2.4495, 11.5 / 2.0, 1);
            chebyshevII.highPass(5, 50, 0.5, 10);
            ArrayList<Double> filteredValues = new ArrayList<>();
            for (Double val : rotatedData) {
                filteredValues.add(chebyshevII.filter(val));
            }

            Log.d(Constants.TAG, "filteredValues " + filteredValues);

            GaitCycleDetection detection = new GaitCycleDetection(filteredValues, NUMBER_OF_GAIT_CYCLES, 40, 0);

            ArrayList<ArrayList<Double>> gaitSequence = detection.detectCycles();
            Log.d(Constants.TAG, "gaitSeq: " + gaitSequence);

            // Generate fingerprints and reliabilities
            Quantization quantization = new Quantization(gaitSequence, BITS_PER_CYCLE);

            quantization.generateFingerprint();

            ArrayList<Integer> fingerprint = new ArrayList<>(quantization.getFingerprint());
            ArrayList<Double> reliability = new ArrayList<>(quantization.getReliability());
            Log.d(Constants.TAG, "fingerprint: " + fingerprint);
            Log.d(Constants.TAG, "reliability: " + reliability);

            // Exchange Reliabilities
            if (sendReliability && fingerprint.size() > 0 && reliability.size() > 0) {
                bManager.sendReliability(reliability);
                otherReliability = bManager.getReliability();

                if (otherReliability == null || otherReliability.size() == 0) {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.ERROR, "otherReliability null"));
                    break;
                }

                if (reliability.hashCode() < otherReliability.hashCode()) {
                    reliability = otherReliability;
                    Log.d(Constants.TAG, "otherReliability used!");
                }
                Log.d(Constants.TAG, "reliability.size(): " + reliability.size());
                Log.d(Constants.TAG, "fingerprint.size(): " + fingerprint.size());

                if (reliability.size() > fingerprint.size()) {
                    Log.d(Constants.TAG, "reliability was larger than fingerprint! reliability cutted down");
                    reliability = new ArrayList<>(reliability.subList(0, fingerprint.size()));
                }

                if (fingerprint.size() > reliability.size()) {
                    Log.d(Constants.TAG, "fingerprint was larger than reliability! fingerprint cutted down");
                    fingerprint = new ArrayList<>(fingerprint.subList(0, reliability.size()));
                }

                Log.d(Constants.TAG, "fingerprint.size(): " + fingerprint.size());

                if (fingerprint.size() < 32) {
                    toneGenerator.stopTone();
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.BLOCK, "fingerprint.size() < 32"));
                    continueBandana = true;
                    break;
                }

                // Sort fingerprint according to reliability
                ArrayList<Integer> sortedFp = new ArrayList<Integer>(quantization.sortFingerprint(fingerprint, reliability, 32));
                bManager.sendFingerprint(sortedFp);
                ArrayList<Integer> otherSortedFp = bManager.getFingerprint();

                if (otherSortedFp == null || otherSortedFp.size() == 0) {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.ERROR, "otherSortedFp null"));
                    break;
                }

                double similarity = compareFingerprints(sortedFp, otherSortedFp);

                if (similarity >= 0.70) {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.SECURE, Double.toString(similarity)));

//                    toneGenerator.stopTone();
                    // toneGenerator.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING);
//                    vibrator.vibrate(200);
                    toneGenerator.stopTone();
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
                } else {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.BLOCK, "Similarity: " + Double.toString(similarity)));

                    toneGenerator.stopTone();
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
//                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
                    // vibrator.vibrate(2000);
                }

                continueBandana = true;
            } else {
                EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.FAIL, null));
            }
        }

        if (wakeLock.isHeld())
            wakeLock.release();

//        try {
//            TimeUnit.SECONDS.sleep(10);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        toneGenerator.stopTone();
        // stopSelf(); // stop the service after one round
        dataExists = false;
        // check previous similarity to increase speed / recognition rate, or delta_similarity (current - previous)
        if (continueBandana) {
            readSensor();
        } else {
            //do not process further..wait for user to close application
//            Toast.makeText(context, "Error while processing the sensor data\nclose app", Toast.LENGTH_SHORT).show();
//            Log.d("MAIN_SERVICE", "Problem while processing");
//
//            EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.FAIL, null));

        }
    }

    private double compareFingerprints(ArrayList<Integer> fp1, ArrayList<Integer> fp2) {
        int equalBitCount = 0;

        for (int i = 0; i < fp1.size(); i++) {
            if (fp1.get(i).equals(fp2.get(i))) {
                equalBitCount++;
            }
        }

        return (double) equalBitCount / fp1.size();
    }

    private String fingerprintToString(ArrayList<Integer> fingerprint) {
        String fpString = "";

        for (int i : fingerprint) {
            fpString = fpString.concat(Integer.toString(i));
        }

        return fpString;
    }

    private void readSliceRotated(ArrayList<Double> accData, int offset, int size) {

        BufferedReader br = null;
        FileReader fr = null;

        try {
            File file = new File(getFilesDir(), "rotatedData");
            fr = new FileReader(file);
            br = new BufferedReader(fr);

            String sCurrentLine;

            sCurrentLine = br.readLine();
            StringTokenizer st = new StringTokenizer(sCurrentLine, ",");
            long beginTime = Long.parseLong(st.nextElement().toString());
            long curTime = beginTime;

            while (curTime < beginTime + offset * 1000 && sCurrentLine != null) {
                sCurrentLine = br.readLine();
                st = new StringTokenizer(sCurrentLine, ",");
                curTime = Long.parseLong(st.nextElement().toString());
            }

            beginTime = curTime;


            if (sCurrentLine == null) {
                return;
            }

            while (curTime < beginTime + size * 1000 && sCurrentLine != null) {

                st = new StringTokenizer(sCurrentLine, ",");
                Long timestamp = Long.parseLong(st.nextElement().toString());
                accData.add(Double.parseDouble(st.nextElement().toString()));
                curTime = timestamp;
                sCurrentLine = br.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            try {
                if (br != null)
                    br.close();

                if (fr != null)
                    fr.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void readSliceRaw(ArrayList<ArrayList<Double>> accData, ArrayList<ArrayList<Double>> gyroData, ArrayList<Long> timestamps, int offset, int size) {

        BufferedReader br = null;
        FileReader fr = null;

        try {
            File file = new File(getFilesDir(), "sensorData");
            fr = new FileReader(file);
            br = new BufferedReader(fr);

            String sCurrentLine;

            sCurrentLine = br.readLine();
            StringTokenizer st = new StringTokenizer(sCurrentLine, ",");
            long beginTime = Long.parseLong(st.nextElement().toString());
            long curTime = beginTime;

            while (curTime < beginTime + offset * 1000 && sCurrentLine != null) {
                sCurrentLine = br.readLine();
                st = new StringTokenizer(sCurrentLine, ",");
                curTime = Long.parseLong(st.nextElement().toString());
            }

            beginTime = curTime;

            if (sCurrentLine == null) {
                return;
            }

            while (curTime < beginTime + size * 1000 && sCurrentLine != null) {
                st = new StringTokenizer(sCurrentLine, ",");

                Long timestamp = Long.parseLong(st.nextElement().toString());
                timestamps.add(timestamp);

                ArrayList<Double> accSample = new ArrayList<>();
                ArrayList<Double> gyroSample = new ArrayList<>();

                accSample.add(Double.parseDouble(st.nextElement().toString()));
                accSample.add(Double.parseDouble(st.nextElement().toString()));
                accSample.add(Double.parseDouble(st.nextElement().toString()));
                accData.add(accSample);

                gyroSample.add(Double.parseDouble(st.nextElement().toString()));
                gyroSample.add(Double.parseDouble(st.nextElement().toString()));
                gyroSample.add(Double.parseDouble(st.nextElement().toString()));
                gyroData.add(gyroSample);

                curTime = timestamp;
                sCurrentLine = br.readLine();
            }


        } catch (IOException e) {
            e.printStackTrace();

        } finally {

            try {

                if (br != null)
                    br.close();

                if (fr != null)
                    fr.close();

            } catch (IOException ex) {

                ex.printStackTrace();

            }

        }
    }

    private void writeToExternalDirectory(String readFileName, String writeFileName) {
        final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Values/");
        final File writeFile = new File(path, writeFileName);
        FileOutputStream fOut = null;
        OutputStreamWriter myOutWriter = null;

        File readFile = new File(getFilesDir(), readFileName);
        FileReader fr = null;
        BufferedReader br = null;

        try {
            fr = new FileReader(readFile);
            br = new BufferedReader(fr);

            writeFile.createNewFile();
            fOut = new FileOutputStream(writeFile);
            myOutWriter = new OutputStreamWriter(fOut);

            String sCurrentLine;

            while ((sCurrentLine = br.readLine()) != null) {
                myOutWriter.append(sCurrentLine + "\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                fr.close();
                myOutWriter.close();
                fOut.flush();
                fOut.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Called when The service is no longer used and is being destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        bManager.close();
        if (registered) {
            registered = false;
            unregisterReceiver(mReceiver);
        }

        EventBus.getDefault().unregister(this);
    }

    /**
     * A client is binding to the service with bindService()
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Called when all clients have unbound with unbindService()
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return mAllowRebind;
    }

    /**
     * Called when a client is binding to the service with bindService()
     */
    @Override
    public void onRebind(Intent intent) {

    }
}
