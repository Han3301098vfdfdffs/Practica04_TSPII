package com.example.practica4.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.practica4.data.BluetoothRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothRepository = BluetoothRepository()
    private val selectedDeviceName = mutableStateOf<String?>(null)
    private val selectedDevice = mutableStateOf<String?>(null)

    val pairedDevices = mutableStateListOf<String>()
    val selectedDeviceAddress = mutableStateOf<String?>(null)
    val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private val _receivedMessages = mutableStateOf("--") // Valor inicial
    val receivedMessages: State<String> = _receivedMessages

    private var readThread: Thread? = null
    private var bluetoothSocket: BluetoothSocket? = null

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private fun startReadingThread(socket: BluetoothSocket) {
        readThread = Thread {
            try {
                val reader = socket.inputStream.bufferedReader()
                while (!Thread.interrupted()) {
                    val line = reader.readLine()?.trim()
                    if (!line.isNullOrEmpty()) {
                        viewModelScope.launch {
                            updateMessage(line) // Actualiza el estado aquí
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("Bluetooth", "Error: ${e.message}")
            }
        }.apply { start() }
    }

    fun connectToDeviceByMac(context: Context, macAddress: String) {
        connectionState.value = ConnectionState.Connecting

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = bluetoothRepository.connectToDeviceByMac(context, macAddress)
                bluetoothSocket = socket
                connectionState.value = ConnectionState.Connected
                startReadingThread(socket)
            } catch (e: SecurityException) {
                connectionState.value = ConnectionState.Error("Permisos insuficientes: ${e.message}")
            } catch (e: IllegalArgumentException) {
                connectionState.value = ConnectionState.Error("Dirección MAC inválida")
            } catch (e: IOException) {
                connectionState.value = ConnectionState.Error("Error de conexión: ${e.message}")
            } catch (e: Exception) {
                connectionState.value = ConnectionState.Error("Error inesperado: ${e.message}")
            }
        }
    }

    private fun updateMessage(newMessage: String) {
        _receivedMessages.value = newMessage
    }

    fun disconnect() {
        bluetoothRepository.disconnect()
        readThread?.interrupt()
        readThread = null
        connectionState.value = ConnectionState.Disconnected
    }

    fun selectDevice(deviceInfo: String) {
        val parts = deviceInfo.split(" - ")
        if (parts.size == 2) {
            selectedDeviceName.value = parts[0]
            selectedDeviceAddress.value = parts[1]
            selectedDevice.value = deviceInfo
        }
    }

    fun sendCommand(command: String, context: Context) {
        bluetoothRepository.sendCommand(command) { response ->
            viewModelScope.launch(Dispatchers.Main) {
                _receivedMessages.value = response
                Toast.makeText(context, "Arduino dice: $response", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    fun checkPermissionsAndLoadDevices(
        context: Context,
        activity: Activity,
        scanPermissionLauncher: ActivityResultLauncher<String>,
        connectPermissionLauncher: ActivityResultLauncher<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (bluetoothRepository.hasBluetoothPermissions(context)) {
                bluetoothRepository.loadPairedDevices(context)
                pairedDevices.clear()
                pairedDevices.addAll(bluetoothRepository.pairedDevices)
                bluetoothRepository.startBluetoothScan(context, connectPermissionLauncher)
            } else {
                // Aquí delegas la solicitud de permisos directamente al repositorio
                bluetoothRepository.requestPermissions(
                    activity,
                    scanPermissionLauncher,
                    connectPermissionLauncher
                )
            }
        } else {
            bluetoothRepository.loadPairedDevices(context)
            pairedDevices.clear()
            pairedDevices.addAll(bluetoothRepository.pairedDevices)
            bluetoothRepository.startBluetoothScan(context, connectPermissionLauncher)
        }
    }

}