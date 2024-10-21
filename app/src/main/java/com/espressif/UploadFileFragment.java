package com.espressif;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.espressif.message.*;
import com.espressif.wifi_provisioning.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class UploadFileFragment extends Fragment {

    private static final String TAG = "UploadFileFragment";
    private static final UUID SERVICE_UUID = UUID.fromString("6e404099-b5a3-f393-e1a9-e54e24dcaa9f");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("6e405094-b5a3-f393-e1a9-e51e24dcca8e");
    private static final int REQUEST_CODE_SELECT_FILE = 1;

    private String deviceAddress;
    private BluetoothDevice device;
    private Uri fileUri;

    private BleOTAClient otaClient;
    private File binFile;

    private TextView responseText;
    private TextView connectionStatus;
    private ImageView connectionStatusIcon, otaOKProgress;
    private Button buttonStartOTA;
    private ProgressBar progressBar;
    private TextView progressText;

    public UploadFileFragment() {
        // Required empty public constructor
    }
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private void requestBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[] {
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // Permisos ya concedidos, iniciar operaciones de Bluetooth
            startOtaProcess();;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permisos concedidos
                startOtaProcess();
            } else {
                Toast.makeText(getContext(), "Bluetooth permissions are required for this app", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        if (getArguments() != null) {
            deviceAddress = getArguments().getString("device");
            Log.d(TAG, "Device address: " + deviceAddress);

            // Solicitar permisos antes de iniciar cualquier operación Bluetooth
            requestBluetoothPermissions();
        } else {
            Log.e(TAG, "No device address provided.");
        }
    }

    /*******************************
     *  Inicializar el Cliente OTA y Conectar al Dispositivo
     *******************************/
    private void initializeOtaClient() {
        if (deviceAddress != null && binFile != null && binFile.length() > 0) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && deviceAddress != null) {
                device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                if (device != null) {
                    otaClient = new BleOTAClient(getContext(), device, binFile, this);
                    // Inicia la conexión al dispositivo aquí
                    startOtaProcess(); // Llama a startOtaProcess aquí
                } else {
                    Log.e(TAG, "Device not found.");
                }
            } else {
                Log.e(TAG, "BluetoothAdapter is null or deviceAddress is null.");
            }
        }
    }




    /*******************************
     *  CREACION VISTA
     *******************************/
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_uploadfile, container, false);
        responseText = view.findViewById(R.id.textCode);
        connectionStatus = view.findViewById(R.id.connectionStatus);
        connectionStatusIcon = view.findViewById(R.id.connectionStatusIcon);
        buttonStartOTA = view.findViewById(R.id.butStartOTA);
        progressBar = view.findViewById(R.id.progressBar);
        progressText = view.findViewById(R.id.progressText);
        otaOKProgress = view.findViewById(R.id.imgCheckOK);

        buttonStartOTA.setOnClickListener(v -> selectOTAFile());

        // Aquí inicializamos el cliente OTA y conectamos al dispositivo cuando se inicia el fragmento
        if (deviceAddress != null) {
            initializeOtaClient();
        }

        return view;
    }
    /*******************************
     *  SELECCION DE FICHERO
     *******************************/
    private void selectOTAFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select OTA File"), REQUEST_CODE_SELECT_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                fileUri = data.getData();
                if (fileUri != null) {
                    String fileName = getFileName(fileUri);
                    responseText.setText(fileName);
                    buttonStartOTA.setVisibility(View.GONE);
                    connectionStatus.setText("Updating, please wait...");
                    connectionStatusIcon.setVisibility(View.GONE);
                    progressBar.setVisibility(View.VISIBLE);
                    progressText.setVisibility(View.VISIBLE);

                    try {
                        InputStream inputStream = getActivity().getContentResolver().openInputStream(fileUri);
                        binFile = new File(getContext().getCacheDir(), fileName);
                        if (binFile.exists() || binFile.createNewFile()) {
                            binFile.deleteOnExit();
                            byte[] buffer = new byte[1024];
                            try (FileOutputStream outputStream = new FileOutputStream(binFile)) {
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }

                            // Verifica que binFile se ha creado correctamente
                            if (binFile.length() > 0) {
                                // Ahora inicializar BleOTAClient
                                initializeOtaClient(); // Mueve aquí la inicialización del cliente OTA
                            } else {
                                Log.e(TAG, "binFile is empty");
                                Toast.makeText(getContext(), "Failed to create valid binFile", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Failed to create binFile");
                            Toast.makeText(getContext(), "Failed to create binFile", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Error reading the file", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void startOtaProcess() {
        if (otaClient != null && binFile != null && binFile.length() > 0) {
            // Inicializar barra de progreso antes de iniciar OTA
            Log.d(TAG, "Inicializando barra de progreso a 0");
            updateProgress(0);

            otaClient.start(new BleOTAClient.GattCallback() {
                @Override
                public void onProgress(int progress) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            Log.d(TAG, "Actualizando barra de progreso: " + progress + "%");
                            updateProgress(progress);
                        });
                    } else {
                        Log.w(TAG, "Activity es null, no se puede actualizar la barra de progreso");
                    }
                }

                @Override
                public void onError(int code) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Error durante OTA: " + code, Toast.LENGTH_LONG).show();
                            disconnect();
                        });
                    }
                }

                @Override
                public void onOTA(BleOTAMessage message) {
                    if (message instanceof StartCommandAckMessage) {
                        if (((StartCommandAckMessage) message).getStatus() == BleOTAClient.COMMAND_ACK_ACCEPT) {
                            Log.i(TAG, "OTA started successfully.");
                        } else {
                            Log.e(TAG, "OTA start rejected by the device.");
                        }
                    } else if (message instanceof EndCommandAckMessage) {
                        if (((EndCommandAckMessage) message).getStatus() == BleOTAClient.COMMAND_ACK_ACCEPT) {
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.runOnUiThread(() -> handleOTAFinished());
                            }
                        } else {
                            Log.e(TAG, "OTA end rejected by the device.");
                        }
                    }
                }
            });
        } else {
            Log.e(TAG, "BleOTAClient no inicializado o binFile es null.");
        }
    }



    // Función auxiliar para obtener el nombre del archivo desde el Uri
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void updateProgress(int progress) {
        Log.d(TAG, "Updating progress: " + progress + "%");
        progressBar.setProgress(progress);
        progressText.setText(progress + "%");
    }

    private void handleOTAFinished() {
        Log.i(TAG, "OTA update finished.");

        // Asegura que la barra de progreso esté al 100%
        updateProgress(100);
        progressText.setVisibility(View.GONE);
        otaOKProgress.setVisibility(View.VISIBLE);
        disconnect();
    }

    @SuppressLint("MissingPermission")
    private void disconnect() {
        if (otaClient != null) {
            otaClient.close();
            otaClient = null;
        }
        updateConnectionStatus(false);
    }

    public void updateConnectionStatus(boolean isConnected) {
        getActivity().runOnUiThread(() -> {
            if (isConnected) {
                connectionStatus.setText(R.string.connected);
                connectionStatusIcon.setImageResource(R.drawable.ic_connected);
            } else {
                connectionStatus.setText(R.string.disconnected);
                connectionStatusIcon.setImageResource(R.drawable.ic_disconnected);
            }
        });
    }
}
