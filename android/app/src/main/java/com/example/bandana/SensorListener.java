/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class SensorListener implements SensorEventListener {

    Context context;
    int duration;

    private SensorManager sensorManager;
    private Sensor sensorMag;
    private Sensor sensorGravity;
    private Sensor sensorAcc;
    private Sensor sensorGyr;

    int curSampleCount = 0;
    long beginTime = -1;

    private float[] gravityValues = null;
    private float[] magneticValues = null;
    private float[] gyroValues = null;

    File rotatedFile;
    File rawFile;

    FileOutputStream rotatedStream;
    FileOutputStream rawStream;

    OutputStreamWriter rotatedWriter;
    OutputStreamWriter rawWriter;

    public SensorListener(Context context, int duration) {
        this.context = context;
        this.duration = duration;
    }

    public void listen() {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorGyr = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        HandlerThread mSensorThread = new HandlerThread("Listener Thread");
        mSensorThread.start();
        Handler mSensorHandler = new Handler(mSensorThread.getLooper());

        rotatedFile = new File(context.getFilesDir(), "rotatedData");
        rawFile = new File(context.getFilesDir(), "sensorData");

        try {
            rotatedFile.createNewFile();
            rawFile.createNewFile();

            rotatedStream = context.openFileOutput("rotatedData", Context.MODE_PRIVATE);
            rotatedWriter = new OutputStreamWriter(rotatedStream);

            rawStream = context.openFileOutput("sensorData", Context.MODE_PRIVATE);
            rawWriter = new OutputStreamWriter(rawStream);

        } catch (IOException e) {
            Log.e("Exception", "File write failed: ", e);
        }

        beginTime = System.currentTimeMillis();

        sensorManager.registerListener(this, sensorMag, 20000, mSensorHandler);
        sensorManager.registerListener(this, sensorGravity, 20000, mSensorHandler);
        sensorManager.registerListener(this, sensorAcc, 20000, mSensorHandler);
        sensorManager.registerListener(this, sensorGyr, 20000, mSensorHandler);
    }

    public void onSensorChanged(SensorEvent event) {

        if ((gravityValues != null) && (magneticValues != null) && (gyroValues != null)
                && (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)) {


            long time = System.currentTimeMillis();

            if (time >= beginTime + duration * 1000) {
                stopSensor();
            } else {

                if (curSampleCount % 3000 == 0) {
                    // intent.putExtra("NotificationText",Integer.toString(curSampleCount / 50) + "/ " + duration);

                    EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.WALKING, null));
                }

                float[] deviceRelativeAcceleration = new float[4];
                deviceRelativeAcceleration[0] = event.values[0];
                deviceRelativeAcceleration[1] = event.values[1];
                deviceRelativeAcceleration[2] = event.values[2];
                deviceRelativeAcceleration[3] = 0;

                /* Change the device relative acceleration values to earth relative values
                    X axis -> East
                    Y axis -> North Pole
                    Z axis -> Sky
                */

                float[] R = new float[16];
                float[] I = new float[16];
                float[] earthAcc = new float[16];

                SensorManager.getRotationMatrix(R, I, gravityValues, magneticValues);

                float[] inv = new float[16];

                android.opengl.Matrix.invertM(inv, 0, R, 0);
                android.opengl.Matrix.multiplyMV(earthAcc, 0, inv, 0, deviceRelativeAcceleration, 0);

//                Log.d(Constants.TAG, "earthAcc[2] " + earthAcc[2]);


                try {
                    rotatedWriter.append(time + ",");
                    rotatedWriter.append(earthAcc[2] + "\n");

                    rawWriter.append(time + ",");
                    rawWriter.append(deviceRelativeAcceleration[0] + "," + deviceRelativeAcceleration[1] + "," + deviceRelativeAcceleration[2] + ",");
                    rawWriter.append(gyroValues[0] + "," + gyroValues[1] + "," + gyroValues[2] + "\n");

                } catch (IOException e) {
                    e.printStackTrace();
                }

                curSampleCount++;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravityValues = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroValues = event.values;
        }
    }

    private void stopSensor() {
        sensorManager.unregisterListener(this);

        try {
            rotatedWriter.close();
            rotatedStream.flush();
            rotatedStream.close();

            rawWriter.close();
            rawStream.flush();
            rawStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        final Intent intent = new Intent(Constants.ACTION_SENSOR_COMPLETED);
        context.sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
