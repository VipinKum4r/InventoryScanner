package com.vipin.inventoryscanne.camera

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

// 1. The Analyzer: This processes the camera frames one by one
@OptIn(ExperimentalGetImage::class)
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // Configure ML Kit to ONLY look for QR Codes (makes it much faster)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Convert the CameraX frame into an ML Kit image
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        // If we find a QR code, pass the string value back out
                        barcode.rawValue?.let { value ->
                            onQrCodeScanned(value)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("QrCodeAnalyzer", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    // CRITICAL STEP: See "Beginner Mistake" note below
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

// 2. The UI Component: This displays the camera feed on the screen
@Composable
fun CameraPreviewScreen(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // AndroidView bridges the old XML-based PreviewView into Jetpack Compose
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Use Case 1: Show the preview on the screen
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider) // FIXED
                }

                // Use Case 2: Analyze the frames for QR codes
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            executor,
                            QrCodeAnalyzer { qrString ->
                                onQrCodeScanned(qrString)
                            }
                        )
                    }

                // We want the back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind any previous use cases before binding new ones
                    cameraProvider.unbindAll()

                    // Bind the camera to the lifecycle of this Compose screen
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreviewScreen", "Camera binding failed", e)
                }
            }, executor)

            previewView // Return the view
        },
        modifier = Modifier.fillMaxSize()
    )
}