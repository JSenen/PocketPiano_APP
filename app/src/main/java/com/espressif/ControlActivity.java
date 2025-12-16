package com.espressif;


import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.ble.BleForegroundService;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.*;

public class ControlActivity extends AppCompatActivity implements BleForegroundService.BleListener {

    private TextView tvStatus;
    private ExpandableListView listView;
    private Button btnRefresh;

    private BleForegroundService service;
    private boolean bound = false;

    private final List<BluetoothGattService> services = new ArrayList<>();
    private final List<String> groupTitles = new ArrayList<>();
    private final Map<String, List<CharRow>> children = new LinkedHashMap<>();

    private ExpandableAdapter adapter;

    private String mac;
    private String name;

    // --- Bind to Service ---
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            BleForegroundService.LocalBinder b = (BleForegroundService.LocalBinder) binder;
            service = b.getService();
            bound = true;

            service.addListener(ControlActivity.this);

            // Si ya está conectado, pedimos MTU y servicios.
            tvStatus.setText("Servicio BLE enlazado. Preparando...");
            service.requestMtu(AppConstants.OTA_MTU);
            service.discoverServices();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
            tvStatus.setText("Service desconectado");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mac = getIntent().getStringExtra(AppConstants.EXTRA_MAC);
        name = getIntent().getStringExtra(AppConstants.EXTRA_NAME);

        // UI simple
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView tvTitle = new TextView(this);
        tvTitle.setTextSize(18);
        tvTitle.setText("CONTROL BLE\n" + (name != null ? name : "(sin nombre)") + "\n" + mac);

        tvStatus = new TextView(this);
        tvStatus.setText("Conectando...");

        btnRefresh = new Button(this);
        btnRefresh.setText("Refrescar servicios");

        listView = new ExpandableListView(this);
        adapter = new ExpandableAdapter();
        listView.setAdapter(adapter);

        root.addView(tvTitle);
        root.addView(tvStatus);
        root.addView(btnRefresh);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        setContentView(root);

        btnRefresh.setOnClickListener(v -> {
            if (service != null) {
                tvStatus.setText("Descubriendo servicios...");
                service.discoverServices();
            }
        });

