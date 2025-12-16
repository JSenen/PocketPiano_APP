package com.espressif.ble;

import android.app.*;
import android.bluetooth.*;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BleForegroundService extends Service {

    public static final String ACTION_START = "BLE_START";
    public static final String ACTION_STOP  = "BLE_STOP";
    public static final String EXTRA_MAC    = "EXTRA_MAC";

    private static final String NOTIF_CHANNEL_ID = "ble_channel";
    private static final int NOTIF_ID = 1001;

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothGatt gatt;

    // Operation queue (important)
    private final Queue<Runnable> opQueue = new ConcurrentLinkedQueue<>();
    private boolean busy = false;

    // State + listeners
    private final List<BleListener> listeners = new ArrayList<>();
    private String connectedMac;

    public interface BleListener {
        void onConnectionState(boolean connected, @Nullable String mac);
        void onMtuChanged(int mtu);
        void onServicesDiscovered(List<BluetoothGattService> services);
        void onNotify(UUID serviceUuid, UUID charUuid, byte[] value);
        void onRead(UUID serviceUuid, UUID charUuid, byte[] value);
        void onWrite(UUID serviceUuid, UUID charUuid, int status);
        void onError(String where, int status);
    }

    public class LocalBinder extends Binder {
        public BleForegroundService getService() { return BleForegroundService.this; }
    }

    private final IBinder binder = new LocalBinder();

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager != null ? btManager.getAdapter() : null;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String mac = intent.getStringExtra(EXTRA_MAC);
            if (mac != null) {
                startInForeground("Conectando a " + mac);
                connect(mac);
            }
        } else if (ACTION_STOP.equals(action)) {
            disconnect();
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    public void addListener(BleListener l) {
        if (l == null) return;
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(BleListener l) {
        listeners.remove(l);
    }

    public boolean isConnected() {
        return gatt != null;
    }

    public String getConnectedMac() {
        return connectedMac;
    }

    public void connect(String mac) {
        if (btAdapter == null) {
            emitError("connect:adapter_null", -1);
            return;
        }
        disconnect(); // ensure single gatt

        connectedMac = mac;
        BluetoothDevice device;
        try {
            device = btAdapter.getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            emitError("connect:bad_mac", -2);
            return;
        }

        // autoConnect = false for faster / more reliable initial connect
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        updateNotification("Conectando a " + mac);
    }

    public void disconnect() {
        busy = false;
        opQueue.clear();

        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        if (connectedMac != null) {
            emitConnection(false, connectedMac);
        }
        connectedMac = null;
        updateNotification("Desconectado");
    }

    public void requestMtu(int mtu) {
        enqueue(() -> {
            if (gatt == null) return;
            boolean ok = gatt.requestMtu(mtu);
            if (!ok) {
                emitError("requestMtu", -3);
                next();
            }
        });
    }

    public void discoverServices() {
        enqueue(() -> {
            if (gatt == null) return;
            boolean ok = gatt.discoverServices();
            if (!ok) {
                emitError("discoverServices", -4);
                next();
            }
        });
    }

    public void setNotify(UUID serviceUuid, UUID charUuid, boolean enable) {
        enqueue(() -> {
            if (gatt == null) return;
            BluetoothGattService s = gatt.getService(serviceUuid);
            if (s == null) { emitError("setNotify:no_service", -10); next(); return; }

            BluetoothGattCharacteristic c = s.getCharacteristic(charUuid);
            if (c == null) { emitError("setNotify:no_char", -11); next(); return; }

            boolean ok = gatt.setCharacteristicNotification(c, enable);
            if (!ok) { emitError("setNotify:setCharacteristicNotification", -12); next(); return; }

            // Enable CCCD descriptor if present
            BluetoothGattDescriptor cccd = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (cccd != null) {
                cccd.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                boolean ok2 = gatt.writeDescriptor(cccd);
                if (!ok2) { emitError("setNotify:writeDescriptor", -13); next(); }
                // next() will be called onDescriptorWrite
            } else {
                // some devices don't require CCCD
                next();
            }
        });
    }

    public void read(UUID serviceUuid, UUID charUuid) {
        enqueue(() -> {
            if (gatt == null) return;
            BluetoothGattService s = gatt.getService(serviceUuid);
            if (s == null) { emitError("read:no_service", -20); next(); return; }
            BluetoothGattCharacteristic c = s.getCharacteristic(charUuid);
            if (c == null) { emitError("read:no_char", -21); next(); return; }
            boolean ok = gatt.readCharacteristic(c);
            if (!ok) { emitError("read:readCharacteristic", -22); next(); }
            // next() called onCharacteristicRead
        });
    }

    public void write(UUID serviceUuid, UUID charUuid, byte[] value, int writeType) {
        enqueue(() -> {
            if (gatt == null) return;
            BluetoothGattService s = gatt.getService(serviceUuid);
            if (s == null) { emitError("write:no_service", -30); next(); return; }
            BluetoothGattCharacteristic c = s.getCharacteristic(charUuid);
            if (c == null) { emitError("write:no_char", -31); next(); return; }

            c.setWriteType(writeType);
            c.setValue(value);

            boolean ok = gatt.writeCharacteristic(c);
            if (!ok) { emitError("write:writeCharacteristic", -32); next(); }
            // next() called onCharacteristicWrite
        });
    }

    // ---- Queue helpers ----
    private void enqueue(Runnable op) {
        opQueue.offer(op);
        if (!busy) {
            busy = true;
            Runnable next = opQueue.poll();
            if (next != null) next.run();
            else busy = false;
        }
    }

    private void next() {
        Runnable n = opQueue.poll();
        if (n != null) {
            n.run();
        } else {
            busy = false;
        }
    }

    // ---- GATT callbacks ----
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitError("onConnectionStateChange", status);
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateNotification("Conectado a " + connectedMac);
                emitConnection(true, connectedMac);
                // Typical flow:
                // request MTU then discover services from UI/OTA layer
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateNotification("Desconectado");
                emitConnection(false, connectedMac);
                disconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) emitError("onMtuChanged", status);
            for (BleListener l : snapshot()) l.onMtuChanged(mtu);
            next();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) emitError("onServicesDiscovered", status);
            List<BluetoothGattService> services = g.getServices();
            for (BleListener l : snapshot()) l.onServicesDiscovered(services);
            next();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) emitError("onDescriptorWrite", status);
            next();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) emitError("onCharacteristicRead", status);
            byte[] v = c.getValue();
            for (BleListener l : snapshot()) l.onRead(c.getService().getUuid(), c.getUuid(), v);
            next();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            for (BleListener l : snapshot()) l.onWrite(c.getService().getUuid(), c.getUuid(), status);
            if (status != BluetoothGatt.GATT_SUCCESS) emitError("onCharacteristicWrite", status);
            next();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] v = c.getValue();
            for (BleListener l : snapshot()) l.onNotify(c.getService().getUuid(), c.getUuid(), v);
        }
    };

    private List<BleListener> snapshot() {
        return new ArrayList<>(listeners);
    }

    private void emitConnection(boolean connected, @Nullable String mac) {
        for (BleListener l : snapshot()) l.onConnectionState(connected, mac);
    }

    private void emitError(String where, int status) {
        for (BleListener l : snapshot()) l.onError(where, status);
    }

    // ---- Foreground notification ----
    private void startInForeground(String text) {
        Notification n = buildNotification(text);
        startForeground(NOTIF_ID, n);
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, BleForegroundService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent piStop = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BLE Service")
                .setContentText(text)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(0, "Desconectar", piStop))
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL_ID, "BLE", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}

