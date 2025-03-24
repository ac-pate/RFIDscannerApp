package com.example.rfidscanner;

import android.bluetooth.BluetoothDevice;
public class BLE_DEVICE {
    private BluetoothDevice device;
    private int rssi;
    private String name;

    public BLE_DEVICE(BluetoothDevice _device, String name, int rssi) {
        this.device = _device;
        this.rssi = rssi;
        this.name = name;
    }

    public String getAddress(){
        return device.getAddress();
    }
    public String getName(){
        return name;
    }
    public void setRSSI(int _rssi){
        this.rssi = rssi;
    }
    public int getRSSI(){
        return rssi;
    }
    public BluetoothDevice getDevice(){
        return device;
    }
}