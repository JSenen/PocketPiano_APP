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

package com.espressif;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import static com.espressif.SampleGattAttributes.*;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String TX_DATA =
            "com.example.bluetooth.le.tx_data";
    public final static String RX_DATA =
            "com.example.bluetooth.le.rx_data";
    public final static String MIDI_DATA =
            "com.example.bluetooth.le.midi_data";

    public final static UUID UUID_NUS_CHARACTERISTIC_TX =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_CHARACTERISTIC_TX);
    public final static UUID UUID_NUS_CHARACTERISTIC_RX =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_CHARACTERISTIC_RX);
    public final static UUID UUID_MIDI_CHARACTERISTIC =
            UUID.fromString(SampleGattAttributes.MIDI_CHARACTERISTIC);

    private static final Queue<Object> BleQueue = new LinkedList<>();

    /**
     * This handles the BLE Queue. If the queue is not empty, it starts the next event.
     */
    @SuppressLint("MissingPermission")
    private void handleBleQueue() {
        if(BleQueue.size() > 0) {
            // Determine which type of event is next and fire it off
            if (BleQueue.element() instanceof BluetoothGattDescriptor) {
                mBluetoothGatt.writeDescriptor((BluetoothGattDescriptor) BleQueue.element());
            } else if (BleQueue.element() instanceof BluetoothGattCharacteristic) {
                mBluetoothGatt.writeCharacteristic((BluetoothGattCharacteristic) BleQueue.element());
            }
        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.d(TAG, "onConnectionStateChange");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered received: " + status);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        /**
         * This is called when a CCCD write has completed. It uses a queue to determine if
         * additional BLE actions are still pending and launches the next one if there are.
         *
         * @param gatt The GATT database object
         * @param descriptor The CCCD that was written.
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d("onDescriptorWrite","status:" +status);
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                // Pop the item that was written from the queue
                BleQueue.remove();
                // See if there are more items in the BLE queues
                handleBleQueue();
            }
        }

        /**
         * This is called when a characteristic write has completed. Is uses a queue to determine if
         * additional BLE actions are still pending and launches the next one if there are.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was written.
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d("onCharacteristicWrite","status:" +status);
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                // Pop the item that was written from the queue
                BleQueue.remove();
                // See if there are more items in the BLE queues
                handleBleQueue();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        Log.d(TAG, "broadcastUpdate 1");
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        Log.d(TAG, "broadcastUpdate " +characteristic.getUuid());
        if (UUID_NUS_CHARACTERISTIC_RX.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, "RX " +stringBuilder.toString());
                intent.putExtra(RX_DATA, stringBuilder.toString());
            }
        }
        else if (UUID_NUS_CHARACTERISTIC_TX.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, "TX " +stringBuilder.toString());
                intent.putExtra(TX_DATA, stringBuilder.toString());
				if (data.length == 6)
				{
	                Log.d(TAG, "data length " +data.length);
					if (data[0]==0x0F)		// Software version
					{
						if (DeviceControlActivity.RadioOption==data[1])	//Module
						{
							DeviceControlActivity.mSoftver_recep.setText(data[2]+"."+data[3]);
						}
					}
					else if (data[0]==0x11)	// Serial number
					{
						if (DeviceControlActivity.RadioOption==data[1])	//Module
						{
							long serialnumb = ((data[2] & 0xFFL) << 24)|((data[3] & 0xFFL) << 16)|((data[4] & 0xFFL) << 8)|(data[5] & 0xFFL);
							DeviceControlActivity.mSerialnumb_recep.setText(""+serialnumb);
						}
					}
					else if (data[0]==0x27)	// Key time level
					{
						long TimeVal = ((data[3] & 0xFFL) << 8)|(data[4] & 0xFFL);
		                Log.d(TAG, "Key time level " +TimeVal);
                        Log.d(TAG, "Data1 " +data[1]);
                        Log.d(TAG, "Data2 " +data[2]);
						if (data[1]==0) // 0-White / 1-Black
						{
							switch(data[2])	// Level
							{
								case 0: // PPP Low
										DeviceControlActivity.mPPPLowW.setText(""+TimeVal);
									break;
								case 1: // PPP High
										DeviceControlActivity.mPPPHighW.setText(""+TimeVal);
									break;
								case 2: // PP High
										DeviceControlActivity.mPPHighW.setText(""+TimeVal);
									break;
								case 3: // P High
										DeviceControlActivity.mPHighW.setText(""+TimeVal);
									break;
								case 4: // MP High
										DeviceControlActivity.mMPHighW.setText(""+TimeVal);
									break;
								case 5: // MF High
										DeviceControlActivity.mMFHighW.setText(""+TimeVal);
									break;
								case 6: // F High
										DeviceControlActivity.mFHighW.setText(""+TimeVal);
									break;
								case 7: // FF High
										DeviceControlActivity.mFFHighW.setText(""+TimeVal);
									break;
								case 8: // FFF High
										DeviceControlActivity.mFFFHighW.setText(""+TimeVal);
									break;
							}
						}
						else // Black
						{
							switch(data[2])	// Level
							{
								case 0: // PPP Low
										DeviceControlActivity.mPPPLowB.setText(""+TimeVal);
									break;
								case 1: // PPP High
										DeviceControlActivity.mPPPHighB.setText(""+TimeVal);
									break;
								case 2: // PP High
										DeviceControlActivity.mPPHighB.setText(""+TimeVal);
									break;
								case 3: // P High
										DeviceControlActivity.mPHighB.setText(""+TimeVal);
									break;
								case 4: // MP High
										DeviceControlActivity.mMPHighB.setText(""+TimeVal);
									break;
								case 5: // MF High
										DeviceControlActivity.mMFHighB.setText(""+TimeVal);
									break;
								case 6: // F High
										DeviceControlActivity.mFHighB.setText(""+TimeVal);
									break;
								case 7: // FF High
										DeviceControlActivity.mFFHighB.setText(""+TimeVal);
									break;
								case 8: // FFF High
										DeviceControlActivity.mFFFHighB.setText(""+TimeVal);
									break;
							}
						}
					}
					else if (data[0]==0x24)	// Key time
					{
						if (DeviceControlActivity.RadioOption==data[1])	//Module
						{
							long TimeVal = ((data[3] & 0xFFL) << 8)|(data[4] & 0xFFL);
			                Log.d(TAG, "Key time " +TimeVal);
	                        Log.d(TAG, "Module " +data[1]);
	                        Log.d(TAG, "Key " +data[2]);
							switch (data[2]) // Key
							{
								case 1:
										DeviceControlActivity.mKey1Val.setText(""+TimeVal);
									break;
								case 2:
										DeviceControlActivity.mKey2Val.setText(""+TimeVal);
									break;
								case 3:
										DeviceControlActivity.mKey3Val.setText(""+TimeVal);
									break;
								case 4:
										DeviceControlActivity.mKey4Val.setText(""+TimeVal);
									break;
								case 5:
										DeviceControlActivity.mKey5Val.setText(""+TimeVal);
									break;
								case 6:
										DeviceControlActivity.mKey6Val.setText(""+TimeVal);
									break;
								case 7:
										DeviceControlActivity.mKey7Val.setText(""+TimeVal);
									break;
								case 8:
										DeviceControlActivity.mKey8Val.setText(""+TimeVal);
									break;
								case 9:
										DeviceControlActivity.mKey9Val.setText(""+TimeVal);
									break;
								case 10:
										DeviceControlActivity.mKey10Val.setText(""+TimeVal);
									break;
								case 11:
										DeviceControlActivity.mKey11Val.setText(""+TimeVal);
									break;
								case 12:
										DeviceControlActivity.mKey12Val.setText(""+TimeVal);
									break;
							}
						}
					}
					else if (data[0]==0x22)	// Key offset
					{
						if (DeviceControlActivity.RadioOption==data[1])	//Module
						{
							short TimeOff = (short)(((data[3] & 0xFFL) << 8)|(data[4] & 0xFFL));
			                Log.d(TAG, "Key offset " +TimeOff);
	                        Log.d(TAG, "Module " +data[1]);
	                        Log.d(TAG, "Key " +data[2]);
							switch (data[2]) // Key
							{
								case 1:
										DeviceControlActivity.mKey1Off.setText(""+TimeOff);
									break;
								case 2:
										DeviceControlActivity.mKey2Off.setText(""+TimeOff);
									break;
								case 3:
										DeviceControlActivity.mKey3Off.setText(""+TimeOff);
									break;
								case 4:
										DeviceControlActivity.mKey4Off.setText(""+TimeOff);
									break;
								case 5:
										DeviceControlActivity.mKey5Off.setText(""+TimeOff);
									break;
								case 6:
										DeviceControlActivity.mKey6Off.setText(""+TimeOff);
									break;
								case 7:
										DeviceControlActivity.mKey7Off.setText(""+TimeOff);
									break;
								case 8:
										DeviceControlActivity.mKey8Off.setText(""+TimeOff);
									break;
								case 9:
										DeviceControlActivity.mKey9Off.setText(""+TimeOff);
									break;
								case 10:
										DeviceControlActivity.mKey10Off.setText(""+TimeOff);
									break;
								case 11:
										DeviceControlActivity.mKey11Off.setText(""+TimeOff);
									break;
								case 12:
										DeviceControlActivity.mKey12Off.setText(""+TimeOff);
									break;
							}
						}
					}
				}
            }
        }
        else if (UUID_MIDI_CHARACTERISTIC.equals(characteristic.getUuid())) {
            Log.d(TAG, "MIDI received");
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, "MIDI " +stringBuilder.toString());
                intent.putExtra(MIDI_DATA, stringBuilder.toString());
            	}
        }
        else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    @SuppressLint("MissingPermission")
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    @SuppressLint("MissingPermission")
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    @SuppressLint("MissingPermission")
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        Log.d(TAG, "setCharacteristicNotification " +characteristic.getUuid());
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//        mBluetoothGatt.writeDescriptor(descriptor);
        // Put the descriptor into the write queue
        BleQueue.add(descriptor);
        // If there is only 1 item in the queue, then write it. If more than one, then the callback
        // will handle it
        if (BleQueue.size() == 1) {
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.i(TAG, "Writing Notification");
        }
    }

    /**
     * Request a write on a given {@code BluetoothGattCharacteristic}. The write result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to write to.
     */
    @SuppressLint("MissingPermission")
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "writeCharacteristic");

        BleQueue.add(characteristic);
        if (BleQueue.size() == 1) {
            mBluetoothGatt.writeCharacteristic(characteristic);
            Log.i(TAG, "Writing Characteristic");
        }
//        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        Log.d(TAG, "getSupportedGattServices");
        return mBluetoothGatt.getServices();
    }
}
