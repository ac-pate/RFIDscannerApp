package com.example.rfidscanner;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

//Broadcast Receiver
public class BLE_STATE extends BroadcastReceiver {
    Context activityContext;
    private static final String TAG = "BLE_STATE";

    public BLE_STATE(Context _activityContext){
        this.activityContext = _activityContext;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state){
                case BluetoothAdapter.STATE_OFF:
                    Log.d(TAG, "onReceive: State OFF");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.d(TAG, "on Receive : State Turning OFF");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.d(TAG, "onReceive : State ON");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.d(TAG, "onReceive : State Turning on");
                    break;
            }
        }
    }
}