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
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
//           startScan()
        }
    }
}
