package com.example.practica4.model

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.practica4.viewmodel.BluetoothViewModel.ConnectionState
import java.io.IOException
import java.util.UUID
