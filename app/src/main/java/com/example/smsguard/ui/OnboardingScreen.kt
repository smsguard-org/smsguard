package com.example.smsguard.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.smsguard.PermissionEntry
import com.example.smsguard.PermissionGroup
import com.example.smsguard.PermissionState
import com.example.smsguard.PermissionStatus
import com.example.smsguard.Prefs
import com.example.smsguard.ProtectionService
import com.example.smsguard.R
import com.example.smsguard.SmsGuardCommand
import com.example.smsguard.TrustedContact
import com.example.smsguard.ui.components.rememberContactImporter

private const val WELCOME_STEP = 0
private const val PERMISSIONS_STEP = 1
private const val CONTACT_STEP = 2
private const val SECURITY_STEP = 3
private const val DONE_STEP = 4
private const val TOTAL_STEPS = 5

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(WELCOME_STEP) }
    
    var permissionTrigger by remember { mutableIntStateOf(0) }
    var lastRequestResult by remember {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }

    var smsEnabled by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    // Trusted contacts captured during onboarding. Persisted as they are added so the list and
    // the Continue-enablement stay in sync with storage.
    var contactsList by remember { mutableStateOf(Prefs.trustedContactsList(context)) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    fun refreshContacts() {
        contactsList = Prefs.trustedContactsList(context)
    }

    // Same phone-book picker as the Contacts tab: requests contacts access, then lists and
    // searches the address book before adding the chosen number as a trusted contact.
    val importContact = rememberContactImporter { contact ->
        Prefs.addTrustedContact(
            context,
            name = contact.name,
            phoneNumber = contact.number,
            isPrimary = contactsList.isEmpty()
        )
        refreshContacts()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Prefs.setHasRequestedPermissions(context, true)
        lastRequestResult = PermissionState.mergeGrants(
            context,
            result,
            PermissionState.allPermissions
        )
        permissionTrigger++
    }

    // Since Android 15 a blocked SMS permission can only be cleared from system Settings, and
    // that grant never reaches the launcher callback above. Re-reading the OS on every
    // foreground return is what makes the change visible instead of leaving a stale red row.
    LifecycleResumeEffect(context) {
        permissionTrigger++
        onPauseOrDispose {}
    }

    val entries = remember(permissionTrigger, lastRequestResult) {
        PermissionState.entries(context)
    }

    fun statusOf(group: PermissionGroup): PermissionStatus =
        entries.firstOrNull { it.group == group }?.status ?: PermissionStatus.DENIED

    val receiveSms = statusOf(PermissionState.permissionGroups[0])
    val requiredSatisfied = PermissionState.requiredGroups.all {
        statusOf(it) == PermissionStatus.GRANTED
    }
    val anyBlocked = entries.any { it.status == PermissionStatus.BLOCKED }

    val requestMissing: () -> Unit = {
        permissionLauncher.launch(
            PermissionState.missing(context).toTypedArray()
        )
    }
    val openAppSettings: () -> Unit = {
        runCatching {
            context.startActivity(PermissionState.appSettingsIntent(context))
        }
    }

    val pinValid = SmsGuardCommand.forPin(pin) != null
    val pinMatches = pin == pinConfirm && pin.isNotEmpty()

    BackHandler(enabled = step > WELCOME_STEP) {
        step--
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            topBar = {
                val animatedProgress by animateFloatAsState(
                    targetValue = (step + 1) / TOTAL_STEPS.toFloat(),
                    label = "ProgressAnimation"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    when (step) {
                        WELCOME_STEP -> FullWidthButton(
                            text = stringResource(R.string.onboarding_continue),
                            onClick = { step++ }
                        )
                        DONE_STEP -> FullWidthButton(
                            text = stringResource(R.string.onboarding_get_started),
                            onClick = {
                                Prefs.setServiceEnabled(context, true)
                                Prefs.setOnboarded(context, true)
                                ProtectionService.start(context)
                                onComplete()
                            }
                        )
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    onClick = { step-- },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.onboarding_back))
                                }
                                if (step == PERMISSIONS_STEP && !requiredSatisfied) {
                                    Button(
                                        onClick = requestMissing,
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text(
                                            stringResource(
                                                if (receiveSms == PermissionStatus.BLOCKED)
                                                    R.string.perm_try_again
                                                else
                                                    R.string.grant_permissions_button
                                            )
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (step == SECURITY_STEP) {
                                                Prefs.setPin(context, pin)
                                                Prefs.setSmsEnabled(context, smsEnabled)
                                            }
                                            step++
                                        },
                                        enabled = when (step) {
                                            SECURITY_STEP -> pinValid && pinMatches
                                            CONTACT_STEP -> contactsList.isNotEmpty()
                                            else -> true
                                        },
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text(stringResource(R.string.onboarding_continue))
                                    }
                                }
                            }
                            if (step == PERMISSIONS_STEP && anyBlocked) {
                                TextButton(onClick = openAppSettings) {
                                    Text(stringResource(R.string.perm_open_settings))
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "StepTransition"
            ) { targetStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (targetStep) {
                        WELCOME_STEP -> WelcomeStep()
                        PERMISSIONS_STEP -> PermissionsStep(
                            entries = entries,
                            anyBlocked = anyBlocked,
                            onRequest = requestMissing,
                            onOpenSettings = openAppSettings
                        )
                        CONTACT_STEP -> ContactStep(
                            contacts = contactsList,
                            name = newContactName,
                            onNameChange = { newContactName = it },
                            phone = newContactPhone,
                            onPhoneChange = { newContactPhone = it },
                            canAdd = newContactName.isNotBlank() && newContactPhone.isNotBlank(),
                            onAdd = {
                                Prefs.addTrustedContact(
                                    context,
                                    name = newContactName.trim(),
                                    phoneNumber = newContactPhone.trim(),
                                    isPrimary = contactsList.isEmpty()
                                )
                                newContactName = ""
                                newContactPhone = ""
                                refreshContacts()
                            },
                            onImport = importContact,
                            onRemove = { contact ->
                                Prefs.removeTrustedContact(context, contact.id)
                                refreshContacts()
                            }
                        )
                        SECURITY_STEP -> SecurityStep(
                            pin = pin,
                            onPinChange = { value ->
                                pin = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                            },
                            pinConfirm = pinConfirm,
                            onPinConfirmChange = { value ->
                                pinConfirm = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                            },
                            pinValid = pinValid,
                            pinMatches = pinMatches
                        )
                        DONE_STEP -> DoneStep(
                            smsEnabled = smsEnabled,
                            trustedContactCount = contactsList.size
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun PermissionsStep(
    entries: List<PermissionEntry>,
    anyBlocked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_permissions_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    if (anyBlocked) {
        BlockedPermissionCard(onOpenSettings = onOpenSettings)
    }

    entries.forEach { entry ->
        PermissionRow(
            title = stringResource(entry.labelRes),
            description = stringResource(entry.descRes),
            status = entry.status
        )
    }

    if (entries.any { it.status != PermissionStatus.GRANTED }) {
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.grant_permissions_button))
        }
    }
}

@Composable
private fun BlockedPermissionCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.perm_blocked_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.perm_blocked_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.perm_blocked_howto),
                style = MaterialTheme.typography.bodySmall
            )
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.perm_open_settings))
            }
            Text(
                text = stringResource(R.string.perm_blocked_adb),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ContactStep(
    contacts: List<TrustedContact>,
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onRemove: (TrustedContact) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_contact_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_contact_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.onboarding_contact_name_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_contact_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    TextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text(stringResource(R.string.onboarding_contact_phone_label)) },
        placeholder = { Text(stringResource(R.string.onboarding_contact_phone_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAdd,
            enabled = canAdd,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.onboarding_contact_add))
        }
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ImportContacts, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.onboarding_contact_import))
        }
    }

    if (contacts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_contact_required),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    } else {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.onboarding_contact_added_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        contacts.forEach { contact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onRemove(contact) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.onboarding_contact_remove),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityStep(
    pin: String,
    onPinChange: (String) -> Unit,
    pinConfirm: String,
    onPinConfirmChange: (String) -> Unit,
    pinValid: Boolean,
    pinMatches: Boolean
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_sec_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_sec_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text(stringResource(R.string.pin_label)) },
        placeholder = { Text(stringResource(R.string.pin_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = pin.isNotEmpty() && !pinValid,
        supportingText = {
            if (pin.isNotEmpty() && !pinValid) {
                Text(stringResource(R.string.pin_too_short))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    TextField(
        value = pinConfirm,
        onValueChange = onPinConfirmChange,
        label = { Text(stringResource(R.string.pin_confirm_label)) },
        placeholder = { Text(stringResource(R.string.pin_confirm_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = pinConfirm.isNotEmpty() && !pinMatches,
        supportingText = {
            if (pinConfirm.isNotEmpty() && !pinMatches) {
                Text(stringResource(R.string.pin_mismatch))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DoneStep(
    smsEnabled: Boolean,
    trustedContactCount: Int
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_done_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_secure_defaults),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    SummaryRow(stringResource(R.string.enable_sms_commands), if (smsEnabled) "On" else "Off")
    SummaryRow("Protection Mode", "Active")
    SummaryRow(
        stringResource(R.string.onboarding_step_contact),
        if (trustedContactCount == 1) "1 contact"
        else "$trustedContactCount contacts"
    )
    Text(
        text = "You can enable background protection and configure the battery beacon in the Settings tab later.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    status: PermissionStatus
) {
    val statusLabel = when (status) {
        PermissionStatus.GRANTED -> stringResource(R.string.perm_status_granted)
        PermissionStatus.DENIED -> stringResource(R.string.perm_status_denied)
        PermissionStatus.BLOCKED -> stringResource(R.string.perm_status_blocked)
    }
    val dotColor = when (status) {
        PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primary
        PermissionStatus.DENIED -> MaterialTheme.colorScheme.error
        PermissionStatus.BLOCKED -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FullWidthButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(text)
    }
}
