package com.example.smsguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/** Helpers for checking location permission, last-known fix, and fresh single fixes. */
object LocationHelper {
    private const val FIX_TIMEOUT_MS = 8_000L

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
                manager.getProviders(false)
            else -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    fun requestFreshFix(context: Context, onResult: (Location?) -> Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.getProvider(it) }.getOrNull() != null }
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        var delivered = false
        val handler = Handler(Looper.getMainLooper())

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!delivered) {
                    delivered = true
                    manager.removeUpdates(this)
                    onResult(location)
                }
            }
        }

        val timeoutRunnable = Runnable {
            if (!delivered) {
                delivered = true
                manager.removeUpdates(listener)
                onResult(lastKnown(context))
            }
        }

        handler.postDelayed(timeoutRunnable, FIX_TIMEOUT_MS)
        if (providers.isEmpty()) {
            timeoutRunnable.run()
            return
        }
        for (provider in providers) {
            runCatching {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }

    fun mapsLink(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
}