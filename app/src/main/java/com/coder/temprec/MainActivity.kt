package com.coder.temprec


import android.bluetooth.*

import android.content.Context

import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity

import java.util.*
// Main activity for the temperature receiver app
class MainActivity : AppCompatActivity() {

    // Bluetooth adapter for managing Bluetooth operations
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Bluetooth GATT client for BLE operations
    private var bluetoothGatt: BluetoothGatt? = null

    // TextView for displaying temperature and app status
    private lateinit var temperatureTextView: TextView

    // UUIDs for the service and characteristic we want to connect to
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val CHARACTERISTIC_UUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")

    private val REQUEST_ENABLE_BT = 1

    // Track whether the ESP32 device has been found
    private var deviceFound = false

    // Bluetooth manager for accessing Bluetooth services
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    // Bluetooth scanner instance for BLE scanning
    private val scanner by lazy {
        bluetoothManager.adapter?.bluetoothLeScanner
    }
}
