package com.javi.chat_bluetooth

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.*


class MainActivity : AppCompatActivity() {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    lateinit var status: TextView
    lateinit var btnConnect: Button
    lateinit var listView: ListView
    lateinit var dialog: Dialog
    lateinit var inputLayout: EditText
    lateinit var chatAdapter: ArrayAdapter<String>
    lateinit var chatMessages: ArrayList<String>


    val MESSAGE_STATE_CHANGE = 1
    val MESSAGE_READ = 2
    val MESSAGE_WRITE = 3
    val MESSAGE_DEVICE_OBJECT = 4
    val MESSAGE_TOAST = 5
    val DEVICE_OBJECT = "device_name"


    override fun onStart() {

        //Checking if BLUETOOTH is enabled when we launch the activity
        super.onStart()
        if(bluetoothAdapter == null)
        {
            Toast.makeText(this,"El Bluetooth no está disponible",Toast.LENGTH_SHORT).show()
            finish()
        }
        if(!bluetoothAdapter.isEnabled)
        {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(bluetoothAdapter.isDiscovering)
        {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()


    }


}
