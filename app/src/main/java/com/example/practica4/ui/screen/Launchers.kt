package com.example.practica4.ui.screen

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.practica4.data.BluetoothRepository

@Composable
fun rememberBluetoothPermissionLauncher(
    context: Context
): ActivityResultLauncher<String> {
    val bluetoothRepository = remember { BluetoothRepository(context) }
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bluetoothRepository.loadPairedDevices(context)
        }
    }

}

@Composable
fun scanBluetoothPermissionLauncher(
    context: Context,
    connectPermissionLauncher: ActivityResultLauncher<String>
): ActivityResultLauncher<String> {
    val bluetoothRepository = remember { BluetoothRepository(context) }
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bluetoothRepository.startBluetoothScan(
                context = context,
                connectPermissionLauncher = connectPermissionLauncher)
        }
    }
}
