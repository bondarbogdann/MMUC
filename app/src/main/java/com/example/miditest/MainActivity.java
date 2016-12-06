package com.example.miditest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.billthefarmer.mididriver.MidiDriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements MidiDriver.OnMidiStartListener {

    private MidiDriver midiDriver;
    private byte[] event;
    private int[] config;

    private Spinner spinnerInstruments;

    private final String DEVICE_NAME="HC-06";
    //private final String DEVICE_ADDRESS="20:13:10:15:33:66";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//Serial Port Service ID
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    TextView pressureView;
    Button startButton, stopButton;
    boolean deviceConnected=false;
    byte buffer[];
    boolean stopThread;
    boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the instruments spinner.
        spinnerInstruments = (Spinner)findViewById(R.id.spinnerInstruments);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.instruments_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInstruments.setAdapter(adapter);
        spinnerInstruments.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                selectInstrument(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        // Instantiate the driver.
        midiDriver = new MidiDriver();
        // Set the listener.
        midiDriver.setOnMidiStartListener(this);

        pressureView = (TextView) findViewById(R.id.pressureView);
        startButton = (Button) findViewById(R.id.buttonStart);
        stopButton = (Button) findViewById(R.id.buttonStop);
        stopButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        midiDriver.start();

        // Get the configuration.
        config = midiDriver.config();

        // Print out the details.
        Log.d(this.getClass().getName(), "maxVoices: " + config[0]);
        Log.d(this.getClass().getName(), "numChannels: " + config[1]);
        Log.d(this.getClass().getName(), "sampleRate: " + config[2]);
        Log.d(this.getClass().getName(), "mixBufferSize: " + config[3]);


    }

    @Override
    protected void onPause() {
        super.onPause();
        midiDriver.stop();
    }

    @Override
    public void onMidiStart() {
        Log.d(this.getClass().getName(), "onMidiStart()");
    }

    private void playNote(int noteNumber) {

        // Construct a note ON message for the note at maximum velocity on channel 1:
        event = new byte[3];
        event[0] = (byte) (0x90 | 0x00);  // 0x90 = note On, 0x00 = channel 1
        event[1] = (byte) noteNumber;
        event[2] = (byte) 0x7F;  // 0x7F = the maximum velocity (127)

        // Send the MIDI event to the synthesizer.
        midiDriver.write(event);

    }

    private void stopNote(int noteNumber, boolean sustainUpEvent) {

        // Stop the note unless the sustain button is currently pressed. Or stop the note if the
        // sustain button was depressed and the note's button is not pressed.
        if (sustainUpEvent) {
            // Construct a note OFF message for the note at minimum velocity on channel 1:
            event = new byte[3];
            event[0] = (byte) (0x80 | 0x00);  // 0x80 = note Off, 0x00 = channel 1
            event[1] = (byte) noteNumber;
            event[2] = (byte) 0x00;  // 0x00 = the minimum velocity (0)

            // Send the MIDI event to the synthesizer.
            midiDriver.write(event);
        }
    }

    private void selectInstrument(int instrument) {

        // Construct a program change to select the instrument on channel 1:
        event = new byte[2];
        event[0] = (byte)(0xC0 | 0x00); // 0xC0 = program change, 0x00 = channel 1
        event[1] = (byte)instrument;

        // Send the MIDI event to the synthesizer.
        midiDriver.write(event);

    }

    public boolean BTinit()
    {
        boolean found=false;
        BluetoothAdapter bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device doesnt Support Bluetooth", Toast.LENGTH_SHORT).show();
        }
        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
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
        boolean connected=true;
        try {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            connected=false;
        }
        if(connected)
        {
            try {
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return connected;
    }

    public void onClickStart(View view) {
        if(BTinit() && BTconnect())
        {
            setUiEnabled(true);
            deviceConnected=true;
            sendAck("1");
            beginListenForData();
            pressureView.setText("Connection Established!");
        }
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
                                String[] results = data.toString().split(";");
                                final String pressureStr0 = results[0];
                                final String pressureStr1 = results[1];
                                final int pressure0 = Integer.parseInt(pressureStr0);
                                final int pressure1 = Integer.parseInt(pressureStr1);
                                final String accelerometerStr = results[2].replace("#", "");
                                final String[] accArr = accelerometerStr.split(",");
                                final int yaw = Integer.parseInt(accArr[0]);
                                final int pitch = Integer.parseInt(accArr[1]);
                                final int roll = Integer.parseInt(accArr[2]);
                                handler.post(new Runnable() {
                                    public void run() {
                                        pressureView.setText(
                                                pressureStr0 + ";" +
                                                pressureStr1 + ";" +
                                                accArr[0] + "," +
                                                accArr[1] + "," +
                                                accArr[2]);
                                        if (pressure0 > 20 && !isPlaying) {
                                            playNote(60);
                                            isPlaying = true;
                                        }
                                        if (pressure0 < 20 && isPlaying){
                                            stopNote(60, true);
                                            isPlaying = false;
                                        }
                                        if (pressure1 > 20) {
                                            playNote(70);
                                        }
                                        if (pressure1 < 20){
                                            stopNote(70, true);
                                        }
                                        sendAck("1");
                                    }
                                });
                                data.setLength(0);
                            }
                        }
                    }
                    catch (IOException ex)
                    {
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

    public void onClickStop(View view) throws IOException {
        stopThread = true;
        outputStream.close();
        inputStream.close();
        socket.close();
        setUiEnabled(false);
        deviceConnected=false;
        pressureView.setText("Connection Closed!");
    }

    public void setUiEnabled(boolean bool)
    {
        startButton.setEnabled(!bool);
        stopButton.setEnabled(bool);
    }
}
