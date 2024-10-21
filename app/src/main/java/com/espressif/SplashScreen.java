package com.espressif;

import static com.espressif.BluetoothUtil.hasPermissions;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.ui.activities.EspMainActivity;
import com.espressif.wifi_provisioning.R;

public class SplashScreen extends AppCompatActivity {

    Button buttonOta, buttonControl;
    private SharedPreferences sharedPreferences;
    private ESPProvisionManager provisionManager;
    private ESPDevice espDevice;  // Añadir espDevice
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

        buttonOta = findViewById(R.id.buttLaunchOTA);
        buttonControl = findViewById(R.id.buttLaunchControl);

        txtVersion = findViewById(R.id.txt_version);

        String version = "1.0.0";
        txtVersion.setText(version);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());  // Inicializa ESPProvisionManager

        //Shared preferences is use to give the option clicked between activities
        SharedPreferences sharedPreferences = getSharedPreferences("OptionClicked", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        //Obtener la imagen
        ImageView imageView = findViewById(R.id.imageView);

        buttonOta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SplashScreen.this, MainActivity.class);
                editor.putString("OptionClicked","ota");
                editor.apply();
                startActivity(intent);
                finish();
            }
        });

        buttonControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("SplashScreen","Intent from SplashScreen to DeviceScan");
                Intent intent = new Intent(SplashScreen.this, MainActivity.class);
                editor.putString("OptionClicked","control");
                editor.apply();
                startActivity(intent);
                finish();
            }
        });


        //Cargar animacion
        //Animation bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce);

        //Aplicar la animacion
        //imageView.startAnimation(bounceAnimation);

//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                Intent intent = new Intent(SplashScreen.this, MainActivity.class);
//                startActivity(intent);
//                finish();
//            }
//        }, 3000); // 3000 ms delay


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