package com.espressif;

import static com.espressif.BluetoothUtil.hasPermissions;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.wifi_provisioning.R;

public class SplashScreen extends AppCompatActivity {

    Button buttonConfig;
    private TextView txtVersion;

    private static final int REQUEST_PERMISSIONS = 1;
    private String[] requiredPermissions = {
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        // Verifica si los permisos ya están concedidos
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions, REQUEST_PERMISSIONS);
        }

        buttonConfig = findViewById(R.id.buttLaunchConfig);

        txtVersion = findViewById(R.id.txt_version);

        String version = "1.1.0";
        txtVersion.setText(version);

        buttonConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SplashScreen.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
    private boolean hasPermissions() {
        for (String permission : requiredPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permisos concedidos
            } else {
                // Permisos denegados
            }
        }
    }
}
