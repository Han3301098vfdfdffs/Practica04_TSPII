package com.example.practica4.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.practica4.R
import com.example.practica4.viewmodel.AppViewModel
import com.example.practica4.viewmodel.BluetoothViewModel

@SuppressLint("ContextCastToActivity")
@Composable
fun BluetoothScreenContent(modifier: Modifier = Modifier) {
    val appViewModel : AppViewModel = viewModel()
    val bluetoothViewModel : BluetoothViewModel = viewModel()
    val connectionState by bluetoothViewModel.connectionState.collectAsState()
    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val connectPermissionLauncher = rememberBluetoothPermissionLauncher(context)
    val scanPermissionLauncher = scanBluetoothPermissionLauncher(context, connectPermissionLauncher)
    val showDevicesDialog = remember { mutableStateOf(false) }
    val receivedMessages by bluetoothViewModel.receivedMessages
    var isSwitchOn by remember { mutableStateOf(false) }

    Column (
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Button(
            onClick = {
                bluetoothViewModel.checkPermissionsAndLoadDevices(
                    context,
                    activity,
                    scanPermissionLauncher,
                    connectPermissionLauncher
                )
                showDevicesDialog.value = true
                      },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.azul_fondo),
                contentColor = colorResource(id = R.color.white)
            )
        ) {
            Text("Seleccciona tu dispositivo")
        }

        // Diálogo para mostrar dispositivos emparejados
        if (showDevicesDialog.value && bluetoothViewModel.pairedDevices.isNotEmpty()) {
            DeviceSelectionDialog(
                show = showDevicesDialog.value,
                pairedDevices = bluetoothViewModel.pairedDevices,
                onSelect = { device ->
                    bluetoothViewModel.selectDevice(device)
                    val macAddress = device.split(" - ").getOrNull(1) ?: ""
                    appViewModel.updateMacAddress(TextFieldValue(macAddress))
                },
                onDismiss = { showDevicesDialog.value = false }
            )
        }

        Column (
            modifier = modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.Start
        ){
            Text(
                text = "Tu dipositivo:",
                fontSize = 20.sp
            )
            Text(
                text = "MAC: ${appViewModel.macAddress.text}",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = modifier.height(8.dp))

        BluetoothConnectionStatusIndicator(connectionState)

        Spacer(modifier = modifier.height(8.dp))

        TemperatureDisplay(receivedMessages)

        Spacer(modifier = modifier.height(8.dp))

        BluetoothControlButtons(
            isTempOn = isSwitchOn,
            onLedOn = {
                if (connectionState is BluetoothViewModel.ConnectionState.Connected) {
                    bluetoothViewModel.sendCommand("A", context = context)
                }
            },
            onLedOff = {
                if (connectionState is BluetoothViewModel.ConnectionState.Connected) {
                    bluetoothViewModel.sendCommand("B", context = context)
                }
            },
            onTempToggle = { newState ->
                isSwitchOn = newState
                if (connectionState is BluetoothViewModel.ConnectionState.Connected) {
                    val command = if (newState) "C" else "D"
                    bluetoothViewModel.sendCommand(command, context = context)
                }
            }
        )

        Spacer(modifier = modifier.height(16.dp))

        Button(
            onClick = {
                when (connectionState) {
                    is BluetoothViewModel.ConnectionState.Disconnected,
                    is BluetoothViewModel.ConnectionState.Error -> {
                        bluetoothViewModel.selectedDeviceAddress.value?.let { macAddress ->
                            bluetoothViewModel.connectToDeviceByMac(context, macAddress)
                        } ?: run {
                            Toast.makeText(context, "Selecciona un dispositivo primero", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        bluetoothViewModel.disconnect()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(
                text = when (connectionState) {
                    is BluetoothViewModel.ConnectionState.Disconnected,
                    is BluetoothViewModel.ConnectionState.Error -> "Conectar"
                    else -> "Desconectar"
                }
            )
        }
    }
}