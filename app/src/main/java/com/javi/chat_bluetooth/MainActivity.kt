package com.javi.chat_bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(bluetoothAdapter.isDiscovering())
        {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()

        //Registro para

    }

    override fun onStart() {

        //Checking if BLUETOOTH is enabled when we launch the activity
        super.onStart()
        if(bluetoothAdapter == null)
        {
            Toast.makeText(this,"El Bluetooth no está disponible",Toast.LENGTH_SHORT).show()
            finish()
        }
        if(!bluetoothAdapter.isEnabled())
        {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, 1)
        }
    }
}
