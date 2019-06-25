/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class is causing crashes and needs to be fixed. Sometimes the socket is closed before the reliability/fingerprint
 * exchange. It may be because Threads/Sockets are not closed properly.
 */

public class BluetoothManager {

    Context context;

    private BluetoothAdapter adapter;
    private AcceptThread acceptThread;
    private List<ConnectThread> connectThreads;
    private ArrayList<BluetoothDevice> deviceList;
    private int failedConnectionCount;
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private BluetoothSocket connectedSocket; // Successfully connected socket
    private boolean isConnected;
    boolean isServer;
    private boolean registered;
    private final BroadcastReceiver mReceiver1;

    public void close() {
        if (acceptThread != null) {
            acceptThread.cancel();
        }
        if (connectThreads.size() > 0) {
            for (ConnectThread ct : connectThreads) {
                ct = null;
            }
        }
        if (connectedSocket != null) {
            try {
                connectedSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            connectedSocket = null;
        }
        isConnected = false;
        registered = false;

        //just to make sure
        if (registered) {
            context.unregisterReceiver(mReceiver1);
            registered = false;
        }
    }

    public BluetoothManager(Context context) {
        this.context = context;
        adapter = BluetoothAdapter.getDefaultAdapter();
        connectThreads = Collections.synchronizedList(new ArrayList<ConnectThread>());
        deviceList = new ArrayList<>();
        failedConnectionCount = 0;
        connectedSocket = null;
        isConnected = false;
        isServer = false;
        /** Discovers devices around and puts them into the device list */
        mReceiver1 = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction(); //may need to chain this to a recognizing function
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    /** Get the BluetoothDevice object from the Intent */
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    //Log.d("BluetoothService",device.getName());
                    deviceList.add(device);
                }
                /** Start discovery process again until a connection is found */
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if (deviceList.size() == 0) {
                        adapter.startDiscovery();
                    } else {
                        sendConnectionRequest(deviceList);
                    }
                }
                /** first look for paired devices..speed up the process */
                else if (Constants.ACTION_PAIRED_FOUND.equals(action)) {
                    if (deviceList.size() > 0) {
                        sendConnectionRequest(deviceList);
                    }
                }
            }
        };
    }

    public void startConnection() {


//        final Intent intent = new Intent(Constants.ACTION_UPDATE_NOTIFICATION);
////        intent.putExtra("NotificationText", "Looking for devices");
//        intent.putExtra(Constants.EXTRA_PROTOCOL_STATE, MainService.ProtocolState.BLUETOOTH_DISCOVER);
//        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
//        context.sendBroadcast(intent);
        EventBus.getDefault().post(new MessageEvent(MessageEvent.ProtocolState.BLUETOOTH_DISCOVER, null));


        /** Start a separate thread to listen for connection requests */
        acceptThread = new AcceptThread();
        acceptThread.start();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(Constants.ACTION_PAIRED_FOUND);
        HandlerThread receiverThread = new HandlerThread("Receiver thread");
        receiverThread.start();
        Handler receiverHandler = new Handler(receiverThread.getLooper());

        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();

        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        /** Start discovering the devices around */
        if (bondedDevices.size() > 0) {
            for (BluetoothDevice bd : bondedDevices) {
                deviceList.add(bd);
                Log.d("PAIRED", "device added ");
            }
        }
        adapter.startDiscovery();
        if (!registered) {
            context.registerReceiver(mReceiver1, filter, null, receiverHandler);
            registered = true;
            if (bondedDevices.size() > 0) {
                final Intent pintent = new Intent(Constants.ACTION_PAIRED_FOUND);
                context.sendBroadcast(pintent);
            }
        }

    }

    /**
     * Server thread, listens for connection requests
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {

            BluetoothServerSocket tmp = null;

            try {
                /** MY_UUID is the app's UUID string, also used by the client code */
                tmp = adapter.listenUsingInsecureRfcommWithServiceRecord("Bandana", MY_UUID_INSECURE);

            } catch (IOException e) {
                e.printStackTrace();
            }

            mmServerSocket = tmp;
        }

        public synchronized void run() {
            BluetoothSocket socket = null;
            /** Keep listening until exception occurs or a socket is returned */
            while (!isInterrupted()) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                if (socket != null && !isConnected) {
                    /** A connection was accepted. Send an intent to main activity to start sensor reading */
                    connectedSocket = socket;
                    isServer = true;
                    connect();
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Client thread, sends connection request to discovered devices
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
        }

        public synchronized void run() {
            BluetoothSocket tmp = null;

            try {
                tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmSocket = tmp;

            try {
                mmSocket.connect();

                /** Connection successful, get the socket and output stream to send reliability */
                if (!isConnected) {
                    connectedSocket = mmSocket;
                    connect();
                }

            } catch (IOException e) {
                failedConnectionCount++;

                if (failedConnectionCount == connectThreads.size()) {
                    failedConnectionCount = 0;
                    connectThreads.clear();
                    deviceList.clear();
                    adapter.startDiscovery();
                }

                return;
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * When the discovery is completed, sends a connection request to each of the devices in the device list
     */
    private void sendConnectionRequest(ArrayList<BluetoothDevice> deviceList) {
        for (BluetoothDevice device : deviceList) {
            ConnectThread connectThread = new ConnectThread(device);
            connectThread.start();
            connectThreads.add(connectThread);
        }
    }

    /**
     * Called when the device is successfully connected
     */
    private void connect() {
        /** Send an intent to main activity to start sensor reading */
        final Intent intent = new Intent(Constants.ACTION_CONNECTED);
        context.sendBroadcast(intent);

        isConnected = true;
        acceptThread.interrupt();
        //acceptThread.cancel();
        adapter.cancelDiscovery();

        if (registered) {
            context.unregisterReceiver(mReceiver1);
            registered = false;
        }
    }

    /**
     * Read the reliability array sent by the connected device
     */
    public ArrayList<Double> getReliability() {
        ArrayList<Double> reliability = new ArrayList<>();
        InputStream inStream = null;
        try {
            inStream = connectedSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] mmBuffer = new byte[8];

        /** Reads reliability values until the value -1.0 is read */
        while (true) {
            try {
                inStream.read(mmBuffer);
                double relValue = ByteBuffer.wrap(mmBuffer).getDouble();

                if (relValue == -1.0)
                    break;

                reliability.add(relValue);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        return reliability;
    }

    /**
     * Send the reliability array to the connected device
     */
    public void sendReliability(ArrayList<Double> reliability) {

        OutputStream outStream = null;
        try {
            outStream = connectedSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            for (double relValue : reliability) {
                outStream.write(ByteBuffer.allocate(8).putDouble(relValue).array());
            }

            outStream.write(ByteBuffer.allocate(8).putDouble(-1.0).array());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Read the fingerprint array sent by the connected device
     */
    public ArrayList<Integer> getFingerprint() {
        ArrayList<Integer> fingerprint = new ArrayList<>();
        InputStream inStream = null;
        try {
            inStream = connectedSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] mmBuffer = new byte[4];

        /** Reads fingerprint values until the value -1.0 is read */
        while (true) {
            try {
                inStream.read(mmBuffer);
                int fpBit = ByteBuffer.wrap(mmBuffer).getInt();

                if (fpBit == -1.0)
                    break;

                fingerprint.add(fpBit);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        return fingerprint;
    }

    /**
     * Send the fingerprint array to the connected device
     */
    public void sendFingerprint(ArrayList<Integer> fingerprint) {

        OutputStream outStream = null;
        try {
            outStream = connectedSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            for (int fpBit : fingerprint) {
                outStream.write(ByteBuffer.allocate(4).putInt(fpBit).array());
            }

            outStream.write(ByteBuffer.allocate(4).putInt(-1).array());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

