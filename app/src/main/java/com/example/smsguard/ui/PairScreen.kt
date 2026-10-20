package com.example.smsguard.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smsguard.PairingClaimResult
import com.example.smsguard.PairingClient
import com.example.smsguard.PairingDevice
import com.example.smsguard.PairingDeviceInfo
import com.example.smsguard.PairingEndpoint
import com.example.smsguard.PairingPayload
import com.example.smsguard.PairingPreflight
import com.example.smsguard.PairingQr
import com.example.smsguard.PairingQrParser
import com.example.smsguard.PermissionState
import com.example.smsguard.Prefs
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One step of the pairing flow. Kept private so the screen owns the whole state machine. */
private sealed interface PairStep {
    data object Idle : PairStep
    data object Scanning : PairStep
    data class NeedsImei(
        val qr: PairingQr,
        val server: String,
        val device: PairingDevice
    ) : PairStep
    data class Claiming(val message: String) : PairStep
    data class Success(val deviceName: String) : PairStep
    data class Failed(val message: String, val offerAppSettings: Boolean = false) : PairStep
}

/**
 * QR pairing screen: scan a dashboard code, collect the device report, and claim the pairing.
 *
 * All identifiers are collected locally. The IMEI is never fabricated — Android 10+ frequently
 * withholds it, in which case the user is asked to type the value shown by `*#06#`.
 */
