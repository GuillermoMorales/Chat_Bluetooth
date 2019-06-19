package com.javi.chat_bluetooth

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import com.javi.chat_bluetooth.UtilClass.Companion.DEVICE_OBJECT
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_DEVICE_OBJECT
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_READ
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_STATE_CHANGE
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_TOAST
import com.javi.chat_bluetooth.UtilClass.Companion.MESSAGE_WRITE
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_CONNECTED
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_CONNECTING
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_LISTEN
import com.javi.chat_bluetooth.UtilClass.Companion.STATE_NONE

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


class OnlineChat(context: Context, handler: Handler) {


    private val APP_NAME = "BluetoothChatApp"
    private val MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var handler: Handler? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ReadWriteThread? = null
    private var currentState: Int = 0


    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        currentState = STATE_NONE
        this.handler = handler
    }

    @Synchronized
    private fun setState(state: Int) {
        this.currentState = state

        handler!!.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
    }

    @Synchronized
    fun getState(): Int {
        return currentState
    }

    @Synchronized
    fun start() {
        // Cancel any thread
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel any running thresd
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        setState(STATE_LISTEN)
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel any thread
        if (currentState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread!!.cancel()
                connectThread = null
            }
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to connect with the given device
        connectThread = ConnectThread(device)
        connectThread!!.start()
        setState(STATE_CONNECTING)
    }

    // manage Bluetooth connection
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ReadWriteThread(socket)
        connectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = handler!!.obtainMessage(MESSAGE_DEVICE_OBJECT)
        val bundle = Bundle()
        bundle.putParcelable(DEVICE_OBJECT, device)
        msg.data = bundle
        handler!!.sendMessage(msg)

        setState(STATE_CONNECTED)
    }

    @Synchronized
    fun stop() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }
        setState(STATE_NONE)
    }

    fun write(out: ByteArray) {
        val r: ReadWriteThread
        synchronized(this) {
            if (currentState != STATE_CONNECTED)
                return
            r = connectedThread!!
        }
        r.write(out)
    }

    private fun connectionFailed() {
        val msg = handler?.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "Imposible conectar con el dispositivo")
        msg?.data = bundle
        handler!!.sendMessage(msg)

        // Start the service over to restart listening mode
        this@OnlineChat.start()
    }

    private fun connectionLost() {
        val msg = handler?.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "La conexión del dispositivo se perdió")
        msg?.data = bundle
        handler!!.sendMessage(msg)

        // Start the service over to restart listening mode
        this@OnlineChat.start()
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            try {
                tmp = bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

            serverSocket = tmp
        }

        override fun run() {
            name = "AcceptThread"
            var socket: BluetoothSocket?
            while (STATE_CONNECTED!= currentState ) {
                try {
                    socket = serverSocket!!.accept()
                } catch (e: IOException) {
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@OnlineChat) {
                        when (currentState) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                // start the connected thread.
                                connected(socket, socket.remoteDevice)
                            STATE_NONE, STATE_CONNECTED ->
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                }

                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                serverSocket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            socket = tmp
        }

        override fun run() {
            name = "ConnectThread"

            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter!!.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                socket!!.connect()
            } catch (e: IOException) {
                try {
                    socket!!.close()
                } catch (e2: IOException) {
                }

                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@OnlineChat) {
                connectThread = null
            }

            // Start the connected thread
            connected(socket, device)
        }

        fun cancel() {
            try {
                socket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    private inner class ReadWriteThread(private val bluetoothSocket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream?
        private val outputStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = bluetoothSocket.inputStream
                tmpOut = bluetoothSocket.outputStream
            } catch (e: IOException) {
            }

            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream!!.read(buffer)

                    // Send the obtained bytes to the UI Activity
                    handler!!.obtainMessage(
                            MESSAGE_READ, bytes, -1,
                            buffer
                    ).sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                    // Start the service over to restart listening mode
                    this@OnlineChat.start()
                    break
                }

            }
        }

        // write to OutputStream
        fun write(buffer: ByteArray) {
            try {
                outputStream!!.write(buffer)
                handler!!.obtainMessage(
                        MESSAGE_WRITE, -1, -1,
                        buffer
                ).sendToTarget()
            } catch (e: IOException) {
            }

        }

        fun cancel() {
            try {
                bluetoothSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

}