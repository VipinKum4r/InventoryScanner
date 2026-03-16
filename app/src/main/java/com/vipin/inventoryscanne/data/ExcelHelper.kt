package com.vipin.inventoryscanne.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelHelper {

    private const val CACHE_FILE_NAME = "cached_inventory.xlsx"
    private const val TARGET_SHEET_NAME = "GAS_Equipments"

    private const val COL_HARDWARE_DESC = 3 // Column D
    private const val COL_SERIAL = 6        // Column G
    private const val COL_TIMESTAMP = 8     // Column I

    fun importExcel(context: Context, uri: Uri): List<InventoryItem> {
        val inventoryList = mutableListOf<InventoryItem>()

        try {
            val cachedFile = File(context.cacheDir, CACHE_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }

            FileInputStream(cachedFile).use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet(TARGET_SHEET_NAME)

                if (sheet == null) {
                    Log.e("ExcelHelper", "Sheet $TARGET_SHEET_NAME not found!")
                    return emptyList()
                }

                for (row in sheet) {
                    if (row.rowNum == 0) continue

                    val serialCell = row.getCell(COL_SERIAL)
                    val serialNumber = serialCell?.toString()?.trim()

                    val nameCell = row.getCell(COL_HARDWARE_DESC)
                    val partName = nameCell?.toString()?.trim() ?: "UNKNOWN PART"

                    if (!serialNumber.isNullOrEmpty()) {
                        inventoryList.add(InventoryItem(serialNumber, partName, null))
                    }
                }
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e("ExcelHelper", "Error importing Excel", e)
        }

        return inventoryList
    }

    fun exportExcel(context: Context, exportUri: Uri, items: List<InventoryItem>) {
        try {
            val cachedFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (!cachedFile.exists()) return

            FileInputStream(cachedFile).use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheet(TARGET_SHEET_NAME)

                if (sheet != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                    // Create a map of ALL items the user scanned
                    val scannedItems = items.filter { it.lastScanned != null }.associateBy({ it.serialNumber }, { it })

                    // NEW: Keep track of which items we successfully matched to existing rows
                    val updatedSerials = mutableSetOf<String>()
                    var maxRowNum = 0

                    // 1. Update existing rows
                    for (row in sheet) {
                        maxRowNum = maxOf(maxRowNum, row.rowNum)

                        if (row.rowNum == 0) {
                            var cell = row.getCell(COL_TIMESTAMP)
                            if (cell == null) cell = row.createCell(COL_TIMESTAMP)
                            cell.setCellValue("Inventory scanned")
                            continue
                        }

                        val serialCell = row.getCell(COL_SERIAL)
                        val serialNumber = serialCell?.toString()?.trim()

                        if (serialNumber != null && scannedItems.containsKey(serialNumber)) {
                            val item = scannedItems[serialNumber]!!
                            updatedSerials.add(serialNumber) // Mark as found!

                            var targetCell = row.getCell(COL_TIMESTAMP)
                            if (targetCell == null) targetCell = row.createCell(COL_TIMESTAMP)
                            targetCell.setCellValue(dateFormat.format(Date(item.lastScanned!!)))
                        }
                    }

                    // 2. NEW: Append brand new rows for items that weren't in the Excel sheet
                    val newItems = scannedItems.filterKeys { !updatedSerials.contains(it) }.values
                    for (newItem in newItems) {
                        maxRowNum++
                        val newRow = sheet.createRow(maxRowNum)

                        // Write to Column D (Hardware Description)
                        var descCell = newRow.getCell(COL_HARDWARE_DESC)
                        if (descCell == null) descCell = newRow.createCell(COL_HARDWARE_DESC)
                        descCell.setCellValue(newItem.partName) // Will say "UNKNOWN"

                        // Write to Column G (Project No / Serial)
                        var serialCell = newRow.getCell(COL_SERIAL)
                        if (serialCell == null) serialCell = newRow.createCell(COL_SERIAL)
                        serialCell.setCellValue(newItem.serialNumber)

                        // Write to Column I (Timestamp)
                        var timeCell = newRow.getCell(COL_TIMESTAMP)
                        if (timeCell == null) timeCell = newRow.createCell(COL_TIMESTAMP)
                        timeCell.setCellValue(dateFormat.format(Date(newItem.lastScanned!!)))
                    }
                }

                context.contentResolver.openOutputStream(exportUri)?.use { outputStream ->
                    workbook.write(outputStream)
                }
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e("ExcelHelper", "Error exporting Excel", e)
        }
    }
}