        // Pulsación corta: acciones rápidas (READ / NOTIFY / WRITE)
        listView.setOnChildClickListener((parent, v, groupPos, childPos, id) -> {
            CharRow row = getCharRow(groupPos, childPos);
            if (row == null) return true;
            showActionsDialog(row);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind al service (ya debe estar arrancado desde DevicesActivity)
        Intent i = new Intent(this, com.espressif.ble.BleForegroundService.class);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
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

    // -------------------- BleListener callbacks --------------------

    @Override
    public void onConnectionState(boolean connected, @Nullable String mac) {
        runOnUiThread(() -> {
            tvStatus.setText(connected ? "Conectado" : "Desconectado");
            if (!connected) Toast.makeText(this, "Desconectado del dispositivo", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onMtuChanged(int mtu) {
        runOnUiThread(() -> tvStatus.setText("MTU negociada: " + mtu));
    }

    @Override
    public void onServicesDiscovered(List<BluetoothGattService> svcs) {
        runOnUiThread(() -> {
            services.clear();
            services.addAll(svcs);

            rebuildModel();
            adapter.notifyDataSetChanged();

            tvStatus.setText("Servicios: " + services.size());
        });
    }

    @Override
    public void onNotify(UUID serviceUuid, UUID charUuid, byte[] value) {
        runOnUiThread(() -> {
            String hex = toHex(value);
            Toast.makeText(this, "NOTIFY " + shortUuid(charUuid) + ": " + hex, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRead(UUID serviceUuid, UUID charUuid, byte[] value) {
        runOnUiThread(() -> {
            String hex = toHex(value);
            new AlertDialog.Builder(this)
                    .setTitle("READ " + shortUuid(charUuid))
                    .setMessage(hex + "\n\nASCII: " + safeAscii(value))
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    @Override
    public void onWrite(UUID serviceUuid, UUID charUuid, int status) {
        runOnUiThread(() -> {
            Toast.makeText(this, "WRITE " + shortUuid(charUuid) + " status=" + status, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onError(String where, int status) {
        runOnUiThread(() -> Toast.makeText(this, "BLE error: " + where + " (" + status + ")", Toast.LENGTH_LONG).show());
    }

    // -------------------- UI model --------------------

    private void rebuildModel() {
        groupTitles.clear();
        children.clear();

        for (BluetoothGattService s : services) {
            String g = "Service " + shortUuid(s.getUuid());
            groupTitles.add(g);

            List<CharRow> rows = new ArrayList<>();
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                rows.add(new CharRow(s.getUuid(), c.getUuid(), c.getProperties()));
            }
            children.put(g, rows);
        }
    }

    private CharRow getCharRow(int groupPos, int childPos) {
        if (groupPos < 0 || groupPos >= groupTitles.size()) return null;
        String g = groupTitles.get(groupPos);
        List<CharRow> rows = children.get(g);
        if (rows == null || childPos < 0 || childPos >= rows.size()) return null;
        return rows.get(childPos);
    }

    private void showActionsDialog(CharRow row) {
        List<String> opts = new ArrayList<>();
        boolean canRead = (row.props & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
        boolean canWrite = ((row.props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
                || ((row.props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0);
        boolean canNotify = ((row.props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                || ((row.props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0);

        if (canRead) opts.add("READ");
        if (canNotify) opts.add(row.notifyEnabled ? "NOTIFY OFF" : "NOTIFY ON");
        if (canWrite) opts.add("WRITE (ASCII)");
        if (canWrite) opts.add("WRITE (HEX)");

        String[] items = opts.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Char " + shortUuid(row.charUuid))
                .setItems(items, (d, which) -> {
                    String chosen = items[which];
                    if (service == null) return;

                    switch (chosen) {
                        case "READ":
                            service.read(row.serviceUuid, row.charUuid);
                            break;

                        case "NOTIFY ON":
                            row.notifyEnabled = true;
                            service.setNotify(row.serviceUuid, row.charUuid, true);
                            adapter.notifyDataSetChanged();
                            break;

                        case "NOTIFY OFF":
                            row.notifyEnabled = false;
                            service.setNotify(row.serviceUuid, row.charUuid, false);
                            adapter.notifyDataSetChanged();
                            break;

                        case "WRITE (ASCII)":
                            showWriteDialog(row, false);
                            break;

                        case "WRITE (HEX)":
                            showWriteDialog(row, true);
                            break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showWriteDialog(CharRow row, boolean hex) {
        if (service == null) return;

        EditText input = new EditText(this);
        input.setHint(hex ? "Ej: 01A0FF" : "Texto ASCII");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle(hex ? "WRITE HEX" : "WRITE ASCII")
                .setView(input)
                .setPositiveButton("Enviar", (d, w) -> {
                    String txt = input.getText().toString();
                    byte[] payload = hex ? parseHex(txt) : txt.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    int writeType =
                            ((row.props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

                    service.write(row.serviceUuid, row.charUuid, payload, writeType);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // -------------------- Adapter --------------------

    private class ExpandableAdapter extends BaseExpandableListAdapter {

        @Override public int getGroupCount() { return groupTitles.size(); }
        @Override public int getChildrenCount(int groupPosition) {
            String g = groupTitles.get(groupPosition);
            List<CharRow> rows = children.get(g);
            return rows != null ? rows.size() : 0;
        }

        @Override public Object getGroup(int groupPosition) { return groupTitles.get(groupPosition); }
        @Override public Object getChild(int groupPosition, int childPosition) { return getChild(groupPosition, childPosition); }

        @Override public long getGroupId(int groupPosition) { return groupPosition; }
        @Override public long getChildId(int groupPosition, int childPosition) { return (groupPosition * 1000L) + childPosition; }
        @Override public boolean hasStableIds() { return true; }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, android.view.ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(ControlActivity.this);
            tv.setPadding(32, 32, 32, 32);
            tv.setTextSize(16);
            tv.setText(groupTitles.get(groupPosition));
            return tv;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, android.view.ViewGroup parent) {
            LinearLayout rowLayout = (convertView instanceof LinearLayout) ? (LinearLayout) convertView : new LinearLayout(ControlActivity.this);
            rowLayout.setOrientation(LinearLayout.VERTICAL);
            rowLayout.setPadding(48, 24, 48, 24);

            TextView tv1;
            TextView tv2;

            if (rowLayout.getChildCount() == 0) {
                tv1 = new TextView(ControlActivity.this);
                tv2 = new TextView(ControlActivity.this);
                rowLayout.addView(tv1);
                rowLayout.addView(tv2);
            } else {
                tv1 = (TextView) rowLayout.getChildAt(0);
                tv2 = (TextView) rowLayout.getChildAt(1);
            }

            CharRow row = getCharRow(groupPosition, childPosition);
            String p = propsToString(row.props);

            tv1.setText("Char " + shortUuid(row.charUuid) + "  [" + p + "]");
            tv2.setText(row.notifyEnabled ? "NOTIFY: ON" : "NOTIFY: OFF");

            return rowLayout;
        }

        @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }
    }

    // -------------------- Model + utils --------------------

    private static class CharRow {
        final UUID serviceUuid;
        final UUID charUuid;
        final int props;
        boolean notifyEnabled = false;

        CharRow(UUID s, UUID c, int props) {
            this.serviceUuid = s;
            this.charUuid = c;
            this.props = props;
        }
    }

    private static String shortUuid(UUID u) {
        String s = u.toString().toUpperCase(Locale.ROOT);
        // si es UUID estándar tipo 0000xxxx-0000-1000-8000-00805f9b34fb, mostramos xxxx
        if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805F9B34FB")) {
            return s.substring(4, 8);
        }
        return s;
    }

    private static String propsToString(int props) {
        List<String> p = new ArrayList<>();
        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) p.add("R");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) p.add("W");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) p.add("WNR");
        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) p.add("N");
        if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) p.add("I");
        return String.join(",", p);
    }

    private static String toHex(byte[] data) {
        if (data == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static String safeAscii(byte[] data) {
        if (data == null) return "";
        String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        // sustituye caracteres raros
        return s.replaceAll("[^\\x20-\\x7E\\n\\r\\t]", ".");
    }

    private static byte[] parseHex(String hex) {
        if (hex == null) return new byte[0];
        String h = hex.replaceAll("[^0-9A-Fa-f]", "");
        if ((h.length() % 2) != 0) h = "0" + h;

        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}

