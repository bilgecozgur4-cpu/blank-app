package com.metehan.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.Locale
import java.util.TimeZone

object DeviceContextCollector {
    fun collect(context: Context): JSONObject {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val power = context.getSystemService(PowerManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val network = when {
            caps == null -> "offline_or_unknown"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }

        return JSONObject()
            .put("captured_at", OffsetDateTime.now().toString())
            .put("battery_percent", batteryPct)
            .put("charging", charging)
            .put("power_save", power.isPowerSaveMode)
            .put("network", network)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
    }
}
