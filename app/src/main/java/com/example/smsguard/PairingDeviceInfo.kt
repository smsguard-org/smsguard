package com.example.smsguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.util.Locale

/** Battery snapshot included in the pairing claim. */
data class PairingBattery(val level: Int, val charging: Boolean)

/** Network snapshot included in the pairing claim. */
data class PairingNetwork(val carrier: String, val type: String)

/** Optional location snapshot, only read when location permission has been granted. */
data class PairingLocation(val latitude: Double, val longitude: Double)

data class PairingStatus(
    val battery: PairingBattery,
    val network: PairingNetwork,
    val location: PairingLocation?
)

/**
 * Everything the dashboard needs to register this phone.
 *
 * [imei] is nullable on purpose: Android 10+ only exposes it to privileged/carrier apps, so the
 * caller must ask the user rather than invent a value. Optional identifiers are omitted from the
 * JSON when the platform withholds them.
 */
data class PairingDevice(
    val brand: String,
    val model: String,
    val name: String,
    val imei: String?,
    val platform: String = "android",
    val osVersion: String,
    val appVersion: String,
    val status: PairingStatus,
    val imei2: String? = null,
    val meid: String? = null,
    val manufacturer: String? = null,
    val deviceType: String? = null,
    val serialNumber: String? = null,
    val imsi: String? = null,
    val iccid: String? = null,
    val phoneNumber: String? = null
)

/** Collects the pairing payload from Android system services. */
object PairingDeviceInfo {
    private val IMEI_PATTERN = Regex("^\\d{14,17}$")

    /** IMEIs are 14–17 digits; anything else must be re-entered by the user. */
    fun isValidImei(value: String?): Boolean = value?.trim()?.matches(IMEI_PATTERN) == true

