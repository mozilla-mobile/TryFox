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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }
    val scannerPaused = remember { AtomicBoolean(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val unsupportedQrCodeMessage = stringResource(id = R.string.qr_scanner_unsupported_link)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        permissionRequestCompleted = true
        if (granted) {
            cameraUnavailable = false
        }
    }

    LaunchedEffect(hasCameraPermission, requestPermissionOnStart) {
        if (!hasCameraPermission && requestPermissionOnStart && !permissionRequestCompleted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraUnavailable = false
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
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        scannerPaused = scannerPaused,
                        onCameraAvailable = { cameraUnavailable = false },
                        onCameraUnavailable = { cameraUnavailable = true },
                        onQrCodeScanned = { rawValue ->
                            val handled = onQrCodeScanned(rawValue)
                            if (!handled) {
                                unsupportedQrCodeCount += 1
                            }
                        },
                    )
                    if (cameraUnavailable) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(id = R.string.qr_scanner_camera_unavailable))
                        }
                    } else {
                        ScannerVisorOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                CameraPermissionDeniedState(
                    onRetryClick = {
                        cameraUnavailable = false
                        permissionRequestCompleted = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
internal fun ScannerVisorOverlay(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = (maxWidth - VISOR_HORIZONTAL_PADDING * 2)
            .coerceAtLeast(0.dp)
        val availableHeight = (maxHeight - VISOR_VERTICAL_PADDING * 2 - VISOR_STATUS_TOP_PADDING)
            .coerceAtLeast(0.dp)
        val visorSize = availableWidth
            .coerceAtMost(availableHeight)
            .coerceAtMost(VISOR_MAX_SIZE)
        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val visorSizePx = visorSize.toPx()
            val left = (size.width - visorSizePx) / 2f
            val top = (size.height - visorSizePx) / 2f
            val right = left + visorSizePx
            val bottom = top + visorSizePx
            val scrimColor = Color.Black.copy(alpha = VISOR_SCRIM_ALPHA)

            drawRect(
                color = scrimColor,
                topLeft = Offset.Zero,
                size = Size(size.width, top),
            )
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, bottom),
                size = Size(size.width, size.height - bottom),
            )
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, top),
                size = Size(left, visorSizePx),
            )
            drawRect(
                color = scrimColor,
                topLeft = Offset(right, top),
                size = Size(size.width - right, visorSizePx),
            )
        }

        Canvas(
            modifier = Modifier
                .size(visorSize),
        ) {
            val cornerLength = VISOR_CORNER_LENGTH.toPx()
            val strokeWidth = VISOR_CORNER_STROKE.toPx()
            val maxX = size.width
            val maxY = size.height

            drawLine(primaryColor, Offset.Zero, Offset(cornerLength, 0f), strokeWidth, StrokeCap.Round)
            drawLine(primaryColor, Offset.Zero, Offset(0f, cornerLength), strokeWidth, StrokeCap.Round)

            drawLine(primaryColor, Offset(maxX, 0f), Offset(maxX - cornerLength, 0f), strokeWidth, StrokeCap.Round)
            drawLine(primaryColor, Offset(maxX, 0f), Offset(maxX, cornerLength), strokeWidth, StrokeCap.Round)

            drawLine(primaryColor, Offset(0f, maxY), Offset(cornerLength, maxY), strokeWidth, StrokeCap.Round)
            drawLine(primaryColor, Offset(0f, maxY), Offset(0f, maxY - cornerLength), strokeWidth, StrokeCap.Round)

            drawLine(primaryColor, Offset(maxX, maxY), Offset(maxX - cornerLength, maxY), strokeWidth, StrokeCap.Round)
            drawLine(primaryColor, Offset(maxX, maxY), Offset(maxX, maxY - cornerLength), strokeWidth, StrokeCap.Round)
        }

        Text(
            modifier = Modifier.offset(y = visorSize / 2 + VISOR_STATUS_TOP_PADDING),
            text = stringResource(id = R.string.qr_scanner_status_scanning),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
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

private val VISOR_HORIZONTAL_PADDING = 24.dp
private val VISOR_VERTICAL_PADDING = 24.dp
private val VISOR_MAX_SIZE = 280.dp
private val VISOR_CORNER_LENGTH = 32.dp
private val VISOR_CORNER_STROKE = 4.dp
private val VISOR_STATUS_TOP_PADDING = 24.dp
private const val VISOR_SCRIM_ALPHA = 0.55f

@Composable
@Suppress("LongMethod")
private fun CameraPreview(
    scannerPaused: AtomicBoolean,
    onCameraAvailable: () -> Unit,
    onCameraUnavailable: () -> Unit,
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
                    onCameraAvailable()
                }.onFailure {
                    if (!disposed) {
                        onCameraUnavailable()
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
