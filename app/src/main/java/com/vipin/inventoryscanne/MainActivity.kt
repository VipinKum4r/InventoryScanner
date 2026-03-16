package com.vipin.inventoryscanne

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vipin.inventoryscanne.camera.CameraPreviewScreen
import com.vipin.inventoryscanne.data.AppDatabase
import com.vipin.inventoryscanne.data.ExcelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InventoryScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val inventoryItems by viewModel.inventoryList.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) isScanning = true
        else Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    // 1. IMPORT LAUNCHER (With Success Toast)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                val parsedItems = ExcelHelper.importExcel(context, it)
                val dao = AppDatabase.getDatabase(context).inventoryDao()
                dao.insertAll(parsedItems)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported ${parsedItems.size} items successfully!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 2. EXPORT LAUNCHER (With Success Toast)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                ExcelHelper.exportExcel(context, it, inventoryItems)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export Saved Successfully!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                // 3. THE NEW CLEAR BUTTON
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllData()
                            Toast.makeText(context, "Data cleared. Ready for new import!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("CLEAR", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    importLauncher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel"
                    ))
                }) {
                    Text("Import")
                }

                Button(onClick = {
                    if (isScanning) {
                        isScanning = false
                    } else {
                        if (hasCameraPermission) {
                            isScanning = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }) {
                    Text(if (isScanning) "Stop Scan" else "Scan QR")
                }

                Button(onClick = {
                    exportLauncher.launch("Inventory_Status.xlsx")
                }) {
                    Text("Export")
                }
            }
        }
    ) { paddingValues ->

        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isScanning && hasCameraPermission) {
                CameraPreviewScreen(
                    onQrCodeScanned = { qrString ->
                        viewModel.processScannedQrCode(qrString)

                        // 4. VIBRATION OPTIMIZATION: Haptic feedback on successful scan
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(250.dp)
                        .background(Color.Transparent)
                ) {
                    Text(
                        text = "Aim at QR Code",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            } else {
                if (inventoryItems.isEmpty()) {
                    Text(
                        text = "No data. Please import an Excel file or scan a new item.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(inventoryItems) { item ->
                            InventoryRow(item)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryRow(item: com.vipin.inventoryscanne.data.InventoryItem) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    val scannedText = if (item.lastScanned != null) {
        "Scanned: ${dateFormat.format(Date(item.lastScanned))}"
    } else {
        "Not Scanned"
    }

    val backgroundColor = if (item.lastScanned != null) Color(0xFFE8F5E9) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = item.serialNumber, fontWeight = FontWeight.Bold)
            Text(text = item.partName, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = scannedText,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.lastScanned != null) Color(0xFF2E7D32) else Color.Gray
        )
    }
}