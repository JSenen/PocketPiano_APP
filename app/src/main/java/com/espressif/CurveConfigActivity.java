package com.espressif;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.wifi_provisioning.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CurveConfigActivity extends AppCompatActivity {
    private static final String TAG = CurveConfigActivity.class.getSimpleName();
    private static final int REQUEST_CONFIG_FILE = 4201;
    private static final int VALUE_COUNT = 9;
    private static final long WRITE_DELAY_MS = 120L;

    private static final String[] LEVEL_KEYS = {
            "PPPLow", "PPPHigh", "PPHigh", "PHigh", "MPHigh", "MFHigh", "FHigh", "FFHigh", "FFFHigh"
    };

    public static final UUID UUID_NORDIC_UART_CHARACTERISTIC_RX =
            UUID.fromString(SampleGattAttributes.NORDIC_UART_CHARACTERISTIC_RX);

    private TextView deviceNameView;
    private TextView connectionStatusView;
    private TextView fileStatusView;
    private TextView summaryView;
    private ProgressBar progressBar;
    private Button selectFileButton;
    private Button sendConfigButton;

    private BluetoothLeService bluetoothLeService;
    private BluetoothGattCharacteristic rxCharacteristic;
    private String deviceAddress;
    private String deviceName;
    private boolean connected;
    private boolean bound;
    private int[][] curveValues;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!bluetoothLeService.initialize()) {
                Toast.makeText(CurveConfigActivity.this, R.string.bluetooth_is_disabled, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            bound = true;
            bluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
            bluetoothLeService = null;
        }
    };

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
                updateConnectionState();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                rxCharacteristic = null;
                updateConnectionState();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                findRxCharacteristic(bluetoothLeService.getSupportedGattServices());
                sendUnlockCommand();
                updateConnectionState();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_curve_config);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.curve_config_title);
        }

        Intent intent = getIntent();
        deviceAddress = intent.getStringExtra("device");
        deviceName = intent.getStringExtra("deviceName");
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = deviceAddress;
        }

        deviceNameView = findViewById(R.id.curveDeviceName);
        connectionStatusView = findViewById(R.id.curveConnectionStatus);
        fileStatusView = findViewById(R.id.curveFileStatus);
        summaryView = findViewById(R.id.curveSummary);
        progressBar = findViewById(R.id.curveProgress);
        selectFileButton = findViewById(R.id.buttonSelectCurveFile);
        sendConfigButton = findViewById(R.id.buttonSendCurveConfig);

        deviceNameView.setText(deviceName);
        sendConfigButton.setEnabled(false);
        updateConnectionState();

        selectFileButton.setOnClickListener(view -> selectConfigFile());
        sendConfigButton.setOnClickListener(view -> sendCurveConfig());

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothLeService != null) {
            bluetoothLeService.connect(deviceAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CONFIG_FILE || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        try {
            String content = readText(uri);
            curveValues = parseConfig(content);
            fileStatusView.setText(getDisplayName(uri));
            summaryView.setText(buildSummary(curveValues));
            sendConfigButton.setEnabled(canSend());
            progressBar.setProgress(0);
        } catch (Exception e) {
            curveValues = null;
            sendConfigButton.setEnabled(false);
            fileStatusView.setText(R.string.curve_file_invalid);
            summaryView.setText(e.getMessage());
            Log.e(TAG, "Invalid curve config", e);
        }
    }

    private void selectConfigFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/json", "text/*", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.curve_select_file)), REQUEST_CONFIG_FILE);
    }

    private void findRxCharacteristic(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            return;
        }
        for (BluetoothGattService service : gattServices) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (UUID_NORDIC_UART_CHARACTERISTIC_RX.equals(characteristic.getUuid())) {
                    rxCharacteristic = characteristic;
                    return;
                }
            }
        }
    }

    private void sendUnlockCommand() {
        if (rxCharacteristic == null || bluetoothLeService == null) {
            return;
        }
        writeCommand(new byte[] {
                (byte) 0x00, (byte) 0xEF, (byte) 0x20, (byte) 0x00, (byte) 0x00, (byte) 0x00
        });
    }

    private void sendCurveConfig() {
        if (!canSend()) {
            Toast.makeText(this, R.string.curve_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        sendConfigButton.setEnabled(false);
        progressBar.setMax(VALUE_COUNT * 2);
        progressBar.setProgress(0);
        summaryView.setText(R.string.curve_sending);

        for (int keyType = 0; keyType < 2; keyType++) {
            for (int level = 0; level < VALUE_COUNT; level++) {
                final int currentKeyType = keyType;
                final int currentLevel = level;
                final int step = keyType * VALUE_COUNT + level + 1;
                handler.postDelayed(() -> {
                    writeLevel(currentKeyType, currentLevel, curveValues[currentKeyType][currentLevel]);
                    progressBar.setProgress(step);
                    if (step == VALUE_COUNT * 2) {
                        summaryView.setText(R.string.curve_sent);
                        sendConfigButton.setEnabled(canSend());
                    }
                }, WRITE_DELAY_MS * step);
            }
        }
    }

    private void writeLevel(int keyType, int level, int value) {
        byte[] command = new byte[6];
        command[0] = (byte) 0x25;
        command[1] = (byte) keyType;
        command[2] = (byte) level;
        command[3] = (byte) (value >>> 8);
        command[4] = (byte) value;
        command[5] = (byte) 0x00;
        writeCommand(command);
    }

    private void writeCommand(byte[] command) {
        if (rxCharacteristic == null || bluetoothLeService == null) {
            return;
        }
        rxCharacteristic.setValue(command);
        bluetoothLeService.writeCharacteristic(rxCharacteristic);
    }

    private boolean canSend() {
        return connected && rxCharacteristic != null && curveValues != null;
    }

    private void updateConnectionState() {
        if (connectionStatusView == null) {
            return;
        }
        if (connected && rxCharacteristic != null) {
            connectionStatusView.setText(R.string.connected);
        } else if (connected) {
            connectionStatusView.setText(R.string.curve_discovering);
        } else {
            connectionStatusView.setText(R.string.disconnected);
        }
        if (sendConfigButton != null) {
            sendConfigButton.setEnabled(canSend());
        }
    }

    private String readText(Uri uri) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private int[][] parseConfig(String content) throws JSONException {
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            return parseJsonObject(new JSONObject(trimmed));
        }
        return parseDelimited(trimmed);
    }

    private int[][] parseJsonObject(JSONObject root) throws JSONException {
        int[][] values = newEmptyConfig();
        readJsonKeyGroup(root, values, 0, "white", "w");
        readJsonKeyGroup(root, values, 1, "black", "b");

        for (int level = 0; level < VALUE_COUNT; level++) {
            readOptionalFlatJsonValue(root, values, 0, level, LEVEL_KEYS[level] + "W");
            readOptionalFlatJsonValue(root, values, 1, level, LEVEL_KEYS[level] + "B");
        }

        validateComplete(values);
        return values;
    }

    private void readJsonKeyGroup(JSONObject root, int[][] values, int keyType, String longKey, String shortKey) throws JSONException {
        Object group = null;
        if (root.has(longKey)) {
            group = root.get(longKey);
        } else if (root.has(shortKey)) {
            group = root.get(shortKey);
        }

        if (group instanceof JSONArray) {
            JSONArray array = (JSONArray) group;
            if (array.length() != VALUE_COUNT) {
                throw new JSONException(longKey + " must contain 9 values");
            }
            for (int level = 0; level < VALUE_COUNT; level++) {
                values[keyType][level] = parseValue(array.getInt(level));
            }
        } else if (group instanceof JSONObject) {
            JSONObject object = (JSONObject) group;
            for (int level = 0; level < VALUE_COUNT; level++) {
                if (object.has(LEVEL_KEYS[level])) {
                    values[keyType][level] = parseValue(object.getInt(LEVEL_KEYS[level]));
                }
            }
        }
    }

    private void readOptionalFlatJsonValue(JSONObject root, int[][] values, int keyType, int level, String key) throws JSONException {
        if (root.has(key)) {
            values[keyType][level] = parseValue(root.getInt(key));
        }
    }

    private int[][] parseDelimited(String content) {
        int[][] values = newEmptyConfig();
        String[] lines = content.split("\\r?\\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.contains("=")) {
                parseAssignmentLine(values, line);
            } else {
                parseCsvLine(values, line);
            }
        }

        validateComplete(values);
        return values;
    }

    private void parseAssignmentLine(int[][] values, String line) {
        String[] parts = line.split("=", 2);
        String key = normalize(parts[0]);
        String value = parts[1].trim();

        if ("white".equals(key) || "w".equals(key) || "black".equals(key) || "b".equals(key)) {
            int keyType = ("black".equals(key) || "b".equals(key)) ? 1 : 0;
            String[] items = value.split("[,;]");
            if (items.length != VALUE_COUNT) {
                throw new IllegalArgumentException(parts[0].trim() + " must contain 9 values");
            }
            for (int level = 0; level < VALUE_COUNT; level++) {
                values[keyType][level] = parseValue(Integer.parseInt(items[level].trim()));
            }
            return;
        }

        for (int level = 0; level < VALUE_COUNT; level++) {
            if (key.equals(normalize(LEVEL_KEYS[level] + "W"))) {
                values[0][level] = parseValue(Integer.parseInt(value));
                return;
            }
            if (key.equals(normalize(LEVEL_KEYS[level] + "B"))) {
                values[1][level] = parseValue(Integer.parseInt(value));
                return;
            }
        }

        throw new IllegalArgumentException("Unknown config key: " + parts[0].trim());
    }

    private void parseCsvLine(int[][] values, String line) {
        String[] parts = line.split("[,;]");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid line: " + line);
        }

        int keyType = parseKeyType(parts[0].trim());
        int level = parseLevel(parts[1].trim());
        int value = parseValue(Integer.parseInt(parts[2].trim()));
        values[keyType][level] = value;
    }

    private int parseKeyType(String value) {
        String normalized = normalize(value);
        if ("white".equals(normalized) || "w".equals(normalized) || "0".equals(normalized)) {
            return 0;
        }
        if ("black".equals(normalized) || "b".equals(normalized) || "1".equals(normalized)) {
            return 1;
        }
        throw new IllegalArgumentException("Invalid key type: " + value);
    }

    private int parseLevel(String value) {
        String normalized = normalize(value);
        for (int level = 0; level < VALUE_COUNT; level++) {
            if (normalized.equals(normalize(LEVEL_KEYS[level])) || normalized.equals(String.valueOf(level))) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid level: " + value);
    }

    private int parseValue(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Values must be between 0 and 65535");
        }
        return value;
    }

    private int[][] newEmptyConfig() {
        int[][] values = new int[2][VALUE_COUNT];
        for (int keyType = 0; keyType < 2; keyType++) {
            for (int level = 0; level < VALUE_COUNT; level++) {
                values[keyType][level] = -1;
            }
        }
        return values;
    }

    private void validateComplete(int[][] values) {
        for (int keyType = 0; keyType < 2; keyType++) {
            for (int level = 0; level < VALUE_COUNT; level++) {
                if (values[keyType][level] < 0) {
                    String color = keyType == 0 ? "white" : "black";
                    throw new IllegalArgumentException("Missing " + color + " value for " + LEVEL_KEYS[level]);
                }
            }
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.US).replace("_", "").replace("-", "");
    }

    private String buildSummary(int[][] values) {
        return "White: " + joinValues(values[0]) + "\nBlack: " + joinValues(values[1]);
    }

    private String joinValues(int[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }

    @SuppressLint("Range")
    private String getDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        return name == null ? getString(R.string.curve_file_loaded) : name;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }
}
