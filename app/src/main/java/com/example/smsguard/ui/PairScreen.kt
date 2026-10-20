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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.smsguard.ui.components.SectionCard
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
fun PairScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(Prefs.pairingBaseUrl(context)) }
    var baseUrlError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf<PairStep>(PairStep.Idle) }
    var invalidQrMessage by remember { mutableStateOf<String?>(null) }
    var manualImei by remember { mutableStateOf("") }
    var manualImeiError by remember { mutableStateOf<String?>(null) }
    var serverNotice by remember { mutableStateOf<String?>(null) }
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

        val qrOrigin = qr.origin?.let { PairingEndpoint.normalizeBaseUrl(it) }
        val server = PairingEndpoint.normalizeBaseUrl(baseUrl) ?: run {
            step = PairStep.Failed(
                "The dashboard address is not valid. Use http://10.0.2.2:3000 on the " +
                    "emulator or your computer's LAN IP on a real phone — not localhost."
            )
            return true
        }

        // The configured address stays the trust anchor. A QR can originate anywhere, and the
        // claim contains the IMEI and other identifiers, so it must never silently redirect them.
        serverNotice = if (qrOrigin != null && qrOrigin != server) {
            "This QR points to $qrOrigin. Pairing uses the configured address $server."
        } else {
            null
        }

        invalidQrMessage = null
        collectAndContinue(qr, server)
        return true
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
        val normalized = PairingEndpoint.normalizeBaseUrl(baseUrl)
        if (normalized == null) {
            baseUrlError =
                "Enter a reachable http(s) address. localhost and 127.0.0.1 point at this phone."
            return
        }
        baseUrl = normalized
        baseUrlError = null
        serverNotice = null
        Prefs.setPairingBaseUrl(context, normalized)

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

    when (val current = step) {
        PairStep.Idle -> IdleContent(
            baseUrl = baseUrl,
            baseUrlError = baseUrlError,
            onBaseUrlChange = {
                baseUrl = it
                baseUrlError = null
            },
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
            serverNotice = serverNotice,
            onCancel = ::cancelClaim,
            modifier = modifier
        )
        is PairStep.Success -> SuccessContent(
            deviceName = current.deviceName,
            onPairAnother = {
                manualImei = ""
                manualImeiError = null
                serverNotice = null
                step = PairStep.Idle
            },
            modifier = modifier
        )
        is PairStep.Failed -> FailedContent(
            message = current.message,
            offerAppSettings = current.offerAppSettings,
            onRescan = ::startScan,
            onEditServer = { step = PairStep.Idle },
            modifier = modifier
        )
    }
}

@Composable
private fun IdleContent(
    baseUrl: String,
    baseUrlError: String?,
    onBaseUrlChange: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "Pair with dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Scan the QR code shown by the SMSGuard dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dashboard server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Server address") },
                    placeholder = { Text(PairingEndpoint.DEFAULT_BASE_URL) },
                    singleLine = true,
                    isError = baseUrlError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = {
                        Text(
                            baseUrlError
                                ?: "Emulator: http://10.0.2.2:3000 · Real phone: your PC's LAN IP, " +
                                "e.g. http://192.168.1.20:3000. Never use localhost."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "The address is saved on this phone. A QR code that contains a " +
                        "reachable server address updates it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "What pairing sends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PairingBullet(
                    "Camera access reads the QR code; phone access lets the app try to read " +
                        "the IMEI automatically."
                )
                PairingBullet(
                    "Android 10+ may hide the IMEI from regular apps. If that happens, SMSGuard " +
                        "asks you for the value instead of sending a fake one."
                )
                PairingBullet(
                    "Battery, carrier and network state are included. Location is included only " +
                        "when location permission is already granted."
                )
            }
        }

        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Scan QR code")
        }

        Text(
            text = "Tip: the dashboard QR must point at your computer, not at localhost. Set " +
                "NEXT_PUBLIC_APP_URL=http://<your-pc-lan-ip>:3000 and restart Next.js if needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PairingBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                .size(250.dp)
                .align(Alignment.Center)
                .border(3.dp, Color.White, RoundedCornerShape(24.dp))
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Scan pairing QR",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Point the camera at the code in the dashboard.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        if (invalidMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 104.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = invalidMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "IMEI needed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Android 10 and newer only share the IMEI with carrier or system apps, " +
                        "so SMSGuard could not read it automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Dial *#06# on this phone to display it, then enter it below. The app " +
                        "sends exactly what you type — it never invents a value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = imei,
                    onValueChange = onImeiChange,
                    label = { Text("IMEI") },
                    placeholder = { Text("14–17 digits") },
                    singleLine = true,
                    isError = error != null || (imei.isNotEmpty() && !valid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            error ?: if (imei.isNotEmpty() && !valid) {
                                "The IMEI must be 14 to 17 digits."
                            } else {
                                "${imei.length}/17 digits"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pair this device")
        }

        if (!hasPhonePermission) {
            OutlinedButton(
                onClick = onGrantPhone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant phone access and retry automatically")
            }
        }

        TextButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text("Scan a different code")
        }
    }
}

@Composable
private fun ClaimingContent(
    message: String,
    serverNotice: String?,
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
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Keep this screen open while the dashboard registers this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (serverNotice != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = serverNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
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
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Device paired",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = deviceName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "The dashboard can now see this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPairAnother,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pair another device")
        }
    }
}

@Composable
private fun FailedContent(
    message: String,
    offerAppSettings: Boolean,
    onRescan: () -> Unit,
    onEditServer: () -> Unit,
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
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Pairing didn't complete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onEditServer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change server address")
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Open app settings")
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
