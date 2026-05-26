package org.mozilla.tryfox.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.mozilla.tryfox.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun QrCodeScannerScreen(
    onNavigateUp: () -> Unit,
    onQrCodeScanned: (String) -> Boolean,
    modifier: Modifier = Modifier,
    cameraPermissionChecker: (Context) -> Boolean = { context ->
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    },
    requestPermissionOnStart: Boolean = true,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(cameraPermissionChecker(context))
    }
    var permissionRequestCompleted by rememberSaveable { mutableStateOf(false) }
    var unsupportedQrCodeCount by rememberSaveable { mutableStateOf(0) }
    val scannerPaused = remember { AtomicBoolean(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val unsupportedQrCodeMessage = stringResource(id = R.string.qr_scanner_unsupported_link)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        permissionRequestCompleted = true
    }

    LaunchedEffect(hasCameraPermission, requestPermissionOnStart) {
        if (!hasCameraPermission && requestPermissionOnStart && !permissionRequestCompleted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(unsupportedQrCodeCount) {
        if (unsupportedQrCodeCount > 0) {
            try {
                snackbarHostState.showSnackbar(
                    message = unsupportedQrCodeMessage,
                    duration = SnackbarDuration.Short,
                )
            } finally {
                scannerPaused.set(false)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.qr_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                id = R.string.common_back_button_description,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    scannerPaused = scannerPaused,
                    onQrCodeScanned = { rawValue ->
                        val handled = onQrCodeScanned(rawValue)
                        if (!handled) {
                            unsupportedQrCodeCount += 1
                        }
                    },
                )
            } else {
                CameraPermissionDeniedState(
                    onRetryClick = {
                        permissionRequestCompleted = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionDeniedState(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(id = R.string.qr_scanner_permission_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = onRetryClick,
        ) {
            Text(stringResource(id = R.string.qr_scanner_retry_permission))
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun CameraPreview(
    scannerPaused: AtomicBoolean,
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(scanner) {
        onDispose {
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { previewView },
    )

    DisposableEffect(context, lifecycleOwner, previewView, scanner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false

        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    if (disposed) {
                        cameraProvider.unbindAll()
                        return@runCatching
                    }

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                cameraExecutor,
                                QrCodeAnalyzer(
                                    scanner = scanner,
                                    scannerPaused = scannerPaused,
                                    onQrCodeScanned = onQrCodeScanned,
                                ),
                            )
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                }.onFailure {
                    if (!disposed) {
                        cameraUnavailable = true
                    }
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            if (cameraProviderFuture.isDone) {
                runCatching {
                    cameraProviderFuture.get().unbindAll()
                }
            }
            cameraExecutor.shutdown()
        }
    }

    if (cameraUnavailable) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.qr_scanner_camera_unavailable))
        }
    }
}

private class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val scannerPaused: AtomicBoolean,
    private val onQrCodeScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (rawValue != null && scannerPaused.compareAndSet(false, true)) {
                    onQrCodeScanned(rawValue)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
