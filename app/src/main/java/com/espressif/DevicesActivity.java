package com.espressif;



import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.espressif.AppConstants;
import com.espressif.ble.BleForegroundService;
import com.espressif.ble.BleScanner;

import java.util.*;

public class DevicesActivity extends AppCompatActivity {

    private BleScanner scanner;
    private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
    private ArrayAdapter<String> adapter;

    private String mode;

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean ok = true;
                for (Boolean v : result.values()) ok &= (v != null && v);
                if (ok) startScan();
                else Toast.makeText(this, "Permisos BLE denegados", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UI simple (por rapidez). Luego lo dejamos bonito con RecyclerView.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button btnScan = new Button(this);
        btnScan.setText("Escanear");
        ListView list = new ListView(this);
        root.addView(btnScan);
        root.addView(list);
        setContentView(root);

        mode = getSharedPreferences(AppConstants.PREFS, MODE_PRIVATE)
                .getString(AppConstants.KEY_MODE, AppConstants.MODE_CONTROL);

        scanner = new BleScanner(this);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);

        btnScan.setOnClickListener(v -> ensurePermsAndScan());

        list.setOnItemClickListener((parent, view, position, id) -> {
            String line = adapter.getItem(position);
            if (line == null) return;
            // la línea empieza por MAC
            String mac = line.split(" ")[0].trim();
            BluetoothDevice d = devices.get(mac);
            if (d == null) return;

            startBleService(mac);

            Intent next;
            if (AppConstants.MODE_OTA.equals(mode)) {
                next = new Intent(this, OtaActivity.class);
            } else {
                next = new Intent(this, ControlActivity.class);
            }
            next.putExtra(AppConstants.EXTRA_MAC, mac);
            next.putExtra(AppConstants.EXTRA_NAME, d.getName());
            startActivity(next);
        });

        ensurePermsAndScan();
    }

    private void ensurePermsAndScan() {
        List<String> perms = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        boolean need = false;
        for (String p : perms) {
            need |= ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED;
        }

        if (need) {
            permLauncher.launch(perms.toArray(new String[0]));
        } else {
            startScan();
        }
    }

    private void startScan() {
        if (!scanner.isBluetoothReady()) {
            Toast.makeText(this, "Activa Bluetooth para escanear", Toast.LENGTH_LONG).show();
            return;
        }

        devices.clear();
        adapter.clear();

        scanner.start(new BleScanner.ScanListener() {
            @Override public void onDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
                String mac = device.getAddress();
                if (!devices.containsKey(mac)) {
                    devices.put(mac, device);
                    String name = device.getName();
                    if (name == null || name.trim().isEmpty()) name = "(sin nombre)";
                    adapter.add(mac + "  " + name + "  RSSI " + rssi);
                }
            }
            @Override public void onScanState(boolean scanning) {}
            @Override public void onError(String msg) {
                Toast.makeText(DevicesActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startBleService(String mac) {
        Intent i = new Intent(this, BleForegroundService.class);
        i.setAction(BleForegroundService.ACTION_START);
        i.putExtra(BleForegroundService.EXTRA_MAC, mac);

        // En Android 8+ recomendable startForegroundService
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) {
            scanner.stop(new BleScanner.ScanListener() {
                @Override public void onDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {}
                @Override public void onScanState(boolean scanning) {}
                @Override public void onError(String msg) {}
            });
        }
    }
}


