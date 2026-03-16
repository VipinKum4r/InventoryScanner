package com.vipin.inventoryscanne

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vipin.inventoryscanne.data.AppDatabase
import com.vipin.inventoryscanne.data.InventoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).inventoryDao()

    // NEW: Wipe the database completely clean every time the app opens!
    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    // ADD THIS FUNCTION: Allows the UI to trigger a database wipe
    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    val inventoryList: StateFlow<List<InventoryItem>> = dao.getAllItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var lastScanTime = 0L
    private var lastScannedSerial = ""

    fun processScannedQrCode(serialNumber: String) {
        val currentTime = System.currentTimeMillis()

        if (serialNumber == lastScannedSerial && (currentTime - lastScanTime) < 5000) {
            return
        }

        lastScanTime = currentTime
        lastScannedSerial = serialNumber

        viewModelScope.launch(Dispatchers.IO) {
            val existingItem = dao.getItemBySerial(serialNumber)

            if (existingItem != null) {
                val updatedItem = existingItem.copy(lastScanned = currentTime)
                dao.insertItem(updatedItem)
            } else {
                val newItem = InventoryItem(
                    serialNumber = serialNumber,
                    partName = "UNKNOWN",
                    lastScanned = currentTime
                )
                dao.insertItem(newItem)
            }
        }
    }
}