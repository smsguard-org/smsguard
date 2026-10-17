package com.example.smsguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Watches active subscriptions while Protection Mode is running.
 *
 * The first successful reading is only saved as a baseline. Later removal is remembered without
 * sending an SMS; an inserted or replaced card then produces one alert. This avoids a false
 * alarm during initial setup while still covering both a direct swap and remove-then-insert flow.
 */
class SimChangeMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val subscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)
    private val executor: Executor = appContext.mainExecutor
    private var listener: SubscriptionManager.OnSubscriptionsChangedListener? = null

    fun start() {
        if (!hasPhoneStatePermission() || subscriptionManager == null || listener != null) return
        listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = handleSubscriptionsChanged()
        }.also { subscriptionManager.addOnSubscriptionsChangedListener(executor, it) }
    }

    fun stop() {
        listener?.let { subscriptionManager?.removeOnSubscriptionsChangedListener(it) }
        listener = null
    }

    @SuppressLint("MissingPermission")
    private fun handleSubscriptionsChanged() {
        if (!Prefs.serviceEnabled(appContext) || !hasPhoneStatePermission()) return
        val current = (subscriptionManager?.activeSubscriptionInfoList ?: emptyList())
            .map(::toSim)
            .sortedBy { it.slotIndex }
        val encoded = current.joinToString("|") { it.encode() }
        val previousEncoded = Prefs.simSnapshot(appContext)

        // The callback is also delivered immediately after registering; that establishes the
        // baseline rather than treating the currently installed SIM as a replacement.
        if (previousEncoded == null) {
            Prefs.setSimSnapshot(appContext, encoded)
            return
        }

        val previous = previousEncoded.split('|').mapNotNull(SimSnapshot::decode)
        Prefs.setSimSnapshot(appContext, encoded)
        if (current.isEmpty()) return // A card was removed; wait for any replacement.

        val previousBySlot = previous.associateBy { it.slotIndex }
        val insertedOrReplaced = current.filter { now ->
            previousBySlot[now.slotIndex]?.fingerprint != now.fingerprint
        }
        if (insertedOrReplaced.isEmpty()) return

        sendAlert(insertedOrReplaced)
    }

    private fun sendAlert(changedSims: List<SimSnapshot>) {
        val recipients = Prefs.trustedContactsList(appContext).map { it.phoneNumber }
            .filter { it.isNotBlank() }
        if (recipients.isEmpty() || !SmsSender.canSend(appContext)) return

        val simDescription = changedSims.joinToString(", ") { it.description }
        val prefix = "SMSGuard security alert: A new SIM card was inserted ($simDescription)."
        Prefs.incrementAlertCount(appContext)

        // A fresh location is preferable, but an alert is never withheld because GPS is off or
        // unavailable. LocationHelper falls back to the most recent fix after a short timeout.
        if (!LocationHelper.hasPermission(appContext)) {
            sendToAll(recipients, prefix)
            return
        }
        LocationHelper.requestFreshFix(appContext) { location ->
            val body = if (location == null) prefix else
                "$prefix\nLocation: ${LocationHelper.mapsLink(location.latitude, location.longitude)}"
            sendToAll(recipients, body)
        }
    }

    private fun sendToAll(recipients: List<String>, message: String) {
        recipients.forEach { SmsSender.send(appContext, it, message) }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun toSim(info: SubscriptionInfo): SimSnapshot {
        // ICCID is used only locally as a one-way fingerprint. Android may withhold it on newer
        // releases; the carrier and network code then remain a useful, less precise fallback.
        val carrier = info.carrierName?.toString()?.trim().orEmpty().ifBlank { "Unknown carrier" }
        val network = listOfNotNull(info.mccString?.takeIf { it.isNotBlank() }, info.mncString?.takeIf { it.isNotBlank() })
            .joinToString("/")
        val identity = info.iccId?.takeIf { it.isNotBlank() }
            ?: listOf(carrier, network, info.displayName?.toString().orEmpty()).joinToString("|")
        val label = if (network.isBlank()) carrier else "$carrier ($network)"
        return SimSnapshot(info.simSlotIndex, sha256(identity), label)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(Locale.US, it) }

    private data class SimSnapshot(
        val slotIndex: Int,
        val fingerprint: String,
        val description: String
    ) {
        fun encode(): String = listOf(slotIndex, fingerprint, description.replace("|", " ")).joinToString("~")

        companion object {
            fun decode(value: String): SimSnapshot? {
                val parts = value.split("~", limit = 3)
                return if (parts.size == 3) SimSnapshot(parts[0].toIntOrNull() ?: return null, parts[1], parts[2]) else null
            }
        }
    }
}
