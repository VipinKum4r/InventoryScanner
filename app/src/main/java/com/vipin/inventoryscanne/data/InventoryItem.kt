package com.vipin.inventoryscanne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_table")
data class InventoryItem(
    @PrimaryKey
    val serialNumber: String,
    val partName: String,
    val lastScanned: Long? = null
)