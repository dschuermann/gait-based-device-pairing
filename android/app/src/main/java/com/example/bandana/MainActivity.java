/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 42;
    private static String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private static final int REQUEST_BLUETOOTH = 43;


    final static File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Values/");

    TextView textView;
    ImageView imageView;
    TextView lastResultTextView;
    ImageView lastResultImageView;

    class AnimationTask extends AsyncTask<MessageEvent.ProtocolState, Integer, Integer> {
        MessageEvent.ProtocolState state;

        public AnimationTask(MessageEvent.ProtocolState state) {
            this.state = state;
        }

        @Override
        protected Integer doInBackground(MessageEvent.ProtocolState... protocolStates) {
            while (!isCancelled()) {
                publishProgress(protocolStates[0].drawableId1);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                publishProgress(protocolStates[0].drawableId2);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            imageView.setImageResource(values[0]);
        }
    }

    AnimationTask task;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        String text = event.value != null ? getString(event.state.stringId, event.value) : getString(event.state.stringId);

        if (event.state == MessageEvent.ProtocolState.BLOCK
                || event.state == MessageEvent.ProtocolState.SECURE) {
            lastResultTextView.setText(text);
            lastResultImageView.setImageResource(event.state.drawableId1);
        } else {
            textView.setText(text);
            //imageView.setImageResource(event.state.drawableId1);

            if (task != null)
                task.cancel(true);
            task = new AnimationTask(event.state);
            task.execute(event.state);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        textView = findViewById(R.id.textField);
        imageView = findViewById(R.id.imageView);
        lastResultTextView = findViewById(R.id.lastResultText);
        lastResultImageView = findViewById(R.id.lastResultImage);

        int hasStoragePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int hasLocationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        boolean hasPermissions = hasStoragePermission == PackageManager.PERMISSION_GRANTED &&
                hasLocationPermission == PackageManager.PERMISSION_GRANTED;

        if (hasPermissions) {
            startBandana();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    REQUESTED_PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        boolean allGranted = true;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                if (allGranted) {
                    startBandana();
                } else {
                    Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    private void startBandana() {

        if (!path.exists()) {
            path.mkdirs();
        }

        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, 0);

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean bluetoothSupported = btAdapter != null;
        if (!bluetoothSupported) {
            Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (btAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            // Send the user a request to make the device discoverable
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, REQUEST_BLUETOOTH);
        } else {
            Intent intent = new Intent(this, MainService.class);
            startService(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BLUETOOTH && resultCode != Activity.RESULT_CANCELED) {
            Log.d(Constants.TAG, "bluetooth discoverability activated");

            Intent intent = new Intent(this, MainService.class);
            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(this, MainService.class);
        stopService(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
}
