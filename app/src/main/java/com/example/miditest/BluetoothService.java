package com.example.miditest;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {
    private final static String TAG = BluetoothService.class.getSimpleName();
    public static final String ACTION_DATA_AVAILABLE = "DATA_AVAILABLE";
    private final IBinder mBinder = new LocalBinder();

    private BluetoothAdapter mBluetoothAdapter;
    private final String DEVICE_NAME="HC-06";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//Serial Port Service ID
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean deviceConnected;

    byte buffer[];
    boolean stopThread;

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            Toast.makeText(getApplicationContext(), "Device doesnt Support Bluetooth", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void broadcastUpdate(final String action, String[] data) {
        final Intent intent = new Intent(action);
        if(action.equals(ACTION_DATA_AVAILABLE)){
            final int pressure0 = Integer.parseInt(data[0]);
            final int pressure1 = Integer.parseInt(data[1]);
            final String[] accArr = data[2].replace("#", "").split(",");
            final int yaw = Integer.parseInt(accArr[0]);
            final int pitch = Integer.parseInt(accArr[1]);
            final int roll = Integer.parseInt(accArr[2]);
            intent.putExtra(Sensors.PRESSURE0.name(), pressure0);
            intent.putExtra(Sensors.PRESSURE1.name(), pressure1);
            intent.putExtra(Sensors.YAW.name(), yaw);
            intent.putExtra(Sensors.PITCH.name(), pitch);
            intent.putExtra(Sensors.ROLL.name(), roll);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    public boolean BTinit()
    {
        boolean found = false;
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if(bondedDevices.isEmpty())
        {
            Toast.makeText(getApplicationContext(),"Please Pair the Device first",Toast.LENGTH_SHORT).show();
        }
        else
        {
            for (BluetoothDevice btDevice : bondedDevices)
            {
                if(btDevice.getName().equals(DEVICE_NAME))
                {
                    device=btDevice;
                    found=true;
                    break;
                }
            }
        }
        return found;
    }

    public boolean BTconnect()
    {
        boolean deviceConnected=true;
        try {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            deviceConnected=false;
        }
        if(deviceConnected)
        {
            try {
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return deviceConnected;
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread  = new Thread(new Runnable()
        {
            public void run()
            {
                StringBuilder data = new StringBuilder();
                while(!Thread.currentThread().isInterrupted() && !stopThread)
                {
                    try
                    {
                        int byteCount = inputStream.available();
                        if(byteCount > 0)
                        {
                            byte[] rawBytes = new byte[byteCount];
                            inputStream.read(rawBytes);
                            final String dataPart=new String(rawBytes,"UTF-8");
                            data.append(dataPart);
                            if(dataPart.endsWith("#")) {
                                final String[] results = data.toString().split(";");
                                handler.post(new Runnable() {
                                    public void run() {
                                        broadcastUpdate(ACTION_DATA_AVAILABLE, results);
                                        sendAck("1");
                                    }
                                });
                                data.setLength(0);
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        ex.printStackTrace();
                        stopThread = true;
                    }
                }
            }
        });

        thread.start();
    }

    public void sendAck(String ack) {
        try {
            outputStream.write(ack.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean connect(){
        if(!(BTinit() && BTconnect()))
            return false;
        deviceConnected=true;
        sendAck("1");
        beginListenForData();
        return true;
    }

    public boolean disconnect() throws IOException {
        stopThread = true;
        outputStream.close();
        inputStream.close();
        socket.close();
        deviceConnected=false;
        return true;
    }

    public boolean isDeviceConnected(){
        return deviceConnected;
    }
}
