package com.example.practica4.viewmodel

import android.annotation.SuppressLint
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private var context: Context = application.applicationContext

    private val bluetoothRepository = BluetoothRepository()
    private val selectedDeviceName = mutableStateOf<String?>(null)
    private val selectedDevice = mutableStateOf<String?>(null)

    val pairedDevices = mutableStateListOf<String>()
    val selectedDeviceAddress = mutableStateOf<String?>(null)
    val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private var lastCommandSent: String? = null
    private var commandTimeoutJob: Job? = null
    private val COMMAND_TIMEOUT = 3000L // 3 segundos para timeout

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
            Log.i("Bluetooth", "Se inició la lectura del socket Bluetooth")
            try {
                val reader = socket.inputStream.bufferedReader()
                while (!Thread.interrupted()) {
                    try {
                        val line = reader.readLine()?.trim()
                        if (!line.isNullOrEmpty()) {
                            viewModelScope.launch {
                                when (line) {
                                    "LED ON" -> {
                                        Log.i("Bluetooth", "Se encendió el LED")
                                        showToast("LED encendido correctamente")
                                        lastCommandSent = null // Resetear el comando pendiente
                                        commandTimeoutJob?.cancel()
                                    }
                                    "LED OFF" -> {
                                        Log.i("Bluetooth", "Se apagó el LED")
                                        showToast("LED apagado correctamente")
                                        lastCommandSent = null // Resetear el comando pendiente
                                        commandTimeoutJob?.cancel()
                                    }
                                    else -> {
                                        updateMessage(line)
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e("Bluetooth", "Error leyendo mensaje: ${e.message}")
                        showToast("Error: No se están recibiendo datos")
                        break
                    }
                }
                Log.i("Bluetooth", "Lectura interrumpida manualmente")
            } catch (e: IOException) {
                Log.e("Bluetooth", "Error durante la lectura: ${e.message}")
                showToast("Error: Conexión interrumpida")
            }
        }.apply { start() }
    }

    private fun showToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
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
                val errorMsg = "Error de conexión: ${e.message}"
                showToast(errorMsg)
                connectionState.value = ConnectionState.Error(errorMsg)
            } catch (e: Exception) {
                connectionState.value = ConnectionState.Error("Error inesperado: ${e.message}")
                showToast("Error Inesperado")
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


    fun sendCommand(command: String) {
        try {
            if (connectionState.value !is ConnectionState.Connected) {
                showToast("Error: No hay conexión Bluetooth")
                return
            }

            lastCommandSent = command
            bluetoothRepository.sendCommand(command)
            Log.i("BluetoothViewModel", "Comando enviado correctamente: \"$command\"")
            showToast("Comando enviado correctamente: \"$command\"")

            // Configurar timeout para verificar confirmación
            commandTimeoutJob = viewModelScope.launch {
                delay(COMMAND_TIMEOUT)
                if (lastCommandSent == command) {
                    when (command) {
                        "A" -> showToast("Error: No se confirmó el encendido del LED")
                        "B" -> showToast("Error: No se confirmó el apagado del LED")
                        else -> showToast("Error: No se recibió confirmación")
                    }
                    lastCommandSent = null
                }
            }

        } catch (e: IOException) {
            Log.e("BluetoothViewModel", "Error enviando comando: ${e.message}")
            showToast("Error: No se pudo enviar el comando")
            connectionState.value = ConnectionState.Error("Error de comunicación")
        } catch (e: Exception) {
            Log.e("BluetoothViewModel", "Error inesperado: ${e.message}")
            showToast("Error inesperado al enviar comando")
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