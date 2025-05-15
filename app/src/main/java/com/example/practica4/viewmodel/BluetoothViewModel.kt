package com.example.practica4.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.bluetooth.BluetoothSocket
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.practica4.data.BluetoothRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothRepository = BluetoothRepository(getApplication())
    private val selectedDeviceName = mutableStateOf<String?>(null)
    private val selectedDevice = mutableStateOf<String?>(null)
    private val _temperature = MutableStateFlow("--")

    // Estados existentes
    val pairedDevices = mutableStateListOf<String>()
    val selectedDeviceAddress = mutableStateOf<String?>(null)
    val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val temperature: StateFlow<String> = _temperature.asStateFlow()

    //private val _receivedMessages = MutableStateFlow<List<String>>(emptyList())
    //val receivedMessages: StateFlow<List<String>> = _receivedMessages.asStateFlow()

    private val _receivedMessages = mutableStateOf("--") // Valor inicial
    val receivedMessages: State<String> = _receivedMessages

    private var readThread: Thread? = null

    // UUID para SPP (Serial Port Profile)
    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Socket y estado de conexión
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

    private fun updateMessage(newMessage: String) {
        _receivedMessages.value = newMessage
    }

    // Función para conectar a un dispositivo por dirección MAC
    fun connectToDeviceByMac(context: Context, macAddress: String) {
        if (!bluetoothRepository.hasBluetoothPermissions(context)) {
            connectionState.value = ConnectionState.Error("Permisos de Bluetooth no concedidos")
            return
        }

        val bluetoothAdapter = bluetoothRepository.getBluetoothAdapter(context) ?: run {
            connectionState.value = ConnectionState.Error("Bluetooth no disponible")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            connectionState.value = ConnectionState.Error("Bluetooth está desactivado")
            return
        }

        connectionState.value = ConnectionState.Connecting

        try {
            // Obtener el dispositivo Bluetooth por su dirección MAC
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.getRemoteDevice(macAddress)
                } else {
                    connectionState.value =
                        ConnectionState.Error("Permiso BLUETOOTH_CONNECT requerido")
                    return
                }
            } else {
                bluetoothAdapter.getRemoteDevice(macAddress)
            }

            // Crear un socket seguro RFCOMM
            val socket = device.createRfcommSocketToServiceRecord(sppUUID)
            bluetoothSocket = socket

            // Cancelar descubrimiento para mejorar la conexión
            bluetoothAdapter.cancelDiscovery()

            // Conectar en un hilo separado para no bloquear la UI
            Thread {
                try {
                    socket.connect()
                    connectionState.value = ConnectionState.Connected
                    Log.d("BluetoothViewModel", "Conectado a ${device.name ?: "dispositivo desconocido"}")
                    startReadingThread(socket)
                } catch (e: IOException) {
                    Log.e("BluetoothViewModel", "Error al conectar: ${e.message}")
                    connectionState.value = ConnectionState.Error("Error de conexión: ${e.message}")
                    try {
                        socket.close()
                    } catch (closeException: IOException) {
                        Log.e("BluetoothViewModel", "Error al cerrar socket: ${closeException.message}")
                    }
                }
            }.start()

        } catch (e: SecurityException) {
            connectionState.value = ConnectionState.Error("Permisos insuficientes: ${e.message}")
        } catch (e: IllegalArgumentException) {
            connectionState.value = ConnectionState.Error("Dirección MAC inválida")
        } catch (e: Exception) {
            connectionState.value = ConnectionState.Error("Error inesperado: ${e.message}")
        }
    }



    private fun handleError(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            connectionState.value = ConnectionState.Error(message)
        }
    }

    // Función para desconectar
    fun disconnect() {
        readThread?.interrupt()
        readThread = null
        try {
            bluetoothSocket?.close()
            connectionState.value = ConnectionState.Disconnected
        } catch (e: IOException) {
            Log.e("BluetoothViewModel", "Error al desconectar: ${e.message}")
            connectionState.value = ConnectionState.Error("Error al desconectar: ${e.message}")
        }
        bluetoothSocket = null
    }

    // Funciones existentes (se mantienen igual)
    fun selectDevice(deviceInfo: String) {
        val parts = deviceInfo.split(" - ")
        if (parts.size == 2) {
            selectedDeviceName.value = parts[0]
            selectedDeviceAddress.value = parts[1]
            selectedDevice.value = deviceInfo
        }
    }



    fun sendCommand(command: String, onResponse: (String) -> Unit = {}, context: Context) {
        try {
            bluetoothSocket?.let { socket ->
                if (connectionState.value is ConnectionState.Connected) {
                    Thread {
                        try {
                            // Envía el comando
                            socket.outputStream.write(command.toByteArray())
                            socket.outputStream.flush()
                            Log.d("Bluetooth", "Comando enviado: $command")

                            // Espera y lee la respuesta (timeout de 2 segundos)
                            val startTime = System.currentTimeMillis()
                            val buffer = ByteArray(1024)
                            var response = ""

                            while (System.currentTimeMillis() - startTime < 2000) {
                                if (socket.inputStream.available() > 0) {
                                    val bytesRead = socket.inputStream.read(buffer)
                                    response += String(buffer, 0, bytesRead)
                                    if (response.contains('\n')) { // Fin de línea
                                        break
                                    }
                                }
                                Thread.sleep(50)
                            }

                            if (response.isNotEmpty()) {
                                Log.d("Bluetooth", "Respuesta recibida: $response")
                                viewModelScope.launch(Dispatchers.Main) {
                                    onResponse(response.trim())
                                    // Puedes mostrar un Toast con la respuesta
                                    Toast.makeText(
                                        context,
                                        "Arduino dice: $response",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Log.w("Bluetooth", "No hubo respuesta del Arduino")
                            }
                        } catch (e: IOException) {
                            Log.e("Bluetooth", "Error en comunicación", e)
                            handleError("Error de comunicación: ${e.message}")
                        }
                    }.start()
                }
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error general", e)
        }
    }


    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    fun syncPairedDevices(context: Context) {
        bluetoothRepository.loadPairedDevices(context)
        pairedDevices.clear()
        pairedDevices.addAll(bluetoothRepository.pairedDevices)
    }

}