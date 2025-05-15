package com.example.practica4.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel

class AppViewModel : ViewModel() {
    var macAddress by mutableStateOf(TextFieldValue(""))
        private set

       fun updateMacAddress(newMac : TextFieldValue) {
        macAddress = newMac
    }
}