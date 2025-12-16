package com.espressif.ble;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.*;

public class BleScanner {

    public interface ScanListener {
        void onDevice(BluetoothDevice device, int rssi, byte[] scanRecord);
        void onScanState(boolean scanning);
        void onError(String msg);
    }

    private final BluetoothAdapter adapter;
    private final BluetoothLeScanner scanner;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ScanCallback callback;
    private boolean scanning;

    public BleScanner(Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm != null ? bm.getAdapter() : null;
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;
    }

    public boolean isBluetoothReady() {
        return adapter != null && adapter.isEnabled() && scanner != null;
    }

    public void start(ScanListener listener) {
        if (!isBluetoothReady()) {
            listener.onError("Bluetooth no disponible o apagado");
            return;
        }
        if (scanning) return;

        callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (result == null || result.getDevice() == null) return;
                byte[] rec = result.getScanRecord() != null ? result.getScanRecord().getBytes() : null;
                listener.onDevice(result.getDevice(), result.getRssi(), rec);
            }

            @Override
            public void onScanFailed(int errorCode) {
                listener.onError("Scan failed: " + errorCode);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Sin filtros por ahora (lista todo). Luego filtramos por nombre/servicio si quieres.
        List<ScanFilter> filters = new ArrayList<>();

        scanner.startScan(filters, settings, callback);
        scanning = true;
        listener.onScanState(true);
    }

    public void stop(ScanListener listener) {
        if (!scanning) return;
        try {
            if (scanner != null && callback != null) scanner.stopScan(callback);
        } catch (Exception ignored) {}
        scanning = false;
        listener.onScanState(false);
    }
}

