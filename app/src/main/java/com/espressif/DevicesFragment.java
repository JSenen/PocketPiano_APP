package com.espressif;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import com.espressif.wifi_provisioning.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * show list of BLE devices
 */
public class DevicesFragment extends ListFragment {

    private enum ScanState { NONE, LE_SCAN, DISCOVERY, DISCOVERY_FINISHED }
    private ScanState scanState = ScanState.NONE;
    private static final long LE_SCAN_PERIOD = 10000; // similar to bluetoothAdapter.startDiscovery
    private final Handler leScanStopHandler = new Handler();
    private final BluetoothAdapter.LeScanCallback leScanCallback;
    private final Runnable leScanStopCallback;
    private final BroadcastReceiver discoveryBroadcastReceiver;
    private final IntentFilter discoveryIntentFilter;
    private String optionClicked;

    private Menu menu;
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<BluetoothUtil.Device> listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothUtil.Device> listAdapter;
    ActivityResultLauncher<String[]> requestBluetoothPermissionLauncherForStartScan;
    ActivityResultLauncher<String> requestLocationPermissionLauncherForStartScan;

    public DevicesFragment() {
        leScanCallback = (device, rssi, scanRecord) -> {
            if(device != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> { updateScan(device); });
            }
        };
        discoveryBroadcastReceiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC && getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateScan(device));
                    }
                }
                if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    scanState = ScanState.DISCOVERY_FINISHED; // don't cancel again
                    stopScan();
                }
            }
        };
        discoveryIntentFilter = new IntentFilter();
        discoveryIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        discoveryIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        leScanStopCallback = this::stopScan; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks
        requestBluetoothPermissionLauncherForStartScan = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                granted -> BluetoothUtil.onPermissionsResult(this, granted, this::startScan));
        requestLocationPermissionLauncherForStartScan = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        new Handler(Looper.getMainLooper()).postDelayed(this::startScan, 1); // run after onResume to avoid wrong empty-text
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(getText(R.string.location_permission_title));
                        builder.setMessage(getText(R.string.location_permission_denied));
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();
                    }
                });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        //Recovery SharedPreferences option clicked in SplashScreen
        SharedPreferences sharedPref = getActivity().getSharedPreferences("OptionClicked",Context.MODE_PRIVATE);
        optionClicked = sharedPref.getString("OptionClicked", null); //Recovery value

        listAdapter = new ArrayAdapter<BluetoothUtil.Device>(getActivity(), 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                BluetoothUtil.Device device = listItems.get(position);
                String deviceName = device.getName();

                // Omitir dispositivos con nombre null o vacío
                if (deviceName == null || deviceName.isEmpty()) {
                    return new View(getContext()); // Retornar una vista vacía
                }

                if (view == null) {
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                }

                ImageView imageView = view.findViewById(R.id.device_icon);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);


                text1.setText(deviceName);
                text2.setText(device.getDevice().getAddress());

                // Mostrar el icono si el nombre del dispositivo contiene "PocketPiano"
                if (deviceName.contains("PocketPiano")) {
                    imageView.setVisibility(View.VISIBLE);

                } else {
                    imageView.setVisibility(View.GONE);
                }

                return view;
            }

            @Override
            public int getCount() {
                // Contar solo los dispositivos con nombre no null y no vacío
                int count = 0;
                for (BluetoothUtil.Device device : listItems) {
                    String deviceName = device.getName();
                    Log.d("BluetoothDevice", "Nombre del dispositivo: " + deviceName);
                    //if (deviceName != null && !deviceName.isEmpty()) {
                    if (deviceName != null /*&& deviceName.contains("PocketPiano")*/) {
                        count++;
                    }
                }
                return count;
            }

            @Nullable
            @Override
            public BluetoothUtil.Device getItem(int position) {
                // Retornar solo dispositivos con nombre no null y no vacío
                int count = 0;
                for (BluetoothUtil.Device device : listItems) {
                    String deviceName = device.getName();
                   // if (deviceName != null && !deviceName.isEmpty()) {
                    if (deviceName != null /*&& deviceName.contains("PocketPiano")*/) {
                        if (count == position) {
                            return device;
                        }
                        count++;
                    }
                }
                return null;
            }
        };

    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(48);
        ((TextView) getListView().getEmptyView()).setTextColor(Color.parseColor("#414141"));

        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
        this.menu = menu;
        if (bluetoothAdapter == null) {
            menu.findItem(R.id.bt_settings).setEnabled(false);
            menu.findItem(R.id.ble_scan).setEnabled(false);
        } else if(!bluetoothAdapter.isEnabled()) {
            menu.findItem(R.id.ble_scan).setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(discoveryBroadcastReceiver, discoveryIntentFilter);
        if(bluetoothAdapter == null) {
            setEmptyText(getString(R.string.bluetooth_le_not_supported));
        } else if(!bluetoothAdapter.isEnabled()) {
            setEmptyText(getString(R.string.bluetooth_is_disabled));
            if (menu != null) {
                listItems.clear();
                listAdapter.notifyDataSetChanged();
                menu.findItem(R.id.ble_scan).setEnabled(false);
            }
        } else {
            setEmptyText(getString(R.string.use_scan_to_refresh_devices));
            if (menu != null)
                menu.findItem(R.id.ble_scan).setEnabled(true);
        }
    }



    @Override
    public void onPause() {
        super.onPause();
        stopScan();
        getActivity().unregisterReceiver(discoveryBroadcastReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        menu = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.ble_scan) {
            startScan();
            return true;
        } else if (id == R.id.ble_scan_stop) {
            stopScan();
            return true;
        } else if (id == R.id.bt_settings) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
            return true;

        } else if (id == R.id.bt_back) {
            // Aquí se maneja la navegación a pagina inicial
            Intent intent = new Intent(getActivity(), SplashScreen.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private BluetoothLeScanner bluetoothLeScanner;

    private void startScan() {
        if (scanState != ScanState.NONE)
            return;
        ScanState nextScanState = ScanState.LE_SCAN;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!BluetoothUtil.hasPermissions(this, requestBluetoothPermissionLauncherForStartScan))
                return;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                scanState = ScanState.NONE;
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.location_permission_title);
                builder.setMessage(R.string.location_permission_grant);
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> requestLocationPermissionLauncherForStartScan.launch(Manifest.permission.ACCESS_FINE_LOCATION));
                builder.show();
                return;
            }
            // Verifica si el GPS está habilitado
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            boolean locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!locationEnabled) {
                scanState = ScanState.DISCOVERY;
            }
        }

        scanState = nextScanState;
        listItems.clear();
        listAdapter.notifyDataSetChanged();
        setEmptyText("<scanning...>");
        menu.findItem(R.id.ble_scan).setVisible(false);
        menu.findItem(R.id.ble_scan_stop).setVisible(true);

        if (scanState == ScanState.LE_SCAN) {
            leScanStopHandler.postDelayed(leScanStopCallback, LE_SCAN_PERIOD);

            // Usar BluetoothLeScanner para iniciar el escaneo
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.startScan(scanCallback);  // Empieza el escaneo usando el callback adecuado
        } else {
            bluetoothAdapter.startDiscovery();
        }
    }

    @SuppressLint("MissingPermission")
    private void updateScan(BluetoothDevice device) {
        if(scanState == ScanState.NONE)
            return;
        BluetoothUtil.Device device2 = new BluetoothUtil.Device(device); // slow getName() only once
        int pos = Collections.binarySearch(listItems, device2);
        if (pos < 0) {
            listItems.add(-pos - 1, device2);
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanState == ScanState.NONE)
            return;
        setEmptyText("<no bluetooth devices found>");
        if (menu != null) {
            menu.findItem(R.id.ble_scan).setVisible(true);
            menu.findItem(R.id.ble_scan_stop).setVisible(false);
        }

        switch (scanState) {
            case LE_SCAN:
                leScanStopHandler.removeCallbacks(leScanStopCallback);
                if (bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(scanCallback);  // Detiene el escaneo BLE
                }
                break;
            case DISCOVERY:
                bluetoothAdapter.cancelDiscovery();
                break;
            default:
                // Ya se ha detenido
        }
        scanState = ScanState.NONE;
    }
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && getActivity() != null) {
                getActivity().runOnUiThread(() -> updateScan(device));  // Actualiza la lista con los dispositivos encontrados
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                if (device != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateScan(device));
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE", "Scan failed with error: " + errorCode);
            getActivity().runOnUiThread(() -> setEmptyText("<scan failed>"));
        }
    };

    //User SELECT A DEVICE FROM LIST
    @SuppressLint("MissingPermission")
    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        stopScan();
        BluetoothUtil.Device device = listItems.get(position-1);
        Bundle args = new Bundle();
        args.putString("device", device.getDevice().getAddress());
//        Fragment fragment = new TerminalFragment();
//        fragment.setArguments(args);
//        getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();

        if (optionClicked !=null && optionClicked.equals("ota")){
            Fragment fragment = new UploadFileFragment();
            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "temperature").addToBackStack(null).commit();
        } else if (optionClicked != null && optionClicked.equals("control")) {
            // Navegar a la Activity de Control y pasar la dirección del dispositivo BLE
            Intent intent = new Intent(getActivity(), DeviceControlActivity.class);
            intent.putExtra("device", device.getDevice().getAddress());
            //intent.putExtra("deviceName",device.getDevice().getName());
            startActivity(intent);
        }

    }
}