    fun collect(context: Context): PairingDevice {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val identity = readIdentity(telephony)

        return PairingDevice(
            brand = Build.MANUFACTURER.orEmpty().ifBlank { "Unknown" },
            model = Build.MODEL.orEmpty().ifBlank { "Unknown" },
            name = friendlyName(context),
            imei = identity.imeis.firstOrNull(),
            platform = "android",
            osVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() },
            appVersion = BuildConfig.VERSION_NAME,
            status = PairingStatus(
                battery = readBattery(context),
                network = PairingNetwork(
                    carrier = readCarrier(telephony),
                    type = readNetworkType(context)
                ),
                location = readLocation(context)
            ),
            imei2 = identity.imeis.getOrNull(1),
            meid = identity.meid,
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { null },
            deviceType = deviceType(context),
            serialNumber = readSerial(),
            imsi = identity.imsi,
            iccid = identity.iccid,
            phoneNumber = identity.phoneNumber
        )
    }

    private data class TelephonyIdentity(
        val imeis: List<String>,
        val meid: String?,
        val imsi: String?,
        val iccid: String?,
        val phoneNumber: String?
    )

    @SuppressLint("MissingPermission")
    private fun readIdentity(manager: TelephonyManager?): TelephonyIdentity {
        if (manager == null) return TelephonyIdentity(emptyList(), null, null, null, null)

        val slots = runCatching { manager.phoneCount }.getOrDefault(1).coerceAtLeast(1)
        val imeis = (0 until slots)
            .mapNotNull { slot -> readImei(manager, slot) }
            .distinct()
        val meid = (0 until slots)
            .firstNotNullOfOrNull { slot -> readMeid(manager, slot) }

        return TelephonyIdentity(
            imeis = imeis,
            meid = meid,
            imsi = sanitizeIdentifier(runCatching { manager.subscriberId }.getOrNull()),
            iccid = sanitizeIdentifier(runCatching { manager.simSerialNumber }.getOrNull()),
            phoneNumber = sanitizeIdentifier(runCatching { manager.line1Number }.getOrNull())
        )
    }

    @SuppressLint("MissingPermission")
    private fun readImei(manager: TelephonyManager, slot: Int): String? {
        val value = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.getImei(slot)
            } else {
                @Suppress("DEPRECATION")
                manager.getDeviceId(slot)
            }
        }.getOrNull()
        return value?.takeIf(::isValidImei)
    }

    @SuppressLint("MissingPermission")
    private fun readMeid(manager: TelephonyManager, slot: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return sanitizeIdentifier(runCatching { manager.getMeid(slot) }.getOrNull())
    }

    private fun sanitizeIdentifier(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("null", ignoreCase = true)) return null
        if (trimmed.equals("unknown", ignoreCase = true)) return null
        if (trimmed.all { it == '0' }) return null
        return trimmed
    }

    private fun friendlyName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val custom = runCatching {
                AndroidSettings.Global.getString(
                    context.contentResolver,
                    AndroidSettings.Global.DEVICE_NAME
                )
            }.getOrNull()
            if (!custom.isNullOrBlank()) return custom.trim()
        }

        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val prettyManufacturer = manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> "Android device"
            manufacturer.isEmpty() -> model
            model.isEmpty() || model.equals(manufacturer, ignoreCase = true) -> prettyManufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$prettyManufacturer $model"
        }
    }

    private fun deviceType(context: Context): String {
        val smallestWidth = context.resources.configuration.smallestScreenWidthDp
        return if (smallestWidth >= TABLET_MIN_WIDTH_DP) "tablet" else "phone"
    }

    @SuppressLint("MissingPermission")
    private fun readSerial(): String? {
        val serial = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        }.getOrNull()
        return sanitizeIdentifier(serial)
    }

    private fun readBattery(context: Context): PairingBattery {
        @Suppress("DEPRECATION")
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            level * 100 / scale
        } else {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        }
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        return PairingBattery(level = percent.coerceIn(0, 100), charging = charging)
    }

    private fun readCarrier(manager: TelephonyManager?): String = runCatching {
        manager?.let { telephony ->
            listOf(telephony.simOperatorName, telephony.networkOperatorName)
                .firstOrNull { !it.isNullOrBlank() }
        }?.trim().orEmpty()
    }.getOrDefault("").ifBlank { "Unknown" }

    private fun readNetworkType(context: Context): String {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivity?.activeNetwork?.let {
            connectivity.getNetworkCapabilities(it)
        }
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            else -> "wifi"
        }
    }

    private fun readLocation(context: Context): PairingLocation? {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val location = LocationHelper.lastKnown(context) ?: return null
        return PairingLocation(location.latitude, location.longitude)
    }

    private const val TABLET_MIN_WIDTH_DP = 600
}

/** Serializes a claim exactly in the shape expected by `POST /api/devices/pairings/:token/claim`. */
object PairingPayload {
    fun toJson(device: PairingDevice, imei: String): JSONObject = JSONObject().apply {
        put("brand", device.brand)
        put("model", device.model)
        put("name", device.name)
        put("imei", imei.trim())
        put("platform", device.platform)
        put("osVersion", device.osVersion)
        put("appVersion", device.appVersion)
        put("status", JSONObject().apply {
            put("battery", JSONObject().apply {
                put("level", device.status.battery.level)
                put("charging", device.status.battery.charging)
            })
            put("network", JSONObject().apply {
                put("carrier", device.status.network.carrier)
                put("type", device.status.network.type)
            })
            device.status.location?.let { location ->
                put("location", JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                })
            }
        })
        putIfPresent("imei2", device.imei2)
        putIfPresent("meid", device.meid)
        putIfPresent("manufacturer", device.manufacturer)
        putIfPresent("deviceType", device.deviceType)
        putIfPresent("serialNumber", device.serialNumber)
        putIfPresent("imsi", device.imsi)
        putIfPresent("iccid", device.iccid)
        putIfPresent("phoneNumber", device.phoneNumber)
    }

    private fun JSONObject.putIfPresent(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }
}
