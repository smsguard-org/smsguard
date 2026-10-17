package com.example.smsguard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the multi-line system summary returned by the `#STATUS#PIN` command.
 *
 * Every section degrades gracefully: if a value is unavailable (permission missing, hardware
 * absent, service null) the line reports that instead of failing the whole reply. Location is the
 * last known fix so the command answers immediately; `#LOCATE#PIN` remains the way to force a
 * precise, fresh GPS reading.
 */
object DeviceStatus {

    fun summary(context: Context): String {
        val lines = mutableListOf<String>()
        lines += "SMSGuard Status"
        lines += "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}"
        lines += "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        lines += "Uptime: ${formatDuration(SystemClock.elapsedRealtime())}"
        lines += batteryLine(context)
        lines += networkLines(context)
        lines += locationLines(context)
        lines += storageLine()
        lines += memoryLine(context)
        return lines.joinToString("\n")
    }

    @Suppress("DEPRECATION")
    private fun batteryLine(context: Context): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (scale > 0) level * 100 / scale else -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val voltage = (battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
        val health = battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging${plugLabel(plugged)}"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        return String.format(
            Locale.US,
            "Battery: %d%% %s, %.1f\u00B0C, %.2fV, %s",
            percent, state, temperature, voltage, healthLabel(health)
        )
    }

    private fun plugLabel(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> " (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (wireless)"
        else -> ""
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Health good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fault detected"
        else -> "Health unknown"
    }

    @Suppress("DEPRECATION")
    private fun networkLines(context: Context): List<String> {
        val lines = mutableListOf<String>()
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val activeCaps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val online = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            activeCaps == null -> "None"
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        lines += if (online) "Network: Connected via $transport" else "Network: Disconnected"

        val mobilePresent = transportPresent(connectivity, NetworkCapabilities.TRANSPORT_CELLULAR)
        val mobileOnline = transportOnline(connectivity, NetworkCapabilities.TRANSPORT_CELLULAR)
        lines += "Mobile data: ${when {
            mobileOnline -> "Connected"
            mobilePresent -> "On (no internet)"
            else -> "Off"
        }}"

        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrier = telephony?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: telephony?.simOperatorName?.takeIf { it.isNotBlank() }
        lines += "Carrier: ${carrier ?: "Unknown"}"

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi?.isWifiEnabled == true) {
            val info = wifi.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            val ip = info?.ipAddress?.takeIf { it != 0 }?.let(::formatIpv4)
            val detail = listOfNotNull(
                ssid?.let { "SSID: $it" },
                ip?.let { "IP: $it" }
            ).joinToString(", ")
            lines += if (detail.isEmpty()) "Wi-Fi: On" else "Wi-Fi: On ($detail)"
        } else {
            lines += "Wi-Fi: Off"
        }
        return lines
    }

    private fun transportPresent(connectivity: ConnectivityManager?, transport: Int): Boolean =
        connectivity?.allNetworks?.any { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(transport) == true
        } == true

    private fun transportOnline(connectivity: ConnectivityManager?, transport: Int): Boolean =
        connectivity?.allNetworks?.any { network ->
            val caps = connectivity.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(transport) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true

    private fun locationLines(context: Context): List<String> {
        val location = LocationHelper.lastKnown(context) ?: return listOf("Location: unavailable")
        return listOf(
            String.format(Locale.US, "Location: %.5f, %.5f", location.latitude, location.longitude),
            "Maps: ${LocationHelper.mapsLink(location.latitude, location.longitude)}"
        )
    }

    private fun storageLine(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            "Storage: ${gigabytes(free)} free / ${gigabytes(total)}"
        } catch (_: Exception) {
            "Storage: unavailable"
        }
    }

    private fun memoryLine(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "Memory: unavailable"
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return "Memory: ${gigabytes(info.availMem)} free / ${gigabytes(info.totalMem)}"
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun gigabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)

    private fun formatIpv4(ip: Int): String = String.format(
        Locale.US, "%d.%d.%d.%d",
        ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
    )
}
