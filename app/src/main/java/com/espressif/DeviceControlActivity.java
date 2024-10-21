package com.espressif;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.GridLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.wifi_provisioning.R;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

//import static com.espressif.bluetoothlegatt.SampleGattAttributes.MIDI_CHARACTERISTIC;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String TAGACTIVITY = "DEVICECONTROL";

    public final static UUID UUID_NORDIC_UART_SERVICE =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_SERVICE);
    public final static UUID UUID_NORDIC_UART_CHARACTERISTIC_RX =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_CHARACTERISTIC_RX);
    public final static UUID UUID_NORDIC_UART_CHARACTERISTIC_TX =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_CHARACTERISTIC_TX);
    public final static UUID UUID_MIDI_CHARACTERISTIC =
            UUID.fromString(SampleGattAttributes.MIDI_CHARACTERISTIC);

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;

    private TextView mProt_recep;
    private TextView mProt_sent;
    public static TextView mSoftver_recep;
    public static TextView mSerialnumb_recep;

    public static EditText mPPPLowB;
    public static EditText mPPPHighB;
    public static EditText mPPHighB;
    public static EditText mPHighB;
    public static EditText mMPHighB;
    public static EditText mMFHighB;
    public static EditText mFHighB;
    public static EditText mFFHighB;
    public static EditText mFFFHighB;

    public static EditText mPPPLowW;
    public static EditText mPPPHighW;
    public static EditText mPPHighW;
    public static EditText mPHighW;
    public static EditText mMPHighW;
    public static EditText mMFHighW;
    public static EditText mFHighW;
    public static EditText mFFHighW;
    public static EditText mFFFHighW;

    public static TextView mKey1Val;
    public static TextView mKey2Val;
    public static TextView mKey3Val;
    public static TextView mKey4Val;
    public static TextView mKey5Val;
    public static TextView mKey6Val;
    public static TextView mKey7Val;
    public static TextView mKey8Val;
    public static TextView mKey9Val;
    public static TextView mKey10Val;
    public static TextView mKey11Val;
    public static TextView mKey12Val;

    public static EditText mKey1Off;
    public static EditText mKey2Off;
    public static EditText mKey3Off;
    public static EditText mKey4Off;
    public static EditText mKey5Off;
    public static EditText mKey6Off;
    public static EditText mKey7Off;
    public static EditText mKey8Off;
    public static EditText mKey9Off;
    public static EditText mKey10Off;
    public static EditText mKey11Off;
    public static EditText mKey12Off;

    public static GridLayout mGridOff;
    public static GridLayout mGridLev;

    public static int RadioOption = 0xFF;

    private ArrayList<Entry> DataValsiaq = new ArrayList<Entry>();
    private LineDataSet DataSetiaq = new LineDataSet(DataValsiaq, "IAQ");
    private ArrayList<ILineDataSet> iLineDataSetiaq = new ArrayList<>();

    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mRxCharacteristic;
    private BluetoothGattCharacteristic mTxCharacteristic;
    private BluetoothGattCharacteristic mMidiCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mBluetoothDevice;


    TextView txt;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Conectar al dispositivo
            boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Conectando al dispositivo: " + mDeviceAddress + " Resultado: " + result);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
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
            final String string = intent.getDataString();

            Log.d(TAG, "mGattUpdateReceiver " + action + string);

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                if (mRxCharacteristic != null) {
                    Log.d(TAG, "Unlock!");

                    byte[] value = new byte[6];
                    value[0] = (byte) 0x00;
                    value[1] = (byte) 0xEF;
                    value[2] = (byte) 0x20;
                    value[3] = (byte) 0x00;
                    value[4] = (byte) 0x00;
                    value[5] = (byte) 0x00;
                    mRxCharacteristic.setValue(value);
                    mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                if (null != intent.getStringExtra(BluetoothLeService.MIDI_DATA)) {
                    mProt_recep.setText(intent.getStringExtra(BluetoothLeService.MIDI_DATA));
                }
                if (null != intent.getStringExtra(BluetoothLeService.TX_DATA)){
                    mProt_sent.setText(intent.getStringExtra(BluetoothLeService.TX_DATA));
                }
            }
        }
    };
    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                            if (UUID_NORDIC_UART_CHARACTERISTIC_RX.equals(characteristic.getUuid())) {
                                // characteristic.setValue(testdata);
                                // mBluetoothLeService.writeCharacteristic(characteristic);
                                //ProgressBarShow(true);
                            }
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Agregar un logotipo
        getSupportActionBar().setLogo(R.drawable.bg_splash);
        getSupportActionBar().setDisplayUseLogoEnabled(true);

        // Recuperar la dirección del dispositivo BLE desde el Intent
        Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra("device");
        mDeviceName = intent.getStringExtra("deviceName");
        //mDeviceName = intent.getStringExtra("deviceName");

        Log.i(TAG,"DeviceControl ONCREATE");
        EXTRAS_DEVICE_ADDRESS = mDeviceAddress;
        EXTRAS_DEVICE_NAME = mDeviceName;

        // Sets up UI references.
        TextView deviceAddressTextView = (TextView) findViewById(R.id.device_address);
        deviceAddressTextView.setText(mDeviceName);

        Log.i(TAGACTIVITY, "Device Adress: " + mDeviceAddress);
        Log.i(TAGACTIVITY, "Device Name: " + mDeviceName);
        //((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        mProt_recep = (TextView) findViewById(R.id.prot_recep);
        mProt_sent = (TextView) findViewById(R.id.prot_sent);

        mSoftver_recep = (TextView) findViewById(R.id.softver_recep);;
        mSerialnumb_recep = (TextView) findViewById(R.id.serialnumb_recep);;

        mPPPLowB = (EditText) findViewById(R.id.textPPPLowB);
        mPPPHighB = (EditText) findViewById(R.id.textPPPHighB);
        mPPHighB = (EditText) findViewById(R.id.textPPHighB);
        mPHighB = (EditText) findViewById(R.id.textPHighB);
        mMPHighB = (EditText) findViewById(R.id.textMPHighB);
        mMFHighB = (EditText) findViewById(R.id.textMFHighB);
        mFHighB = (EditText) findViewById(R.id.textFHighB);
        mFFHighB = (EditText) findViewById(R.id.textFFHighB);
        mFFFHighB = (EditText) findViewById(R.id.textFFFHighB);

        mPPPLowW = (EditText) findViewById(R.id.textPPPLow);
        mPPPHighW = (EditText) findViewById(R.id.textPPPHigh);
        mPPHighW = (EditText) findViewById(R.id.textPPHigh);
        mPHighW = (EditText) findViewById(R.id.textPHigh);
        mMPHighW = (EditText) findViewById(R.id.textMPHigh);
        mMFHighW = (EditText) findViewById(R.id.textMFHigh);
        mFHighW = (EditText) findViewById(R.id.textFHigh);
        mFFHighW = (EditText) findViewById(R.id.textFFHigh);
        mFFFHighW = (EditText) findViewById(R.id.textFFFHigh);

        mKey1Val = (TextView) findViewById(R.id.textKey1Val);
        mKey2Val = (TextView) findViewById(R.id.textKey2Val);
        mKey3Val = (TextView) findViewById(R.id.textKey3Val);
        mKey4Val = (TextView) findViewById(R.id.textKey4Val);
        mKey5Val = (TextView) findViewById(R.id.textKey5Val);
        mKey6Val = (TextView) findViewById(R.id.textKey6Val);
        mKey7Val = (TextView) findViewById(R.id.textKey7Val);
        mKey8Val = (TextView) findViewById(R.id.textKey8Val);
        mKey9Val = (TextView) findViewById(R.id.textKey9Val);
        mKey10Val = (TextView) findViewById(R.id.textKey10Val);
        mKey11Val = (TextView) findViewById(R.id.textKey11Val);
        mKey12Val = (TextView) findViewById(R.id.textKey12Val);

        mKey1Off = (EditText) findViewById(R.id.textKey1Off);
        mKey2Off = (EditText) findViewById(R.id.textKey2Off);
        mKey3Off = (EditText) findViewById(R.id.textKey3Off);
        mKey4Off = (EditText) findViewById(R.id.textKey4Off);
        mKey5Off = (EditText) findViewById(R.id.textKey5Off);
        mKey6Off = (EditText) findViewById(R.id.textKey6Off);
        mKey7Off = (EditText) findViewById(R.id.textKey7Off);
        mKey8Off = (EditText) findViewById(R.id.textKey8Off);
        mKey9Off = (EditText) findViewById(R.id.textKey9Off);
        mKey10Off = (EditText) findViewById(R.id.textKey10Off);
        mKey11Off = (EditText) findViewById(R.id.textKey11Off);
        mKey12Off = (EditText) findViewById(R.id.textKey12Off);

        mGridLev = (GridLayout) findViewById(R.id.GridLev);
        mGridOff = (GridLayout) findViewById(R.id.GridOff);

        // Vincular el servicio BLE
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_control, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem connectionStatusItem = menu.findItem(R.id.action_connection_status);

        // Cambiar el ícono dinámicamente según el estado de la conexión
        if (mConnected) {
            connectionStatusItem.setIcon(R.drawable.ic_connected); // Ícono de conectado
        } else {
            connectionStatusItem.setIcon(R.drawable.ic_disconnected); // Ícono de desconectado
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Cerrar esta actividad y regresar a la anterior
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        Log.d(TAG, "displayData " +data);

        if (data != null) {
//            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

/*
Generic Attri	UUID: 00001801-0000-1000-8000-00805f9b34fb
					UUID: 00002a05-0000-1000-8000-00805f9b34fb
Generic Access	UUID: 00001800-0000-1000-8000-00805f9b34fb
    				UUID: 00002a00-0000-1000-8000-00805f9b34fb
    				UUID: 00002a01-0000-1000-8000-00805f9b34fb
					UUID: 00002aa6-0000-1000-8000-00805f9b34fb
MIDI Ble		UUID: 03b80e5a-ede8-4b33-a751-6ce34ec4c700
    				UUID: 7772e5db-3868-4112-a1a9-f2669d106bf3
						Get Midi Characteristic
						Notification  7772e5db-3868-4112-a1a9-f2669d106bf3
								uuid: 7772e5db-3868-4112-a1a9-f2669d106bf3 enable: true
NUS				UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
	RX 				UUID: 6e400002-b5a3-f393-e0a9-e50e24dcca9e
    					Get NUS RX Characteristic
	TX				UUID: 6e400003-b5a3-f393-e0a9-e50e24dcca9e
    					Get NUS TX Characteristic
						Notification 6e400003-b5a3-f393-e0a9-e50e24dcca9e
						uuid: 6e400003-b5a3-f393-e0a9-e50e24dcca9e enable: true
*/

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            Log.d(TAG, "displayGattServices service UUID: " +uuid);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                Log.d(TAG, "Characteristic UUID: " +uuid);

                if (gattCharacteristic.getUuid().equals(UUID_MIDI_CHARACTERISTIC)){
                    Log.d(TAG, "Get Midi Characteristic");
                    mMidiCharacteristic = gattCharacteristic;
                }
                else if (gattCharacteristic.getUuid().equals(UUID_NORDIC_UART_CHARACTERISTIC_RX)){
                    Log.d(TAG, "Get NUS RX Characteristic");
                    mRxCharacteristic = gattCharacteristic;
                }
                else if (gattCharacteristic.getUuid().equals(UUID_NORDIC_UART_CHARACTERISTIC_TX)){
                    Log.d(TAG, "Get NUS TX Characteristic");
                    mTxCharacteristic = gattCharacteristic;
                }

                // Activate notifications for all characteristics
                if ((gattCharacteristic.getUuid().equals(UUID_NORDIC_UART_CHARACTERISTIC_TX)) ||
                        (gattCharacteristic.getUuid().equals(UUID_MIDI_CHARACTERISTIC)) ) {
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                }
            }
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
//        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        mKey1Val.setText("");
        mKey2Val.setText("");
        mKey3Val.setText("");
        mKey4Val.setText("");
        mKey5Val.setText("");
        mKey6Val.setText("");
        mKey7Val.setText("");
        mKey8Val.setText("");
        mKey9Val.setText("");
        mKey10Val.setText("");
        mKey11Val.setText("");
        mKey12Val.setText("");

        mKey1Off.setText("");
        mKey2Off.setText("");
        mKey3Off.setText("");
        mKey4Off.setText("");
        mKey5Off.setText("");
        mKey6Off.setText("");
        mKey7Off.setText("");
        mKey8Off.setText("");
        mKey9Off.setText("");
        mKey10Off.setText("");
        mKey11Off.setText("");
        mKey12Off.setText("");
        // Check which radio button was clicked
        // Verifica qué RadioButton fue seleccionado y realiza las acciones
        if (view.getId() == R.id.radio_z && checked) {
            if (RadioOption != 0) {
                RadioOption = 0;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave1 && checked) {
            if (RadioOption != 1) {
                RadioOption = 1;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave2 && checked) {
            if (RadioOption != 2) {
                RadioOption = 2;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave3 && checked) {
            if (RadioOption != 3) {
                RadioOption = 3;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave4 && checked) {
            if (RadioOption != 4) {
                RadioOption = 4;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave5 && checked) {
            if (RadioOption != 5) {
                RadioOption = 5;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        } else if (view.getId() == R.id.radio_octave6 && checked) {
            if (RadioOption != 6) {
                RadioOption = 6;
                mSerialnumb_recep.setText("");
                mSoftver_recep.setText("");
            }
        }


        byte[] value = new byte[6];
        value[0] = (byte) 0x10;//Serial
        value[1] = (byte) RadioOption;
        value[2] = (byte) 0x00;
        value[3] = (byte) 0x00;
        value[4] = (byte) 0x00;
        value[5] = (byte) 0x00;
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
        Log.d(TAG, "Request serial Module " +value[1]);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                byte[] value = new byte[6];
                value[0] = (byte) 0x0E;//Soft ver
                value[1] = (byte) RadioOption;
                value[2] = (byte) 0x00;
                value[3] = (byte) 0x00;
                value[4] = (byte) 0x00;
                value[5] = (byte) 0x00;
                mRxCharacteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                Log.d(TAG, "Request soft ver " +value[1]);

                Handler handler2 = new Handler();
                handler2.postDelayed(new Runnable()
                {
                    @Override
                    public void run() {
                        byte[] value = new byte[6];
                        value[0] = (byte) 0x23;//Enable keys from selected module
                        value[1] = (byte) (0x01<<RadioOption);
                        value[2] = (byte) 0x00;
                        value[3] = (byte) 0x00;
                        value[4] = (byte) 0x00;
                        value[5] = (byte) 0x00;
                        mRxCharacteristic.setValue(value);
                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                        Log.d(TAG, "Request key mask " +value[1]);
                    }
                }, 100);
            }
        }, 100);
    }

    public void sendReadLevel(View view) {
        if (mRxCharacteristic == null) {
            Log.e(TAG, "char not found!");
            return;
        }

        byte[] value = new byte[6];
        value[0] = (byte) 0x26;//Get level
        value[1] = (byte) 0x00;//White
        value[2] = (byte) 0x00;//Level
        value[3] = (byte) 0x00;
        value[4] = (byte) 0x00;
        value[5] = (byte) 0x00;
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                byte[] value = new byte[6];
                value[0] = (byte) 0x26;//Get level
                value[1] = (byte) 0x00;//White
                value[2] = (byte) 0x01;//Level
                value[3] = (byte) 0x00;
                value[4] = (byte) 0x00;
                value[5] = (byte) 0x00;
                mRxCharacteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                Handler handler2 = new Handler();
                handler2.postDelayed(new Runnable()
                {
                    @Override
                    public void run() {
                        byte[] value = new byte[6];
                        value[0] = (byte) 0x26;//Get level
                        value[1] = (byte) 0x00;//White
                        value[2] = (byte) 0x02;//Level
                        value[3] = (byte) 0x00;
                        value[4] = (byte) 0x00;
                        value[5] = (byte) 0x00;
                        mRxCharacteristic.setValue(value);
                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                        Handler handler3 = new Handler();
                        handler3.postDelayed(new Runnable()
                        {
                            @Override
                            public void run() {
                                byte[] value = new byte[6];
                                value[0] = (byte) 0x26;//Get level
                                value[1] = (byte) 0x00;//White
                                value[2] = (byte) 0x03;//Level
                                value[3] = (byte) 0x00;
                                value[4] = (byte) 0x00;
                                value[5] = (byte) 0x00;
                                mRxCharacteristic.setValue(value);
                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                Handler handler4 = new Handler();
                                handler4.postDelayed(new Runnable()
                                {
                                    @Override
                                    public void run() {
                                        byte[] value = new byte[6];
                                        value[0] = (byte) 0x26;//Get level
                                        value[1] = (byte) 0x00;//White
                                        value[2] = (byte) 0x04;//Level
                                        value[3] = (byte) 0x00;
                                        value[4] = (byte) 0x00;
                                        value[5] = (byte) 0x00;
                                        mRxCharacteristic.setValue(value);
                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                        Handler handler5 = new Handler();
                                        handler5.postDelayed(new Runnable()
                                        {
                                            @Override
                                            public void run() {
                                                byte[] value = new byte[6];
                                                value[0] = (byte) 0x26;//Get level
                                                value[1] = (byte) 0x00;//White
                                                value[2] = (byte) 0x05;//Level
                                                value[3] = (byte) 0x00;
                                                value[4] = (byte) 0x00;
                                                value[5] = (byte) 0x00;
                                                mRxCharacteristic.setValue(value);
                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                Handler handler6 = new Handler();
                                                handler6.postDelayed(new Runnable()
                                                {
                                                    @Override
                                                    public void run() {
                                                        byte[] value = new byte[6];
                                                        value[0] = (byte) 0x26;//Get level
                                                        value[1] = (byte) 0x00;//White
                                                        value[2] = (byte) 0x06;//Level
                                                        value[3] = (byte) 0x00;
                                                        value[4] = (byte) 0x00;
                                                        value[5] = (byte) 0x00;
                                                        mRxCharacteristic.setValue(value);
                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                        Handler handler7 = new Handler();
                                                        handler7.postDelayed(new Runnable()
                                                        {
                                                            @Override
                                                            public void run() {
                                                                byte[] value = new byte[6];
                                                                value[0] = (byte) 0x26;//Get level
                                                                value[1] = (byte) 0x00;//White
                                                                value[2] = (byte) 0x07;//Level
                                                                value[3] = (byte) 0x00;
                                                                value[4] = (byte) 0x00;
                                                                value[5] = (byte) 0x00;
                                                                mRxCharacteristic.setValue(value);
                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                Handler handler8 = new Handler();
                                                                handler8.postDelayed(new Runnable()
                                                                {
                                                                    @Override
                                                                    public void run() {
                                                                        byte[] value = new byte[6];
                                                                        value[0] = (byte) 0x26;//Get level
                                                                        value[1] = (byte) 0x00;//White
                                                                        value[2] = (byte) 0x08;//Level
                                                                        value[3] = (byte) 0x00;
                                                                        value[4] = (byte) 0x00;
                                                                        value[5] = (byte) 0x00;
                                                                        mRxCharacteristic.setValue(value);
                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                        Handler handler0 = new Handler();
                                                                        handler0.postDelayed(new Runnable()
                                                                        {
                                                                            @Override
                                                                            public void run() {
                                                                                byte[] value = new byte[6];
                                                                                value[0] = (byte) 0x26;//Get level
                                                                                value[1] = (byte) 0x01;//Black
                                                                                value[2] = (byte) 0x00;//Level
                                                                                value[3] = (byte) 0x00;
                                                                                value[4] = (byte) 0x00;
                                                                                value[5] = (byte) 0x00;
                                                                                mRxCharacteristic.setValue(value);
                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                Handler handler01 = new Handler();
                                                                                handler01.postDelayed(new Runnable()
                                                                                {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        byte[] value = new byte[6];
                                                                                        value[0] = (byte) 0x26;//Get level
                                                                                        value[1] = (byte) 0x01;//Black
                                                                                        value[2] = (byte) 0x01;//Level
                                                                                        value[3] = (byte) 0x00;
                                                                                        value[4] = (byte) 0x00;
                                                                                        value[5] = (byte) 0x00;
                                                                                        mRxCharacteristic.setValue(value);
                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                        Handler handler02 = new Handler();
                                                                                        handler02.postDelayed(new Runnable()
                                                                                        {
                                                                                            @Override
                                                                                            public void run() {
                                                                                                byte[] value = new byte[6];
                                                                                                value[0] = (byte) 0x26;//Get level
                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                value[2] = (byte) 0x02;//Level
                                                                                                value[3] = (byte) 0x00;
                                                                                                value[4] = (byte) 0x00;
                                                                                                value[5] = (byte) 0x00;
                                                                                                mRxCharacteristic.setValue(value);
                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                Handler handler03 = new Handler();
                                                                                                handler03.postDelayed(new Runnable()
                                                                                                {
                                                                                                    @Override
                                                                                                    public void run() {
                                                                                                        byte[] value = new byte[6];
                                                                                                        value[0] = (byte) 0x26;//Get level
                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                        value[2] = (byte) 0x03;//Level
                                                                                                        value[3] = (byte) 0x00;
                                                                                                        value[4] = (byte) 0x00;
                                                                                                        value[5] = (byte) 0x00;
                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                        Handler handler04 = new Handler();
                                                                                                        handler04.postDelayed(new Runnable()
                                                                                                        {
                                                                                                            @Override
                                                                                                            public void run() {
                                                                                                                byte[] value = new byte[6];
                                                                                                                value[0] = (byte) 0x26;//Get level
                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                value[2] = (byte) 0x04;//Level
                                                                                                                value[3] = (byte) 0x00;
                                                                                                                value[4] = (byte) 0x00;
                                                                                                                value[5] = (byte) 0x00;
                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                Handler handler05 = new Handler();
                                                                                                                handler05.postDelayed(new Runnable()
                                                                                                                {
                                                                                                                    @Override
                                                                                                                    public void run() {
                                                                                                                        byte[] value = new byte[6];
                                                                                                                        value[0] = (byte) 0x26;//Get level
                                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                                        value[2] = (byte) 0x05;//Level
                                                                                                                        value[3] = (byte) 0x00;
                                                                                                                        value[4] = (byte) 0x00;
                                                                                                                        value[5] = (byte) 0x00;
                                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                        Handler handler06 = new Handler();
                                                                                                                        handler06.postDelayed(new Runnable()
                                                                                                                        {
                                                                                                                            @Override
                                                                                                                            public void run() {
                                                                                                                                byte[] value = new byte[6];
                                                                                                                                value[0] = (byte) 0x26;//Get level
                                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                                value[2] = (byte) 0x06;//Level
                                                                                                                                value[3] = (byte) 0x00;
                                                                                                                                value[4] = (byte) 0x00;
                                                                                                                                value[5] = (byte) 0x00;
                                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                Handler handler07 = new Handler();
                                                                                                                                handler07.postDelayed(new Runnable()
                                                                                                                                {
                                                                                                                                    @Override
                                                                                                                                    public void run() {
                                                                                                                                        byte[] value = new byte[6];
                                                                                                                                        value[0] = (byte) 0x26;//Get level
                                                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                                                        value[2] = (byte) 0x07;//Level
                                                                                                                                        value[3] = (byte) 0x00;
                                                                                                                                        value[4] = (byte) 0x00;
                                                                                                                                        value[5] = (byte) 0x00;
                                                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                        Handler handler08 = new Handler();
                                                                                                                                        handler08.postDelayed(new Runnable()
                                                                                                                                        {
                                                                                                                                            @Override
                                                                                                                                            public void run() {
                                                                                                                                                byte[] value = new byte[6];
                                                                                                                                                value[0] = (byte) 0x26;//Get level
                                                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                                                value[2] = (byte) 0x08;//Level
                                                                                                                                                value[3] = (byte) 0x00;
                                                                                                                                                value[4] = (byte) 0x00;
                                                                                                                                                value[5] = (byte) 0x00;
                                                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                            }
                                                                                                                                        }, 100);
                                                                                                                                    }
                                                                                                                                }, 100);
                                                                                                                            }
                                                                                                                        }, 100);
                                                                                                                    }
                                                                                                                }, 100);
                                                                                                            }
                                                                                                        }, 100);
                                                                                                    }
                                                                                                }, 100);
                                                                                            }
                                                                                        }, 100);
                                                                                    }
                                                                                }, 100);
                                                                            }
                                                                        }, 100);
                                                                    }
                                                                }, 100);
                                                            }
                                                        }, 100);
                                                    }
                                                }, 100);
                                            }
                                        }, 100);
                                    }
                                }, 100);
                            }
                        }, 100);
                    }
                }, 100);
            }
        }, 100);

    }

    public void sendWriteLevel(View view) {
        if (mRxCharacteristic == null) {
            Log.e(TAG, "char not found!");
            return;
        }

        Log.v("mPPPLowW", mPPPLowW.getText().toString());
        Log.v("mPPPHighW", mPPPHighW.getText().toString());
        Log.v("mPPHighW", mPPHighW.getText().toString());
        Log.v("mPHighW", mPHighW.getText().toString());
        Log.v("mMPHighW", mMPHighW.getText().toString());
        Log.v("mMFHighW", mMFHighW.getText().toString());
        Log.v("mFHighW", mFHighW.getText().toString());
        Log.v("mFFHighW", mFFHighW.getText().toString());
        Log.v("mFFFHighW", mFFFHighW.getText().toString());

        Log.v("mPPPLowB", mPPPLowB.getText().toString());
        Log.v("mPPPHighB", mPPPHighB.getText().toString());
        Log.v("mPPHighB", mPPHighB.getText().toString());
        Log.v("mPHighB", mPHighB.getText().toString());
        Log.v("mMPHighB", mMPHighB.getText().toString());
        Log.v("mMFHighB", mMFHighB.getText().toString());
        Log.v("mFHighB", mFHighB.getText().toString());
        Log.v("mFFHighB", mFFHighB.getText().toString());
        Log.v("mFFFHighB", mFFFHighB.getText().toString());

        byte[] value = new byte[6];
        int iVal = Integer.parseInt(mPPPLowW.getText().toString());
        value[0] = (byte) 0x25;//Set level
        value[1] = (byte) 0x00;//White
        value[2] = (byte) 0x00;
        value[3] = (byte) (iVal >>> 8);
        value[4] = (byte) iVal;
        value[5] = (byte) 0x00;
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                byte[] value = new byte[6];
                int iVal = Integer.parseInt(mPPPHighW.getText().toString());
                value[0] = (byte) 0x25;//Set level
                value[1] = (byte) 0x00;//White
                value[2] = (byte) 0x01;//Level
                value[3] = (byte) (iVal >>> 8);
                value[4] = (byte) iVal;
                value[5] = (byte) 0x00;
                mRxCharacteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                Handler handler2 = new Handler();
                handler2.postDelayed(new Runnable()
                {
                    @Override
                    public void run() {
                        byte[] value = new byte[6];
                        int iVal = Integer.parseInt(mPPHighW.getText().toString());
                        value[0] = (byte) 0x25;//Set level
                        value[1] = (byte) 0x00;//White
                        value[2] = (byte) 0x02;//Level
                        value[3] = (byte) (iVal >>> 8);
                        value[4] = (byte) iVal;
                        value[5] = (byte) 0x00;
                        mRxCharacteristic.setValue(value);
                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                        Handler handler3 = new Handler();
                        handler3.postDelayed(new Runnable()
                        {
                            @Override
                            public void run() {
                                byte[] value = new byte[6];
                                int iVal = Integer.parseInt(mPHighW.getText().toString());
                                value[0] = (byte) 0x25;//Set level
                                value[1] = (byte) 0x00;//White
                                value[2] = (byte) 0x03;//Level
                                value[3] = (byte) (iVal >>> 8);
                                value[4] = (byte) iVal;
                                value[5] = (byte) 0x00;
                                mRxCharacteristic.setValue(value);
                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                Handler handler4 = new Handler();
                                handler4.postDelayed(new Runnable()
                                {
                                    @Override
                                    public void run() {
                                        byte[] value = new byte[6];
                                        int iVal = Integer.parseInt(mMPHighW.getText().toString());
                                        value[0] = (byte) 0x25;//Set level
                                        value[1] = (byte) 0x00;//White
                                        value[2] = (byte) 0x04;//Level
                                        value[3] = (byte) (iVal >>> 8);
                                        value[4] = (byte) iVal;
                                        value[5] = (byte) 0x00;
                                        mRxCharacteristic.setValue(value);
                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                        Handler handler5 = new Handler();
                                        handler5.postDelayed(new Runnable()
                                        {
                                            @Override
                                            public void run() {
                                                byte[] value = new byte[6];
                                                int iVal = Integer.parseInt(mMFHighW.getText().toString());
                                                value[0] = (byte) 0x25;//Set level
                                                value[1] = (byte) 0x00;//White
                                                value[2] = (byte) 0x05;//Level
                                                value[3] = (byte) (iVal >>> 8);
                                                value[4] = (byte) iVal;
                                                value[5] = (byte) 0x00;
                                                mRxCharacteristic.setValue(value);
                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                Handler handler6 = new Handler();
                                                handler6.postDelayed(new Runnable()
                                                {
                                                    @Override
                                                    public void run() {
                                                        byte[] value = new byte[6];
                                                        int iVal = Integer.parseInt(mFHighW.getText().toString());
                                                        value[0] = (byte) 0x25;//Set level
                                                        value[1] = (byte) 0x00;//White
                                                        value[2] = (byte) 0x06;//Level
                                                        value[3] = (byte) (iVal >>> 8);
                                                        value[4] = (byte) iVal;
                                                        value[5] = (byte) 0x00;
                                                        mRxCharacteristic.setValue(value);
                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                        Handler handler7 = new Handler();
                                                        handler7.postDelayed(new Runnable()
                                                        {
                                                            @Override
                                                            public void run() {
                                                                byte[] value = new byte[6];
                                                                int iVal = Integer.parseInt(mFFHighW.getText().toString());
                                                                value[0] = (byte) 0x25;//Set level
                                                                value[1] = (byte) 0x00;//White
                                                                value[2] = (byte) 0x07;//Level
                                                                value[3] = (byte) (iVal >>> 8);
                                                                value[4] = (byte) iVal;
                                                                value[5] = (byte) 0x00;
                                                                mRxCharacteristic.setValue(value);
                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                Handler handler8 = new Handler();
                                                                handler8.postDelayed(new Runnable()
                                                                {
                                                                    @Override
                                                                    public void run() {
                                                                        byte[] value = new byte[6];
                                                                        int iVal = Integer.parseInt(mFFFHighW.getText().toString());
                                                                        value[0] = (byte) 0x25;//Set level
                                                                        value[1] = (byte) 0x00;//White
                                                                        value[2] = (byte) 0x08;//Level
                                                                        value[3] = (byte) (iVal >>> 8);
                                                                        value[4] = (byte) iVal;
                                                                        value[5] = (byte) 0x00;
                                                                        mRxCharacteristic.setValue(value);
                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                        Handler handler0 = new Handler();
                                                                        handler0.postDelayed(new Runnable()
                                                                        {
                                                                            @Override
                                                                            public void run() {
                                                                                byte[] value = new byte[6];
                                                                                int iVal = Integer.parseInt(mPPPLowB.getText().toString());
                                                                                value[0] = (byte) 0x25;//Set level
                                                                                value[1] = (byte) 0x01;//Black
                                                                                value[2] = (byte) 0x00;//Level
                                                                                value[3] = (byte) (iVal >>> 8);
                                                                                value[4] = (byte) iVal;
                                                                                value[5] = (byte) 0x00;
                                                                                mRxCharacteristic.setValue(value);
                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                Handler handler01 = new Handler();
                                                                                handler01.postDelayed(new Runnable()
                                                                                {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        byte[] value = new byte[6];
                                                                                        int iVal = Integer.parseInt(mPPPHighB.getText().toString());
                                                                                        value[0] = (byte) 0x25;//Set level
                                                                                        value[1] = (byte) 0x01;//Black
                                                                                        value[2] = (byte) 0x01;//Level
                                                                                        value[3] = (byte) (iVal >>> 8);
                                                                                        value[4] = (byte) iVal;
                                                                                        value[5] = (byte) 0x00;
                                                                                        mRxCharacteristic.setValue(value);
                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                        Handler handler02 = new Handler();
                                                                                        handler02.postDelayed(new Runnable()
                                                                                        {
                                                                                            @Override
                                                                                            public void run() {
                                                                                                byte[] value = new byte[6];
                                                                                                int iVal = Integer.parseInt(mPPHighB.getText().toString());
                                                                                                value[0] = (byte) 0x25;//Set level
                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                value[2] = (byte) 0x02;//Level
                                                                                                value[3] = (byte) (iVal >>> 8);
                                                                                                value[4] = (byte) iVal;
                                                                                                value[5] = (byte) 0x00;
                                                                                                mRxCharacteristic.setValue(value);
                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                Handler handler03 = new Handler();
                                                                                                handler03.postDelayed(new Runnable()
                                                                                                {
                                                                                                    @Override
                                                                                                    public void run() {
                                                                                                        byte[] value = new byte[6];
                                                                                                        int iVal = Integer.parseInt(mPHighB.getText().toString());
                                                                                                        value[0] = (byte) 0x25;//Set level
                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                        value[2] = (byte) 0x03;//Level
                                                                                                        value[3] = (byte) (iVal >>> 8);
                                                                                                        value[4] = (byte) iVal;
                                                                                                        value[5] = (byte) 0x00;
                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                        Handler handler04 = new Handler();
                                                                                                        handler04.postDelayed(new Runnable()
                                                                                                        {
                                                                                                            @Override
                                                                                                            public void run() {
                                                                                                                byte[] value = new byte[6];
                                                                                                                int iVal = Integer.parseInt(mMPHighB.getText().toString());
                                                                                                                value[0] = (byte) 0x25;//Set level
                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                value[2] = (byte) 0x04;//Level
                                                                                                                value[3] = (byte) (iVal >>> 8);
                                                                                                                value[4] = (byte) iVal;
                                                                                                                value[5] = (byte) 0x00;
                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                Handler handler05 = new Handler();
                                                                                                                handler05.postDelayed(new Runnable()
                                                                                                                {
                                                                                                                    @Override
                                                                                                                    public void run() {
                                                                                                                        byte[] value = new byte[6];
                                                                                                                        int iVal = Integer.parseInt(mMFHighB.getText().toString());
                                                                                                                        value[0] = (byte) 0x25;//Set level
                                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                                        value[2] = (byte) 0x05;//Level
                                                                                                                        value[3] = (byte) (iVal >>> 8);
                                                                                                                        value[4] = (byte) iVal;
                                                                                                                        value[5] = (byte) 0x00;
                                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                        Handler handler06 = new Handler();
                                                                                                                        handler06.postDelayed(new Runnable()
                                                                                                                        {
                                                                                                                            @Override
                                                                                                                            public void run() {
                                                                                                                                byte[] value = new byte[6];
                                                                                                                                int iVal = Integer.parseInt(mFHighB.getText().toString());
                                                                                                                                value[0] = (byte) 0x25;//Set level
                                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                                value[2] = (byte) 0x06;//Level
                                                                                                                                value[3] = (byte) (iVal >>> 8);
                                                                                                                                value[4] = (byte) iVal;
                                                                                                                                value[5] = (byte) 0x00;
                                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                Handler handler07 = new Handler();
                                                                                                                                handler07.postDelayed(new Runnable()
                                                                                                                                {
                                                                                                                                    @Override
                                                                                                                                    public void run() {
                                                                                                                                        byte[] value = new byte[6];
                                                                                                                                        int iVal = Integer.parseInt(mFFHighB.getText().toString());
                                                                                                                                        value[0] = (byte) 0x25;//Set level
                                                                                                                                        value[1] = (byte) 0x01;//Black
                                                                                                                                        value[2] = (byte) 0x07;//Level
                                                                                                                                        value[3] = (byte) (iVal >>> 8);
                                                                                                                                        value[4] = (byte) iVal;
                                                                                                                                        value[5] = (byte) 0x00;
                                                                                                                                        mRxCharacteristic.setValue(value);
                                                                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                        Handler handler08 = new Handler();
                                                                                                                                        handler08.postDelayed(new Runnable()
                                                                                                                                        {
                                                                                                                                            @Override
                                                                                                                                            public void run() {
                                                                                                                                                byte[] value = new byte[6];
                                                                                                                                                int iVal = Integer.parseInt(mFFFHighB.getText().toString());
                                                                                                                                                value[0] = (byte) 0x25;//Set level
                                                                                                                                                value[1] = (byte) 0x01;//Black
                                                                                                                                                value[2] = (byte) 0x08;//Level
                                                                                                                                                value[3] = (byte) 0x00;
                                                                                                                                                value[4] = (byte) 0x00;
                                                                                                                                                value[5] = (byte) 0x00;
                                                                                                                                                mRxCharacteristic.setValue(value);
                                                                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                                                                            }
                                                                                                                                        }, 100);
                                                                                                                                    }
                                                                                                                                }, 100);
                                                                                                                            }
                                                                                                                        }, 100);
                                                                                                                    }
                                                                                                                }, 100);
                                                                                                            }
                                                                                                        }, 100);
                                                                                                    }
                                                                                                }, 100);
                                                                                            }
                                                                                        }, 100);
                                                                                    }
                                                                                }, 100);
                                                                            }
                                                                        }, 100);
                                                                    }
                                                                }, 100);
                                                            }
                                                        }, 100);
                                                    }
                                                }, 100);
                                            }
                                        }, 100);
                                    }
                                }, 100);
                            }
                        }, 100);
                    }
                }, 100);
            }
        }, 100);
    }

    public void sendWriteOffset(View view) {
        if (mRxCharacteristic == null) {
            Log.e(TAG, "char not found!");
            return;
        }

        Log.v("mKey1Off", mKey1Off.getText().toString());
        Log.v("mKey2Off", mKey2Off.getText().toString());
        Log.v("mKey3Off", mKey3Off.getText().toString());
        Log.v("mKey4Off", mKey4Off.getText().toString());
        Log.v("mKey5Off", mKey5Off.getText().toString());
        Log.v("mKey6Off", mKey6Off.getText().toString());
        Log.v("mKey7Off", mKey7Off.getText().toString());
        Log.v("mKey8Off", mKey8Off.getText().toString());
        Log.v("mKey9Off", mKey9Off.getText().toString());
        Log.v("mKey10Off", mKey10Off.getText().toString());
        Log.v("mKey11Off", mKey11Off.getText().toString());
        Log.v("mKey12Off", mKey12Off.getText().toString());

        Log.v("mKey1Off n", ""+(short)Integer.parseInt(mKey1Off.getText().toString()));
        Log.v("mKey2Off n", ""+(short)Integer.parseInt(mKey2Off.getText().toString()));
        Log.v("mKey3Off n",""+(short)Integer.parseInt(mKey3Off.getText().toString()));
        Log.v("mKey4Off n", ""+(short)Integer.parseInt(mKey4Off.getText().toString()));
        Log.v("mKey5Off n", ""+(short)Integer.parseInt(mKey5Off.getText().toString()));
        Log.v("mKey6Off n", ""+(short)Integer.parseInt(mKey6Off.getText().toString()));
        Log.v("mKey7Off n", ""+(short)Integer.parseInt(mKey7Off.getText().toString()));
        Log.v("mKey8Off n", ""+(short)Integer.parseInt(mKey8Off.getText().toString()));
        Log.v("mKey9Off n", ""+(short)Integer.parseInt(mKey9Off.getText().toString()));
        Log.v("mKey10Off n", ""+(short)Integer.parseInt(mKey10Off.getText().toString()));
        Log.v("mKey11Off n", ""+(short)Integer.parseInt(mKey11Off.getText().toString()));
        Log.v("mKey12Off n", ""+(short)Integer.parseInt(mKey12Off.getText().toString()));

        byte[] value = new byte[6];
        short sVal = (short)Integer.parseInt(mKey1Off.getText().toString());
        value[0] = (byte) 0x20;//Set key offset
        value[1] = (byte) RadioOption;//Module
        value[2] = (byte) 0x01;//Key
        value[3] = (byte) (sVal >>> 8);
        value[4] = (byte) sVal;
        value[5] = (byte) 0x00;
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);

        Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                byte[] value = new byte[6];
                short sVal = (short)Integer.parseInt(mKey2Off.getText().toString());
                value[0] = (byte) 0x20;//Set key offset
                value[1] = (byte) RadioOption;//Module
                value[2] = (byte) 0x02;//Key
                value[3] = (byte) (sVal >>> 8);
                value[4] = (byte) sVal;
                value[5] = (byte) 0x00;
                mRxCharacteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                Handler handler3 = new Handler();
                handler3.postDelayed(new Runnable()
                {
                    @Override
                    public void run() {
                        byte[] value = new byte[6];
                        short sVal = (short)Integer.parseInt(mKey3Off.getText().toString());
                        value[0] = (byte) 0x20;//Set key offset
                        value[1] = (byte) RadioOption;//Module
                        value[2] = (byte) 0x03;//Key
                        value[3] = (byte) (sVal >>> 8);
                        value[4] = (byte) sVal;
                        value[5] = (byte) 0x00;
                        mRxCharacteristic.setValue(value);
                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                        Handler handler4 = new Handler();
                        handler4.postDelayed(new Runnable()
                        {
                            @Override
                            public void run() {
                                byte[] value = new byte[6];
                                short sVal = (short)Integer.parseInt(mKey4Off.getText().toString());
                                value[0] = (byte) 0x20;//Set key offset
                                value[1] = (byte) RadioOption;//Module
                                value[2] = (byte) 0x04;//Key
                                value[3] = (byte) (sVal >>> 8);
                                value[4] = (byte) sVal;
                                value[5] = (byte) 0x00;
                                mRxCharacteristic.setValue(value);
                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                Handler handler5 = new Handler();
                                handler5.postDelayed(new Runnable()
                                {
                                    @Override
                                    public void run() {
                                        byte[] value = new byte[6];
                                        short sVal = (short)Integer.parseInt(mKey5Off.getText().toString());
                                        value[0] = (byte) 0x20;//Set key offset
                                        value[1] = (byte) RadioOption;//Module
                                        value[2] = (byte) 0x05;//Key
                                        value[3] = (byte) (sVal >>> 8);
                                        value[4] = (byte) sVal;
                                        value[5] = (byte) 0x00;
                                        mRxCharacteristic.setValue(value);
                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                        Handler handler6 = new Handler();
                                        handler6.postDelayed(new Runnable()
                                        {
                                            @Override
                                            public void run() {
                                                byte[] value = new byte[6];
                                                short sVal = (short)Integer.parseInt(mKey6Off.getText().toString());
                                                value[0] = (byte) 0x20;//Set key offset
                                                value[1] = (byte) RadioOption;//Module
                                                value[2] = (byte) 0x06;//Key
                                                value[3] = (byte) (sVal >>> 8);
                                                value[4] = (byte) sVal;
                                                value[5] = (byte) 0x00;
                                                mRxCharacteristic.setValue(value);
                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                Handler handler7 = new Handler();
                                                handler7.postDelayed(new Runnable()
                                                {
                                                    @Override
                                                    public void run() {
                                                        byte[] value = new byte[6];
                                                        short sVal = (short)Integer.parseInt(mKey7Off.getText().toString());
                                                        value[0] = (byte) 0x20;//Set key offset
                                                        value[1] = (byte) RadioOption;//Module
                                                        value[2] = (byte) 0x07;//Key
                                                        value[3] = (byte) (sVal >>> 8);
                                                        value[4] = (byte) sVal;
                                                        value[5] = (byte) 0x00;
                                                        mRxCharacteristic.setValue(value);
                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                        Handler handler8 = new Handler();
                                                        handler8.postDelayed(new Runnable()
                                                        {
                                                            @Override
                                                            public void run() {
                                                                byte[] value = new byte[6];
                                                                short sVal = (short)Integer.parseInt(mKey8Off.getText().toString());
                                                                value[0] = (byte) 0x20;//Set key offset
                                                                value[1] = (byte) RadioOption;//Module
                                                                value[2] = (byte) 0x08;//Key
                                                                value[3] = (byte) (sVal >>> 8);
                                                                value[4] = (byte) sVal;
                                                                value[5] = (byte) 0x00;
                                                                mRxCharacteristic.setValue(value);
                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                Handler handler9 = new Handler();
                                                                handler9.postDelayed(new Runnable()
                                                                {
                                                                    @Override
                                                                    public void run() {
                                                                        byte[] value = new byte[6];
                                                                        short sVal = (short)Integer.parseInt(mKey9Off.getText().toString());
                                                                        value[0] = (byte) 0x20;//Set key offset
                                                                        value[1] = (byte) RadioOption;//Module
                                                                        value[2] = (byte) 0x09;//Key
                                                                        value[3] = (byte) (sVal >>> 8);
                                                                        value[4] = (byte) sVal;
                                                                        value[5] = (byte) 0x00;
                                                                        mRxCharacteristic.setValue(value);
                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                        Handler handler10 = new Handler();
                                                                        handler10.postDelayed(new Runnable()
                                                                        {
                                                                            @Override
                                                                            public void run() {
                                                                                byte[] value = new byte[6];
                                                                                short sVal = (short)Integer.parseInt(mKey10Off.getText().toString());
                                                                                value[0] = (byte) 0x20;//Set key offset
                                                                                value[1] = (byte) RadioOption;//Module
                                                                                value[2] = (byte) 0x0A;//Key
                                                                                value[3] = (byte) (sVal >>> 8);
                                                                                value[4] = (byte) sVal;
                                                                                value[5] = (byte) 0x00;
                                                                                mRxCharacteristic.setValue(value);
                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                Handler handler11 = new Handler();
                                                                                handler11.postDelayed(new Runnable()
                                                                                {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        byte[] value = new byte[6];
                                                                                        short sVal = (short)Integer.parseInt(mKey11Off.getText().toString());
                                                                                        value[0] = (byte) 0x20;//Set key offset
                                                                                        value[1] = (byte) RadioOption;//Module
                                                                                        value[2] = (byte) 0x0B;//Key
                                                                                        value[3] = (byte) (sVal >>> 8);
                                                                                        value[4] = (byte) sVal;
                                                                                        value[5] = (byte) 0x00;
                                                                                        mRxCharacteristic.setValue(value);
                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                        Handler handler12 = new Handler();
                                                                                        handler12.postDelayed(new Runnable()
                                                                                        {
                                                                                            @Override
                                                                                            public void run() {
                                                                                                byte[] value = new byte[6];
                                                                                                short sVal = (short)Integer.parseInt(mKey12Off.getText().toString());
                                                                                                value[0] = (byte) 0x20;//Set key offset
                                                                                                value[1] = (byte) RadioOption;//Module
                                                                                                value[2] = (byte) 0x0C;//Key
                                                                                                value[3] = (byte) (sVal >>> 8);
                                                                                                value[4] = (byte) sVal;
                                                                                                value[5] = (byte) 0x00;
                                                                                                mRxCharacteristic.setValue(value);
                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                            }
                                                                                        }, 100);
                                                                                    }
                                                                                }, 100);
                                                                            }
                                                                        }, 100);
                                                                    }
                                                                }, 100);
                                                            }
                                                        }, 100);
                                                    }
                                                }, 100);
                                            }
                                        }, 100);
                                    }
                                }, 100);
                            }
                        }, 100);
                    }
                }, 100);
            }
        }, 100);






    }

    public void sendReadOffset(View view) {
        if (mRxCharacteristic == null) {
            Log.e(TAG, "char not found!");
            return;
        }

        byte[] value = new byte[6];
        value[0] = (byte) 0x21;//Get key offset
        value[1] = (byte) RadioOption;//Module
        value[2] = (byte) 0x01;//Key
        value[3] = (byte) 0x00;
        value[4] = (byte) 0x00;
        value[5] = (byte) 0x00;
        mRxCharacteristic.setValue(value);
        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);

        Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable()
        {
            @Override
            public void run() {
                byte[] value = new byte[6];
                value[0] = (byte) 0x21;
                value[1] = (byte) RadioOption;//Module
                value[2] = (byte) 0x02;//Key
                value[3] = (byte) 0x00;
                value[4] = (byte) 0x00;
                value[5] = (byte) 0x00;
                mRxCharacteristic.setValue(value);
                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                Handler handler3 = new Handler();
                handler3.postDelayed(new Runnable()
                {
                    @Override
                    public void run() {
                        byte[] value = new byte[6];
                        value[0] = (byte) 0x21;
                        value[1] = (byte) RadioOption;//Module
                        value[2] = (byte) 0x03;//Key
                        value[3] = (byte) 0x00;
                        value[4] = (byte) 0x00;
                        value[5] = (byte) 0x00;
                        mRxCharacteristic.setValue(value);
                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                        Handler handler4 = new Handler();
                        handler4.postDelayed(new Runnable()
                        {
                            @Override
                            public void run() {
                                byte[] value = new byte[6];
                                value[0] = (byte) 0x21;
                                value[1] = (byte) RadioOption;//Module
                                value[2] = (byte) 0x04;//Key
                                value[3] = (byte) 0x00;
                                value[4] = (byte) 0x00;
                                value[5] = (byte) 0x00;
                                mRxCharacteristic.setValue(value);
                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                Handler handler5 = new Handler();
                                handler5.postDelayed(new Runnable()
                                {
                                    @Override
                                    public void run() {
                                        byte[] value = new byte[6];
                                        value[0] = (byte) 0x21;
                                        value[1] = (byte) RadioOption;//Module
                                        value[2] = (byte) 0x05;//Key
                                        value[3] = (byte) 0x00;
                                        value[4] = (byte) 0x00;
                                        value[5] = (byte) 0x00;
                                        mRxCharacteristic.setValue(value);
                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                        Handler handler6 = new Handler();
                                        handler6.postDelayed(new Runnable()
                                        {
                                            @Override
                                            public void run() {
                                                byte[] value = new byte[6];
                                                value[0] = (byte) 0x21;
                                                value[1] = (byte) RadioOption;//Module
                                                value[2] = (byte) 0x06;//Key
                                                value[3] = (byte) 0x00;
                                                value[4] = (byte) 0x00;
                                                value[5] = (byte) 0x00;
                                                mRxCharacteristic.setValue(value);
                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                Handler handler7 = new Handler();
                                                handler7.postDelayed(new Runnable()
                                                {
                                                    @Override
                                                    public void run() {
                                                        byte[] value = new byte[6];
                                                        value[0] = (byte) 0x21;
                                                        value[1] = (byte) RadioOption;//Module
                                                        value[2] = (byte) 0x07;//Key
                                                        value[3] = (byte) 0x00;
                                                        value[4] = (byte) 0x00;
                                                        value[5] = (byte) 0x00;
                                                        mRxCharacteristic.setValue(value);
                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                        Handler handler8 = new Handler();
                                                        handler8.postDelayed(new Runnable()
                                                        {
                                                            @Override
                                                            public void run() {
                                                                byte[] value = new byte[6];
                                                                value[0] = (byte) 0x21;
                                                                value[1] = (byte) RadioOption;//Module
                                                                value[2] = (byte) 0x08;//Key
                                                                value[3] = (byte) 0x00;
                                                                value[4] = (byte) 0x00;
                                                                value[5] = (byte) 0x00;
                                                                mRxCharacteristic.setValue(value);
                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                Handler handler9 = new Handler();
                                                                handler9.postDelayed(new Runnable()
                                                                {
                                                                    @Override
                                                                    public void run() {
                                                                        byte[] value = new byte[6];
                                                                        value[0] = (byte) 0x21;
                                                                        value[1] = (byte) RadioOption;//Module
                                                                        value[2] = (byte) 0x09;//Key
                                                                        value[3] = (byte) 0x00;
                                                                        value[4] = (byte) 0x00;
                                                                        value[5] = (byte) 0x00;
                                                                        mRxCharacteristic.setValue(value);
                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                        Handler handler10 = new Handler();
                                                                        handler10.postDelayed(new Runnable()
                                                                        {
                                                                            @Override
                                                                            public void run() {
                                                                                byte[] value = new byte[6];
                                                                                value[0] = (byte) 0x21;
                                                                                value[1] = (byte) RadioOption;//Module
                                                                                value[2] = (byte) 0x0A;//Key
                                                                                value[3] = (byte) 0x00;
                                                                                value[4] = (byte) 0x00;
                                                                                value[5] = (byte) 0x00;
                                                                                mRxCharacteristic.setValue(value);
                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                Handler handler11 = new Handler();
                                                                                handler11.postDelayed(new Runnable()
                                                                                {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        byte[] value = new byte[6];
                                                                                        value[0] = (byte) 0x21;
                                                                                        value[1] = (byte) RadioOption;//Module
                                                                                        value[2] = (byte) 0x0B;//Key
                                                                                        value[3] = (byte) 0x00;
                                                                                        value[4] = (byte) 0x00;
                                                                                        value[5] = (byte) 0x00;
                                                                                        mRxCharacteristic.setValue(value);
                                                                                        mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                        Handler handler12 = new Handler();
                                                                                        handler12.postDelayed(new Runnable()
                                                                                        {
                                                                                            @Override
                                                                                            public void run() {
                                                                                                byte[] value = new byte[6];
                                                                                                value[0] = (byte) 0x21;
                                                                                                value[1] = (byte) RadioOption;//Module
                                                                                                value[2] = (byte) 0x0C;//Key
                                                                                                value[3] = (byte) 0x00;
                                                                                                value[4] = (byte) 0x00;
                                                                                                value[5] = (byte) 0x00;
                                                                                                mRxCharacteristic.setValue(value);
                                                                                                mBluetoothLeService.writeCharacteristic(mRxCharacteristic);
                                                                                            }
                                                                                        }, 100);
                                                                                    }
                                                                                }, 100);
                                                                            }
                                                                        }, 100);
                                                                    }
                                                                }, 100);
                                                            }
                                                        }, 100);
                                                    }
                                                }, 100);
                                            }
                                        }, 100);
                                    }
                                }, 100);
                            }
                        }, 100);
                    }
                }, 100);
            }
        }, 100);


    }

    class MyTask extends AsyncTask<Integer, Integer, String> {
        @Override
        protected String doInBackground(Integer... params) {
            return "File sent.";
        }

        @Override
        protected void onPostExecute(String result) {
            txt.setText(result);
            setContentView(R.layout.activity_device_control);
        }

        @Override
        protected void onPreExecute() {
            txt.setText("Task Starting...");
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            txt.setText("Running..." + values[0]);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