@Composable
fun PairScreen(
    initialToken: String? = null,
    onInitialTokenConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<PairStep>(PairStep.Idle) }
    var invalidQrMessage by remember { mutableStateOf<String?>(null) }
    var manualImei by remember { mutableStateOf("") }
    var manualImeiError by remember { mutableStateOf<String?>(null) }
    var claimJob by remember { mutableStateOf<Job?>(null) }

    fun claim(device: PairingDevice, imei: String, qr: PairingQr, server: String) {
        claimJob?.cancel()
        step = PairStep.Claiming("Checking pairing code…")
        claimJob = scope.launch {
            try {
                val preflightMessage = when (val preflight = PairingClient.preflight(server, qr.token)) {
                    PairingPreflight.AlreadyUsed ->
                        "This pairing code has already been used. Scan a new QR code."
                    PairingPreflight.Expired ->
                        "This pairing code has expired. Scan a new QR code."
                    is PairingPreflight.Unavailable -> preflight.message
                    PairingPreflight.Ready -> null
                }
                if (preflightMessage != null) {
                    step = PairStep.Failed(preflightMessage)
                    return@launch
                }

                step = PairStep.Claiming("Waiting for approval…")
                when (val result = PairingClient.claim(
                    baseUrl = server,
                    token = qr.token,
                    payload = PairingPayload.toJson(device, imei)
                )) {
                    is PairingClaimResult.Success -> {
                        val name = result.deviceName?.takeIf { it.isNotBlank() } ?: device.name
                        Prefs.setPairedDeviceName(context, name)
                        step = PairStep.Success(name)
                    }
                    is PairingClaimResult.AlreadyUsed -> step = PairStep.Failed(result.message)
                    is PairingClaimResult.Expired -> step = PairStep.Failed(result.message)
                    is PairingClaimResult.Rejected -> step = PairStep.Failed(result.message)
                    is PairingClaimResult.Failed -> step = PairStep.Failed(result.message)
                }
            } finally {
                claimJob = null
            }
        }
    }

    fun collectAndContinue(qr: PairingQr, server: String) {
        val device = PairingDeviceInfo.collect(context)
        val imei = device.imei
        if (PairingDeviceInfo.isValidImei(imei)) {
            claim(device, imei!!, qr, server)
        } else {
            manualImei = ""
            manualImeiError = null
            step = PairStep.NeedsImei(qr, server, device)
        }
    }

    fun handleQr(raw: String): Boolean {
        val qr = PairingQrParser.parse(raw)
        if (qr == null) {
            invalidQrMessage =
                "That QR code is not a pairing code. Look for a /link/ or smsguard://pair link."
            return false
        }

        // The scanned QR is the source of truth: post straight to the address it encodes, even
        // when that is localhost (an adb reverse tunnel or local proxy is a valid setup). Only a
        // deep-link QR without an address falls back to the saved default.
        val server = PairingEndpoint.normalizeBaseUrl(
            qr.origin ?: Prefs.pairingBaseUrl(context)
        ) ?: run {
            step = PairStep.Failed("The QR code does not contain a usable server address.")
            return true
        }
        if (qr.origin != null) Prefs.setPairingBaseUrl(context, server)

        invalidQrMessage = null
        collectAndContinue(qr, server)
        return true
    }

    /** Handles a token that arrived through a `smsguard://pair?token=...` deep link. */
    fun handleToken(token: String) {
        if (!PairingQrParser.isPlausibleToken(token)) {
            step = PairStep.Failed("That pairing link is not valid. Scan the QR code instead.")
            return
        }
        // A bare smsguard:// link carries no server address, so reuse the last dashboard address
        // this phone paired with (the emulator default on a fresh install).
        val server = PairingEndpoint.normalizeBaseUrl(Prefs.pairingBaseUrl(context)) ?: run {
            step = PairStep.Failed("No dashboard address is configured. Scan the QR code instead.")
            return
        }
        collectAndContinue(PairingQr(token = token), server)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA]
            ?: PermissionState.isGranted(context, Manifest.permission.CAMERA)
        if (cameraGranted) {
            invalidQrMessage = null
            step = PairStep.Scanning
        } else {
            step = PairStep.Failed(
                "Camera access is required to scan the pairing QR code.",
                offerAppSettings = true
            )
        }
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val current = step as? PairStep.NeedsImei ?: return@rememberLauncherForActivityResult
        if (!granted) {
            manualImeiError = "Phone permission was not granted. Enter the IMEI manually below."
            return@rememberLauncherForActivityResult
        }
        val refreshed = PairingDeviceInfo.collect(context)
        val imei = refreshed.imei
        if (PairingDeviceInfo.isValidImei(imei)) {
            claim(refreshed, imei!!, current.qr, current.server)
        } else {
            manualImeiError =
                "Android still did not provide an IMEI. Enter the value shown by *#06#."
        }
    }

    fun startScan() {
        val missing = buildList {
            if (!PermissionState.isGranted(context, Manifest.permission.CAMERA)) {
                add(Manifest.permission.CAMERA)
            }
            if (!PermissionState.isGranted(context, Manifest.permission.READ_PHONE_STATE)) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
        }
        if (missing.isEmpty()) {
            invalidQrMessage = null
            step = PairStep.Scanning
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    fun cancelClaim() {
        claimJob?.cancel()
        claimJob = null
        step = PairStep.Idle
    }

    DisposableEffect(Unit) {
        onDispose { claimJob?.cancel() }
    }

    // A token handed over by a deep link is claimed immediately; the screen still shows the
    // usual claiming/IMEI/success steps from there.
    LaunchedEffect(initialToken) {
        if (!initialToken.isNullOrBlank()) {
            onInitialTokenConsumed()
            handleToken(initialToken)
        }
    }

    when (val current = step) {
        PairStep.Idle -> IdleContent(
            onScan = ::startScan,
            modifier = modifier
        )
        PairStep.Scanning -> ScannerContent(
            invalidMessage = invalidQrMessage,
            onQrDetected = ::handleQr,
            onCancel = { step = PairStep.Idle },
            modifier = modifier
        )
        is PairStep.NeedsImei -> NeedsImeiContent(
            imei = manualImei,
            error = manualImeiError,
            hasPhonePermission =
                PermissionState.isGranted(context, Manifest.permission.READ_PHONE_STATE),
            onImeiChange = {
                manualImei = it.filter(Char::isDigit).take(17)
                manualImeiError = null
            },
            onGrantPhone = {
                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            },
            onContinue = {
                if (PairingDeviceInfo.isValidImei(manualImei)) {
                    claim(current.device, manualImei, current.qr, current.server)
                } else {
                    manualImeiError = "Enter the IMEI shown by *#06# (14–17 digits)."
                }
            },
            onRescan = ::startScan,
            modifier = modifier
        )
        is PairStep.Claiming -> ClaimingContent(
            message = current.message,
            onCancel = ::cancelClaim,
            modifier = modifier
        )
        is PairStep.Success -> SuccessContent(
            deviceName = current.deviceName,
            onPairAnother = {
                manualImei = ""
                manualImeiError = null
                step = PairStep.Idle
            },
            modifier = modifier
        )
        is PairStep.Failed -> FailedContent(
            message = current.message,
            offerAppSettings = current.offerAppSettings,
            onRescan = ::startScan,
            modifier = modifier
        )
    }
}

@Composable
private fun IdleContent(
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Pair device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Scan the QR code shown on the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Scan QR code")
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun ScannerContent(
    invalidMessage: String?,
    onQrDetected: (String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        QrScanner(
            onQrDetected = onQrDetected,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .border(3.dp, Color.White, RoundedCornerShape(20.dp))
        )

        Text(
            text = "Scan pairing QR",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (invalidMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 88.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = invalidMessage,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun NeedsImeiContent(
    imei: String,
    error: String?,
    hasPhonePermission: Boolean,
    onImeiChange: (String) -> Unit,
    onGrantPhone: () -> Unit,
    onContinue: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valid = PairingDeviceInfo.isValidImei(imei)
    val hint = error ?: if (imei.isNotEmpty() && !valid) "Enter 14–17 digits" else null
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "IMEI needed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Android hid it. Dial *#06# and enter the value below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = imei,
            onValueChange = onImeiChange,
            label = { Text("IMEI") },
            placeholder = { Text("14–17 digits") },
            singleLine = true,
            isError = error != null || (imei.isNotEmpty() && !valid),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onContinue,
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pair")
        }

        if (!hasPhonePermission) {
            TextButton(
                onClick = onGrantPhone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant phone access")
            }
        }

        TextButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text("Scan again")
        }
    }
}

@Composable
private fun ClaimingContent(
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SuccessContent(
    deviceName: String,
    onPairAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Paired",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPairAnother,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pair another")
        }
    }
}

@Composable
private fun FailedContent(
    message: String,
    offerAppSettings: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pairing failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Scan again")
        }
        if (offerAppSettings) {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(PermissionState.appSettingsIntent(context))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("App settings")
            }
        }
    }
}

/**
 * CameraX preview plus ML Kit QR analysis. The callback returns true once a code is consumed, which
 * stops further analysis without tearing down the camera process.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScanner(
    onQrDetected: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val detected = remember { AtomicBoolean(false) }
    val currentOnQrDetected by rememberUpdatedState(onQrDetected)

    DisposableEffect(lifecycleOwner) {
        val disposed = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null

        val bindCamera = Runnable {
            if (disposed.get()) return@Runnable
            runCatching {
                val cameraProvider = providerFuture.get()
                if (disposed.get()) return@runCatching
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = imageAnalysis

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (detected.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (detected.get()) return@addOnSuccessListener
                            val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                                ?: return@addOnSuccessListener
                            mainExecutor.execute {
                                if (!detected.get() && currentOnQrDetected(value)) {
                                    detected.set(true)
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }
        }

        providerFuture.addListener(bindCamera, mainExecutor)
        onDispose {
            disposed.set(true)
            detected.set(true)
            runCatching { analysis?.clearAnalyzer() }
            runCatching { provider?.unbindAll() }
            runCatching { scanner.close() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
