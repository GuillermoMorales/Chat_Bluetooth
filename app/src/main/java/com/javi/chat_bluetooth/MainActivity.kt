package com.javi.chat_bluetooth

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.*
import android.widget.ArrayAdapter
import android.bluetooth.BluetoothDevice
import android.view.View
import android.widget.Toast
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import android.widget.AdapterView
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.media.session.PlaybackState.STATE_NONE
import android.os.Handler
import android.app.Activity
import com.javi.chat_bluetooth.UtilClass.Companion.DEVICE_OBJECT
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_DEVICE_OBJECT
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_READ
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_STATE_CHANGE
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_TOAST
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_WRITE
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_CONNECTED
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_CONNECTING
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_LISTEN


class MainActivity : AppCompatActivity() {
    var bluetoothAdapter: BluetoothAdapter? = null
    lateinit var status: TextView
    lateinit var btnConnect: Button
    lateinit var listView: ListView
    lateinit var dialog: Dialog
    lateinit var inputLayout: TextInputLayout
    lateinit var chatAdapter: ArrayAdapter<String>
    lateinit var chatMessages: ArrayList<String>


    private val REQUEST_ENABLE_BLUETOOTH = 1
    lateinit var connectingDevice: BluetoothDevice
    lateinit var discoveredDevicesAdapter: ArrayAdapter<String>
    var chatController: OnlineChat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewsByIds()

        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "El Bluetooth no está disponible", Toast.LENGTH_SHORT).show();
            finish()
        }
        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener { showPrinterPickDialog() }

        //set chat adapter
        chatMessages = ArrayList()
        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages)
        listView.adapter = chatAdapter
    }

    private val handler = Handler(Handler.Callback { msg ->
        when (msg.what) {
            MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                STATE_CONNECTED -> {
                    setStatus("Connected to: " + connectingDevice.name)
                    btnConnect.isEnabled = false
                }
                STATE_CONNECTING -> {
                    setStatus("Connecting...")
                    btnConnect.isEnabled = false
                }
                STATE_LISTEN, STATE_NONE -> setStatus("Not connected")
            }
            MESSAGE_WRITE -> {
                val writeBuf = msg.obj as ByteArray

                val writeMessage = String(writeBuf)
                chatMessages.add("Me: $writeMessage")
                chatAdapter.notifyDataSetChanged()
            }
            MESSAGE_READ -> {
                val readBuf = msg.obj as ByteArray

                val readMessage = String(readBuf, 0, msg.arg1)
                chatMessages.add(connectingDevice.name + ":  " + readMessage)
                chatAdapter.notifyDataSetChanged()
            }
            MESSAGE_DEVICE_OBJECT -> {
                connectingDevice = msg.data.getParcelable(DEVICE_OBJECT)!!
                Toast.makeText(applicationContext, "Connected to " + connectingDevice.name,
                        Toast.LENGTH_SHORT).show()
            }
            MESSAGE_TOAST -> Toast.makeText(applicationContext, msg.getData().getString("toast"),
                    Toast.LENGTH_SHORT).show()
        }
        false
    })

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> if (resultCode == Activity.RESULT_OK) {
                chatController = OnlineChat(this, handler)
            } else {
                Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    public override fun onResume() {
        super.onResume()

        if (chatController?.getState() == STATE_NONE) {
            chatController?.start()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        chatController!!.stop()
    }

    override fun onStart() {

        //Checking if BLUETOOTH is enabled when we launch the activity
        super.onStart()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "El Bluetooth no está disponible", Toast.LENGTH_SHORT).show()
            finish()
        }


        if (!bluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            chatController = OnlineChat(this, handler)


        }
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun findViewsByIds() {
        status = findViewById(R.id.status)
        btnConnect = findViewById(R.id.btn_connect)
        listView = findViewById(R.id.list)
        inputLayout = findViewById(R.id.input_layout)
        val btnSend = findViewById<Button>(R.id.btn_send)

        btnSend.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                if (inputLayout.editText?.text.toString() == "") {
                    Toast.makeText(this@MainActivity, "Please input some texts", Toast.LENGTH_SHORT).show()
                } else {
                    //TODO: here
                    sendMessage(inputLayout.editText?.text.toString())
                    inputLayout.editText?.setText("")
                }
            }
        })
    }

    private fun showPrinterPickDialog() {
        dialog = Dialog(this)
        dialog.setContentView(R.layout.layout_bluetooth)
        dialog.setTitle("Bluetooth Devices")

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()

        //Initializing bluetooth adapters
        val pairedDevicesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        discoveredDevicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)

        //locate listviews and attatch the adapters
        val listView = dialog.findViewById(R.id.pairedDeviceList) as ListView
        val listView2 = dialog.findViewById(R.id.discoveredDeviceList) as ListView
        listView.adapter = pairedDevicesAdapter
        listView2.adapter = discoveredDevicesAdapter

        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryFinishReceiver, filter)

        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryFinishReceiver, filter)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter!!.bondedDevices

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                pairedDevicesAdapter.add(device.name + "\n" + device.address)
            }
        } else {
            pairedDevicesAdapter.add("No devices have been paired")
        }

        //Handling listview item click event
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            bluetoothAdapter!!.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)

            connectToDevice(address)
            dialog.dismiss()
        }

        listView2.onItemClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
            bluetoothAdapter!!.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)

            connectToDevice(address)
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.cancelButton).setOnClickListener(View.OnClickListener { dialog.dismiss() })
        dialog.setCancelable(false)
        dialog.show()
    }

    private val discoveryFinishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.name + "\n" + device.address)
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                if (discoveredDevicesAdapter.count == 0) {
                    discoveredDevicesAdapter.add("No devices found")
                }
            }
        }
    }

    private fun connectToDevice(deviceAddress: String) {
        bluetoothAdapter!!.cancelDiscovery()
        val device = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
        chatController!!.connect(device)
    }

    private fun sendMessage(message: String) {
        if (chatController!!.getState() != STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show()
            return
        }

        if (message.isNotEmpty()) {
            val send = message.toByteArray()
            chatController!!.write(send)
        }
    }



}
