package com.example.miditest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;

    MidiProcessor midiProcessor;
    private BluetoothService btService;

    TextView pressureView;

    //Bluetooth service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            btService = ((BluetoothService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected btService= " + btService);
            if (!btService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            btService = null;
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BluetoothService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(BluetoothStatusChangeReceiver, BluetoothActionsFilter());
    }

    private static IntentFilter BluetoothActionsFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final BroadcastReceiver BluetoothStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothService.ACTION_DATA_AVAILABLE)) {

                final int pressure0 = intent.getIntExtra(Sensors.PRESSURE0.name(),0);
                final int pressure1 = intent.getIntExtra(Sensors.PRESSURE1.name(),0);
                final int yaw = intent.getIntExtra(Sensors.YAW.name(),0);
                final int pitch = intent.getIntExtra(Sensors.PITCH.name(),0);
                final int roll = intent.getIntExtra(Sensors.ROLL.name(),0);

                runOnUiThread(new Runnable() {
                    public void run() {
                        pressureView.setText(
                                pressure0 + ";" + pressure1 + ";" + yaw + "," + pitch + "," + roll);
                        midiProcessor.play(pressure0, pressure1, yaw, pitch, roll);
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        // Set up the instruments spinner.
        Spinner spinnerInstruments0 = (Spinner) findViewById(R.id.spinnerInstruments0);
        Spinner spinnerInstruments1 = (Spinner) findViewById(R.id.spinnerInstruments1);
        Spinner spinnerInstruments2 = (Spinner) findViewById(R.id.spinnerInstruments2);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.instruments_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInstruments0.setAdapter(adapter);
        spinnerInstruments1.setAdapter(adapter);
        spinnerInstruments2.setAdapter(adapter);
        spinnerInstruments0.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                midiProcessor.selectInstrument(position, (byte)0x00);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spinnerInstruments1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                midiProcessor.selectInstrument(position, (byte)0x01);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spinnerInstruments2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                midiProcessor.selectInstrument(position, (byte)0x02);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        pressureView = (TextView) findViewById(R.id.pressureView);
        midiProcessor = new MidiProcessor();
        service_init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        midiProcessor.startDriver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        midiProcessor.stopDriver();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    Log.i(TAG, "BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (!btService.isDeviceConnected()){
                        if (btService.connect()){
                            item.setTitle("Disconnect");
                            Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Couldn't connect to device", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        //Disconnect button pressed
                        if (btService!=null)
                        {
                            try {
                                if (btService.disconnect()){
                                    item.setTitle("Connect");
                                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Couldn't disconnect from device", Toast.LENGTH_SHORT).show();
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Can't disconect from BT");
                            }
                        }
                    }
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(BluetoothStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        btService.stopSelf();
        btService= null;

    }
}
