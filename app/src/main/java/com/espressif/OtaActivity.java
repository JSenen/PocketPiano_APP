package com.espressif;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.ble.BleForegroundService;
import com.espressif.ota.OtaEngine;

import java.io.InputStream;

public class OtaActivity extends AppCompatActivity implements BleForegroundService.BleListener, OtaEngine.Listener {

    private TextView tvLog;
    private ProgressBar progress;
    private Button btnSelect;

    private BleForegroundService service;
    private OtaEngine ota;
    private boolean bound = false;

    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((BleForegroundService.LocalBinder) binder).getService();
            bound = true;

            service.addListener(OtaActivity.this);

            service.requestMtu(AppConstants.OTA_MTU);
            service.discoverServices();
            service.setNotify(AppConstants.OTA_SERVICE, AppConstants.OTA_PROGRESS, true);

            ota = new OtaEngine(service, OtaActivity.this);
            log("BLE listo");
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        btnSelect = new Button(this);
        btnSelect.setText("Seleccionar firmware (.bin)");

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);

        tvLog = new TextView(this);

        root.addView(btnSelect);
        root.addView(progress);
        root.addView(tvLog,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1f));

        setContentView(root);

        btnSelect.setOnClickListener(v -> filePicker.launch("*/*"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(
                new Intent(this, com.espressif.ble.BleForegroundService.class),
                conn,
                Context.BIND_AUTO_CREATE
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound && service != null) {
            service.removeListener(this);
            unbindService(conn);
        }
        bound = false;
    }

    // ---------- File ----------
    private void onFilePicked(Uri uri) {
        if (uri == null || ota == null) return;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            byte[] data = new byte[in.available()];
            int read = in.read(data);
            log("Firmware cargado (" + read + " bytes)");
            ota.start(data);
        } catch (Exception e) {
            log("Error leyendo archivo: " + e.getMessage());
        }
    }

    // ---------- OTA callbacks ----------
    @Override public void onProgress(int percent) {
        runOnUiThread(() -> progress.setProgress(percent));
    }

    @Override public void onLog(String msg) {
        runOnUiThread(() -> tvLog.append(msg + "\n"));
    }

    @Override public void onFinished(boolean ok, String msg) {
        runOnUiThread(() -> {
            log(msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    // ---------- BLE callbacks ----------
    @Override public void onNotify(UUID svc, UUID ch, byte[] value) {
        // Aquí debes adaptar según cómo tu firmware mande ACKs
        // Ejemplo típico:
        if (ch.equals(AppConstants.OTA_PROGRESS)) {
            if (value != null && value.length > 0) {
                if (value[0] == 0x01) ota.onStartAck();
                else if (value[0] == 0x02) ota.onChunkAck();
                else if (value[0] == 0x03) ota.onEndAck(true);
                else ota.onEndAck(false);
            }
        }
    }

    @Override public void onConnectionState(boolean connected, @Nullable String mac) {}
    @Override public void onMtuChanged(int mtu) {}
    @Override public void onServicesDiscovered(java.util.List<?> s) {}
    @Override public void onRead(UUID s, UUID c, byte[] v) {}
    @Override public void onWrite(UUID s, UUID c, int status) {}
    @Override public void onError(String where, int status) {
        ota.abort("BLE error: " + where);
    }

    private void log(String s) {
        tvLog.append(s + "\n");
    }
}

