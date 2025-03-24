package com.example.rfidscanner;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BLE_MANAGER {
    private static final String TAG = "BLE_MANAGER";
    Context context;
    Activity activity;
    private final BluetoothAdapter btAdapter;
    private final BluetoothLeScanner btScanner;
    private final BLE_STATE btState;
    BluetoothGatt gatt;
    private static final long SCAN_PERIOD = 10000;
    ArrayList<BLE_DEVICE> devices;
    BluetoothDevice peripheral;
    Handler bleHandler;
    private boolean peripheralAvailable = false;

    private Queue<Runnable> commandQueue = new LinkedList<>();
    private boolean commandQueueBusy = false;
    private RfidDataListener rfidDataListener;

    // Interface for RFID data callbacks
    public interface RfidDataListener {
        void onRfidDataReceived(String data);
    }

    public void setRfidDataListener(RfidDataListener listener) {
        this.rfidDataListener = listener;
    }

    public BLE_MANAGER(Activity _activity) {
        context = _activity.getApplicationContext();
        activity = _activity;
        BluetoothManager bluetoothManager = (BluetoothManager) _activity.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = bluetoothManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        btState = new BLE_STATE(context);
        bleHandler = new Handler(Looper.getMainLooper());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void connectPeripheral() {
        if (!peripheralAvailable) {
            Log.d(TAG, "Peripheral not available");
            return;
        }
        // start the gatt connection
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        Log.d(TAG, "Peripheral available, attempting to connect devices");
        gatt = peripheral.connectGatt(context, false, gattCallback, TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                    return;
                }
                int bondState = peripheral.getBondState();
                //
                // If BOND_BONDING: bonding is in progress, don't call discoverServices()
                if (bondState == BluetoothDevice.BOND_NONE || bondState == BOND_BONDED) {
                    int delayWhenBonded = 0;
                    //for some version need to
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                        delayWhenBonded = 1000;
                    }
                    final int delay = bondState == BOND_BONDED ? delayWhenBonded : 0;

                    Runnable discoveryServicesRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions();
                                return;
                            }
                            boolean success = gatt.discoverServices();
                            if (!success) {
                                Log.d(TAG, "DiscoveryServiceRunnable: discoverServices failed to start");
                            }
                        }
                    };
                    bleHandler.postDelayed(discoveryServicesRunnable, delay);
                } else if (bondState == BOND_BONDING) {
                    Log.d(TAG, "Waiting for bonding to complete");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // ESP32 Service and Characteristic UUIDs
                UUID serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
                UUID characteristicUUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

                // get the ble gatt service
                BluetoothGattService service = gatt.getService(serviceUUID);
                if (service != null) {
                    //get the characteristic
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);

                    if (characteristic != null) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions();
                            return;
                        }
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    } else {
                        Log.d(TAG, "Characteristic not found");
                    }
                } else {
                    Log.d(TAG, "Service not found");
                }
            } else {
                Log.d(TAG, "Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The data is contained in the characteristic's value
                byte[] data = characteristic.getValue();
                int value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
            } else {
                Log.d(TAG, "Failed to read characteristic");
            }
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);

            StringBuilder stringValue = new StringBuilder();
            for (byte b : value) {
                stringValue.append(String.format("%02X ", b));
            }
            String rfidData = stringValue.toString().trim();
            Log.d(TAG, "NFC TAG: " + rfidData);

            boolean success = readCharacteristic(characteristic);
            if(success){
                nextCommand();
            } else {
                Log.d(TAG, "Failed to receive data");
            }

            // Notify listener of the new RFID data
            if (rfidDataListener != null) {
                // Using handler to make sure it runs on UI thread
                bleHandler.post(() -> rfidDataListener.onRfidDataReceived(rfidData));
            }
        }
    };

    public void startScan() {
        Log.d(TAG, "Scanning started");
        devices = new ArrayList<>();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                    return;
                }
                btScanner.startScan(leScanCallback);

                // Stop scanning after SCAN_PERIOD
                bleHandler.postDelayed(() -> stopScan(), SCAN_PERIOD);
            }
        });
    }

    public boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (gatt == null) {
            Log.d(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return false;
        }
        if (characteristic == null) {
            Log.d(TAG, "ERROR: Characteristic is 'null', ignoring read request");
            return false;
        }
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.d(TAG, "ERROR: Characteristic cannot be read");
            return false;
        }
        return commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                    return;
                }
                if (!gatt.readCharacteristic(characteristic)) {
                    completedCommand();
                } else {
                    Log.d(TAG, String.format("Reading characteristic <%s>", characteristic.getUuid()));
                    nextCommand();
                }
            }
        });
    }

    private void nextCommand() {
        if(commandQueueBusy) {
            return;
        }
        if (gatt == null) {
            Log.d(TAG, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", peripheral.getAddress()));
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }
        if (commandQueue.size() > 0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;
            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        Log.d(TAG, String.format("ERROR: Command exception for device '%s'", peripheral.getAddress()));
                    }
                }
            });
        }
    }

    private void completedCommand() {
        commandQueueBusy = false;
        commandQueue.poll();
        nextCommand();
    }

    public void stopScan() {
        Log.d(TAG, "Scan Stopping");
        AsyncTask.execute(() -> {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return;
            }
            btScanner.stopScan(leScanCallback);
        });
    }

    public void showDevices() {
        if (devices.size() != 0) {
            for (BLE_DEVICE device : devices) {
                String name = device.getName();
                String deviceInfo = "Device Name: " + name + " Address: " + device.getAddress() + " rssi: " + device.getRSSI() + "\n";
                Log.d(TAG, deviceInfo);

                // Make sure null string pass because it will have a null pointer exception
                if(name == null) continue;
                if(name.equals("ESP32")) {
                    peripheralAvailable = true;
                    peripheral = device.getDevice();
                }
            }
        } else {
            Log.d(TAG, "No devices found");
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return;
            }
            BLE_DEVICE device = new BLE_DEVICE(result.getDevice(), result.getDevice().getName(), result.getRssi());
            devices.add(device);
        }

        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed: scan failed");
            stopScan();
        }
    };

    public void enableBluetooth() {
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission not given");
            requestPermissions();
            return;
        }
        activity.startActivity(enableIntent);

        // broadcast the fact that bluetooth changed
        IntentFilter newIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(btState, newIntent);
    }

    public void disableBluetooth() {
        // guide user to disable bluetooth
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_BLUETOOTH_SETTINGS);
        activity.startActivity(intent);

        // broadcast the fact that bluetooth changed
        IntentFilter newIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(btState, newIntent);
    }

    public boolean hasBluetooth() {
        return btAdapter != null && btAdapter.isEnabled();
    }

    public void requestPermissions() {
        int BLUETOOTH_PERMISSION_CODE = 1;
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        ActivityCompat.requestPermissions(activity, permissions, BLUETOOTH_PERMISSION_CODE);
    }
}