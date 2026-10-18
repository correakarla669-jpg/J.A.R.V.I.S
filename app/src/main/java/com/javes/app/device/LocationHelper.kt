package com.javes.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Localização com permissão explícita. Não grava coordenadas em disco
 * nem envia a terceiros — só devolve texto para a resposta por voz.
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Obtém a última localização conhecida ou uma atualização rápida.
     * Timeout curto para não travar a UI de voz.
     */
    @SuppressLint("MissingPermission")
    fun describeLocation(): String {
        if (!hasPermission()) {
            return "Não tenho permissão de localização. Autorize em Configurações do Android."
        }
        if (!isLocationEnabled()) {
            return "A localização do aparelho está desligada. Ative o GPS ou a localização e tente de novo."
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            return "Permissão de localização negada pelo sistema."
        } catch (_: Exception) {
            null
        }

        val loc = last ?: requestQuickUpdate(lm)
        if (loc == null) {
            return "Não consegui obter a localização agora. Tente de novo em um local aberto."
        }

        val address = reverseGeocode(loc)
        return if (address != null) {
            "Você está próximo de $address."
        } else {
            val lat = String.format(Locale.US, "%.4f", loc.latitude)
            val lon = String.format(Locale.US, "%.4f", loc.longitude)
            "Coordenadas aproximadas: latitude $lat, longitude $lon."
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestQuickUpdate(lm: LocationManager): Location? {
        return try {
            val latch = CountDownLatch(1)
            var result: Location? = null
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    result = location
                    latch.countDown()
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> return null
            }
            lm.requestSingleUpdate(provider, listener, context.mainLooper)
            latch.await(4, TimeUnit.SECONDS)
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun reverseGeocode(loc: Location): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            val list = if (Build.VERSION.SDK_INT >= 33) {
                // API 33+ é assíncrona; usamos a forma legada com cuidado
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            }
            val a = list?.firstOrNull() ?: return null
            listOfNotNull(a.thoroughfare, a.subLocality, a.subAdminArea, a.locality, a.adminArea)
                .distinct()
                .take(3)
                .joinToString(", ")
                .ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
