package com.coder.temprec

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Bluetooth adapter and GATT client
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    // TextView to show temperature or status messages
    private lateinit var temperatureTextView: TextView

    // UUIDs for the custom BLE service and characteristic
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val CHARACTERISTIC_UUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")
    private val REQUEST_ENABLE_BT = 1

    // Flag to track whether the target device was found
    private var deviceFound = false

    // Lazy init BluetoothManager
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    // Lazy init BLE scanner
    private val scanner by lazy {
        bluetoothManager.adapter?.bluetoothLeScanner
    }

    // Permissions launcher to handle runtime permission results
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.i("Permission", "All permissions granted")
            checkBluetoothEnabled()
        } else {
            Log.e("Permission", "Some permissions are not granted!")
            showPermissionDeniedMessage()
        }
    }

    // Launcher to enable Bluetooth via intent
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            //           startScan()
        } else {
            temperatureTextView.text = "❌ Bluetooth must be enabled to use this app"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up UI programmatically
        temperatureTextView = TextView(this).apply {
            text = "🌡 Waiting for temperature..."
            textSize = 24f
            setPadding(30, 100, 30, 30)
        }
        setContentView(temperatureTextView)

        // Initialize Bluetooth adapter and check if device supports Bluetooth
        bluetoothAdapter = bluetoothManager.adapter ?: run {
            temperatureTextView.text = "❌ Bluetooth not supported on this device"
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG)
                .show()
            return
        }

        // Request necessary permissions
        requestPermissionsWithRationale()
    }

    // Show rationale and request permissions
    private fun requestPermissionsWithRationale() {
        val permissionsToRequest = getRequiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            checkBluetoothEnabled()
            return
        }

        val shouldShowRationale = permissionsToRequest.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        if (shouldShowRationale) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app needs Bluetooth and Location permissions to scan for and connect to the temperature sensor device.")
                .setPositiveButton("OK") { _, _ ->
                    permissionsLauncher.launch(permissionsToRequest)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    temperatureTextView.text = "❌ Permissions required to use this app"
                }
                .create()
                .show()
        } else {
            permissionsLauncher.launch(permissionsToRequest)
        }
    }

    // Show message when permission is denied
    private fun showPermissionDeniedMessage() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage("This app requires Bluetooth and Location permissions to function properly. Please grant these permissions in Settings.")
            .setPositiveButton("OK") { _, _ ->
                temperatureTextView.text = "❌ Permissions denied. Please enable in Settings."
            }
            .create()
            .show()
    }

    // Get permissions required based on Android version
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    // Enable Bluetooth if not already enabled
    private fun checkBluetoothEnabled() {
        if (!bluetoothAdapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                } else {
                    temperatureTextView.text = "❌ Bluetooth Connect permission denied"
                }
            } else {
                // For Android 11 and below
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }
        } else {
            startScan()
        }
    }

    // Start scanning for BLE devices
    private fun startScan() {
        // Permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Permission", "Bluetooth permissions not granted!")
                return
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Permission", "Location permission not granted!")
                return
            }

        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Permission", "Location permission not granted!")
            return
        }
        //set scanning message in the UI and reset the flag
        deviceFound = false
        temperatureTextView.text = "🔎 Scanning for ESP32-Thermo..."

        try {
            scanner?.startScan(scanCallback) ?: run {
                temperatureTextView.text = "❌ Bluetooth scanner not available"
                return
           }

            // Stop scanning after 10 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                stopScan()
                if (!deviceFound) {
                    runOnUiThread {
                        temperatureTextView.text = "❌ ESP32-Thermo not found"
                    }
                }
            }, 10000)
        } catch (e: Exception) {
            Log.e("BLE", "Scan error: ${e.message}")
            temperatureTextView.text = "❌ Error starting scan: ${e.message}"
        }
    }

    // Stop scanning for devices
    private fun stopScan() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                                       scanner?.stopScan(scanCallback)
                }
            } else {
                               scanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e("BLE", "Error stopping scan: ${e.message}")
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device

            // Safe way to get device name
            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name
                } else {
                    null
                }
            } else {
                device.name
            }

            Log.i("BLE", "Device found: $deviceName / ${device.address}")

            if (deviceName == "ESP32-Thermo") {
                Log.i("BLE", "ESP32-Thermo found, connecting...")
                deviceFound = true
                stopScan()
                connectToDevice(device)
                runOnUiThread {
                    temperatureTextView.text = "🔗 Connecting to ESP32-Thermo..."
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed with error: $errorCode")
            runOnUiThread {
                temperatureTextView.text = "❌ Scan failed with error: $errorCode"
            }
        }
    }

    // Connect to the BLE device
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                temperatureTextView.text = "❌ Bluetooth Connect permission denied"
                return
            }

              bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (e: Exception) {
            Log.e("BLE", "Error connecting: ${e.message}")
            temperatureTextView.text = "❌ Error connecting: ${e.message}"
        }
    }

    // GATT Callback for connection, services, and characteristic changes
    private val gattCallback = object : BluetoothGattCallback() {

        // Called when connection state changes
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected to GATT server")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread {
                        temperatureTextView.text = "❌ Bluetooth Connect permission denied"
                    }
                    return
                }

                gatt.discoverServices()
                runOnUiThread {
                    temperatureTextView.text = "🔍 Discovering services..."
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Disconnected from GATT server")
                runOnUiThread {
                    temperatureTextView.text = "❌ Disconnected from ESP32-Thermo"
                }
            } else {
                Log.e("BLE", "Connection state changed with error $status")
                runOnUiThread {
                    temperatureTextView.text = "❌ Connection error: $status"
                }
            }
        }

        // Called when services are discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                if (service == null || characteristic == null) {
                    runOnUiThread {
                        temperatureTextView.text = "❌ Service/Characteristic not found"
                    }
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                // Enable notifications for temperature data
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    runOnUiThread {
                        temperatureTextView.text = "🔄 Setting up temperature notifications..."
                    }
                } else {
                    runOnUiThread {
                        temperatureTextView.text = "❌ Notification setup failed"
                    }
                }
            }
            else {
                runOnUiThread {
                    temperatureTextView.text = "❌ Service discovery failed: $status"
                }
            }
        }
        // Called when new data is received from characteristic
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            try {
                val value = characteristic.getStringValue(0)
                Log.i("BLE", "Temperature received: $value")
                runOnUiThread {
                    temperatureTextView.text = "🌡 Temperature: $value °C"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    temperatureTextView.text = "❌ Error reading temperature"
                }
            }
        }
    }

}