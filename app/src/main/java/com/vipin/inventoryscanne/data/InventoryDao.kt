package com.vipin.inventoryscanne.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_table ORDER BY lastScanned DESC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_table WHERE serialNumber = :serial LIMIT 1")
    fun getItemBySerial(serial: String): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: InventoryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<InventoryItem>)

    // NEW: Function to wipe the database clean
    @Query("DELETE FROM inventory_table")
    fun deleteAll()
}