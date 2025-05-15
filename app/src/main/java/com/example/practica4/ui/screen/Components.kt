package com.example.practica4.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.practica4.viewmodel.BluetoothViewModel

@Composable
fun BluetoothConnectionStatusIndicator(connectionState: BluetoothViewModel.ConnectionState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Estado:")
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = when (connectionState) {
                        is BluetoothViewModel.ConnectionState.Disconnected -> Color.Gray
                        is BluetoothViewModel.ConnectionState.Connecting -> Color.Yellow
                        is BluetoothViewModel.ConnectionState.Connected -> Color.Green
                        is BluetoothViewModel.ConnectionState.Error -> Color.Red
                    },
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun RoundButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(100.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun DeviceSelectionDialog(
    show: Boolean,
    pairedDevices: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Dispositivos emparejados") },
            text = {
                Column {
                    pairedDevices.forEach { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelect(device)
                                    onDismiss()
                                }
                        ) {
                            Text(
                                text = device,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun TemperatureDisplay(temperature: String) {
    Text(
        text = "Temperatura: $temperature °C",
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        color = when {
            temperature == "--" -> Color.Gray
            (temperature.toFloatOrNull() ?: 0f) > 30 -> Color.Red
            else -> Color.Blue
        }
    )
}

@Composable
fun BluetoothControlButtons(
    onLedOn: () -> Unit,
    onLedOff: () -> Unit,
    isTempOn: Boolean,
    onTempToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RoundButton("LED ON", onClick = onLedOn, color = Color.Green)
        RoundButton("LED OFF", onClick = onLedOff, color = Color.Red)
        Switch(
            checked = isTempOn,
            onCheckedChange = onTempToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Blue,
                uncheckedThumbColor = Color.Gray
            )
        )
    }
}