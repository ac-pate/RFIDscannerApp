package com.example.rfidscanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements BLE_MANAGER.RfidDataListener {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private BLE_MANAGER bleManager;
    private Button btnScan;
    private Button btnConnect;
    private TextView txtRfidData;
    private TextView txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        btnScan = findViewById(R.id.btn_scan);
        btnConnect = findViewById(R.id.btn_connect);
        txtRfidData = findViewById(R.id.txt_rfid_data);
        txtStatus = findViewById(R.id.txt_status);

        // Initialize BLE manager
        bleManager = new BLE_MANAGER(this);
        bleManager.setRfidDataListener(this);

        // Check Bluetooth permissions
        if (checkPermissions()) {
            initializeBluetooth();
        } else {
            requestRequiredPermissions();
        }

        // Set up button listeners
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanForDevices();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToDevice();
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                initializeBluetooth();
            } else {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeBluetooth() {
        if (!bleManager.hasBluetooth()) {
            bleManager.enableBluetooth();
            updateStatus("Enabling Bluetooth...");
        } else {
            updateStatus("Bluetooth is ready");
            btnScan.setEnabled(true);
        }
    }

    private void scanForDevices() {
        updateStatus("Scanning for devices...");
        btnScan.setEnabled(false);
        bleManager.startScan();

        // Wait for scan to complete
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                bleManager.stopScan();
                bleManager.showDevices();
                btnScan.setEnabled(true);
                btnConnect.setEnabled(true);
                updateStatus("Scan complete");
            }
        }, 10000); // 10 seconds scan time
    }

    private void connectToDevice() {
        updateStatus("Connecting to ESP32...");
        btnConnect.setEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bleManager.connectPeripheral();
        }
    }

    @Override
    public void onRfidDataReceived(String data) {
        Log.d(TAG, "RFID data received: " + data);
        txtRfidData.setText(data);
        updateStatus("RFID tag detected");
    }

    private void updateStatus(String message) {
        txtStatus.setText(message);
        Log.d(TAG, "Status: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.stopScan();
        }
    }
}