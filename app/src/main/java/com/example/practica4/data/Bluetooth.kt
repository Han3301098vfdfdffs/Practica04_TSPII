package com.example.practica4.data

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothRepository(context: Context) {
    val devices = mutableStateListOf<String>()
    val pairedDevices = mutableStateListOf<String>()
    private var scanCallback: ScanCallback? = null
    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        return bluetoothManager?.adapter
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermission(
        context: Context,
        connectPermissionLauncher: ActivityResultLauncher<String>? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                connectPermissionLauncher?.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }
    }

    private fun requestBluetoothPermissions(
        activity: Activity,
        scanPermissionLauncher: ActivityResultLauncher<String>,
        connectPermissionLauncher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_SCAN)) {
                AlertDialog.Builder(activity)
                    .setTitle("Permisos necesarios")
                    .setMessage("La aplicación necesita permisos de Bluetooth para escanear y conectar dispositivos")
                    .setPositiveButton("Entendido") { _, _ ->
                        scanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                        connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    .show()
            } else {
                scanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }

    fun loadPairedDevices(context: Context) {
        pairedDevices.clear()
        checkPermission(context)
        val bluetoothAdapter = getBluetoothAdapter(context)
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            pairedDevices.add("${device.name ?: "Dispositivo desconocido"} - ${device.address}")
        }
    }

    fun startBluetoothScan(
        context: Context,
        connectPermissionLauncher: ActivityResultLauncher<String>
    ) {
        checkPermission(context)
        devices.clear()
        val bluetoothAdapter = getBluetoothAdapter(context)
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                checkPermission(context, connectPermissionLauncher)

                val deviceName = result.device.name ?: "Dispositivo desconocido"
                val deviceAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        result.device.address
                    } else {
                        "Dirección no disponible"
                    }
                } else {
                    result.device.address
                }

                val deviceInfo = "$deviceName - $deviceAddress"
                if (!devices.contains(deviceInfo)) {
                    devices.add(deviceInfo)
                }
            }
        }

        scanner?.startScan(scanCallback)
    }

    fun checkPermissionButton(
        context: Context,
        activity: Activity,
        scanPermissionLauncher: ActivityResultLauncher<String>,
        connectPermissionLauncher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasBluetoothPermissions(context)) {
                loadPairedDevices(context)
                startBluetoothScan(context, connectPermissionLauncher)
            } else {
                requestBluetoothPermissions(activity, scanPermissionLauncher, connectPermissionLauncher)
            }
        }
    }
}