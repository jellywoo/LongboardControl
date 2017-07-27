/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.polkapolka.bluetooth.le;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

import android.widget.SeekBar;

import com.github.anastr.speedviewlib.base.Gauge;
import com.github.anastr.speedviewlib.base.Speedometer;
import com.larswerkman.holocolorpicker.ColorPicker;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements ColorPicker.OnColorChangedListener{
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private int[] RGBFrame = {0,0,0};
    private TextView isSerial;
    private TextView mConnectionState;
    private TextView batteryPercent;
    private SeekBar mRed,mGreen,mBlue;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;
    private int red = 0, green = 255, blue = 0;
    private ColorPicker picker;
    private ProgressBar progressBar;
    private VerticalSeekBar seekBar;
    private long lastBatteryTime;
    private Gauge gauge;
    private Location oldLocation;
    private int firstOpen = 1;
    private float curDist = 0;
    private float lastDist;
    private float totalDist;
    private TextView dist1;
    private TextView dist2;
    private TextView dist3;

    private int curSpeed;
    private boolean motorOn = false;
    private long lastMotorTime;
    private final Handler handler = new Handler();
    private final Runnable runnable = new Runnable() {
        public void run() {
            if (motorOn && (SystemClock.elapsedRealtime() - lastMotorTime > 0)) {
                String str = "a," + curSpeed + "\n";
                Log.d(TAG, "sending motor speed (runnable)=" + str);

                final byte[] tx = str.getBytes();
                if (mConnected) {
                    characteristicTX.setValue(tx);
                    mBluetoothLeService.writeCharacteristic(characteristicTX);
                    mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
                    lastMotorTime = SystemClock.elapsedRealtime();
                    handler.postDelayed(runnable, 10);
                }
            }
        }
    };

    public final static UUID HM_RX_TX =
            UUID.fromString(SampleGattAttributes.HM_RX_TX);

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        isSerial = (TextView) findViewById(R.id.isSerial);
        dist1 = (TextView)findViewById(R.id.miles1b);
        dist2 = (TextView)findViewById(R.id.miles2b);
        dist3 = (TextView)findViewById(R.id.miles3b);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        lastDist = prefs.getFloat("lastDist", 0);
        totalDist = prefs.getFloat("totalDist", 0);
        dist2.setText(String.format("%.02f", (lastDist * 0.000621371192)));
        dist3.setText(String.format("%.02f", (totalDist * 0.000621371192)));

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        picker = (ColorPicker) findViewById(R.id.picker);
        picker.setShowOldCenterColor(false);
        picker.setOnColorChangedListener(this);
        picker.setVisibility(View.INVISIBLE);
        picker.setEnabled(false);

        batteryPercent = (TextView) findViewById(R.id.batteryPercent);
        progressBar = (ProgressBar) findViewById(R.id.battery);
        seekBar = (VerticalSeekBar) findViewById(R.id.seekBar1);

        gauge = (Gauge) findViewById(R.id.speedometer);
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if (firstOpen == 1) {
                    firstOpen = 0;
                    oldLocation = location;
                }

                else {
                    if (location.distanceTo(oldLocation) - oldLocation.getAccuracy() > 0) {
                        gauge.speedTo(location.getSpeed() * (25 / 11));

                        curDist += location.distanceTo(oldLocation);
                        String temp = String.format("%.02f", (curDist * 0.000621371192));
                        dist1.setText(temp);
                        temp = String.format("%.02f", ((lastDist + curDist) * 0.000621371192));
                        dist2.setText(temp);
                        temp = String.format("%.02f", ((totalDist + curDist) * 0.000621371192));
                        dist3.setText(temp);

                        oldLocation = location;
                    }
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        catch (SecurityException e){
            gauge.speedTo(0);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        seekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                handler.removeCallbacks(runnable);
                i *= 1.8;
                curSpeed = i;

                String str = "a," + i + "\n";
                Log.d(TAG, "sending motor speed (listener)=" + str);

                final byte[] tx = str.getBytes();
                if (mConnected) {
                    characteristicTX.setValue(tx);
                    mBluetoothLeService.writeCharacteristic(characteristicTX);
                    mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
                    lastMotorTime = SystemClock.elapsedRealtime();
                    handler.postDelayed(runnable, 10);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                motorOn = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                motorOn = false;
                seekBar.setProgress(50);
                handler.removeCallbacks(runnable);
            }
        });

        progressBar.setProgress(100);
        batteryPercent.setText("No Data");

        Switch lightingSwitch = (Switch) findViewById(R.id.switch1);
        lightingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                String str;

                if (!b) {
                    picker.setVisibility(View.INVISIBLE);
                    picker.setEnabled(false);
                    str = "b," + 0 + "," + 0 + "," + 0 + "\n";
                }
                else {
                    picker.setVisibility(View.VISIBLE);
                    picker.setEnabled(true);
                    str = "b," + red + "," + green + "," + blue + "\n";
                }

                if(mConnected) {
                    Log.d(TAG, "sending color=" + str);

                    final byte[] tx = str.getBytes();
                    characteristicTX.setValue(tx);
                    mBluetoothLeService.writeCharacteristic(characteristicTX);
                    mBluetoothLeService.setCharacteristicNotification(characteristicRX,true);
                }
            }
        });
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String inData = intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
                if (inData != null && (SystemClock.elapsedRealtime() - lastBatteryTime) > 10000) {
                    try {
                        lastBatteryTime = SystemClock.elapsedRealtime();
                        int temp = Integer.parseInt(inData.trim());
                        progressBar.setProgress(temp);
                        batteryPercent.setText(progressBar.getProgress() + "%");

//                        String newColor;
//                        float green = 255 * (progressBar.getProgress()/100);
//                        float red = 255 - green;
//                        newColor = "#" + Integer.toString(Math.round(red)) + Integer.toString(Math.round(green)) + "00";
//                        progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor(newColor)));
                    }
                    catch (NumberFormatException e) {

                    }
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        lastDist = prefs.getFloat("lastDist", 0);
        totalDist = prefs.getFloat("totalDist", 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);

        totalDist += curDist;
        lastDist += curDist;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("lastDist", lastDist);
        editor.putFloat("totalDist", totalDist);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case R.id.clear_miles1:
                lastDist = 0;
                dist2.setText(String.format("%.02f", lastDist));
                return true;
            case R.id.clear_miles2:
                totalDist = 0;
                dist3.setText(String.format("%.02f", totalDist));
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
                if (resourceId == R.string.disconnected) {
                    isSerial.setText("Getting ready...");
                }
            }
        });
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            
            // If the service exists for HM 10 Serial, say so.
            if(SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") { isSerial.setText("Ready"); } else {  isSerial.setText("No Data"); }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

     		// get characteristic when UUID matches RX/TX UUID
    		 characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
    		 characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }
        
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onColorChanged(int color){
        red = Color.red(color);
        green = Color.green(color);
        blue = Color.blue(color);

        String str = "b," + red + "," + green + "," + blue + "\n";
        Log.d(TAG, "sending color=" + str);
        final byte[] tx = str.getBytes();
        if(mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX,true);
        }
    }
}