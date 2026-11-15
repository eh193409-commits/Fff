package com.example.sara.control

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkInformationManager(private val context: Context) {

    fun getNetworkInfo(): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null

        if (caps == null) {
            return NetworkInfo(
                isInternetConnected = false,
                isWifiConnected = false,
                isCellularConnected = false,
                transportLabel = "Offline",
                isMetered = false,
                downstreamKbps = 0,
                upstreamKbps = 0,
                cellularGenerationHint = "None"
            )
        }

        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val transports = buildList {
            if (hasWifi) add("Wi-Fi")
            if (hasCellular) add("Mobile Data")
            if (hasEthernet) add("Ethernet")
            if (hasVpn) add("VPN")
        }
        val transportLabel = if (transports.isEmpty()) "Connected" else transports.joinToString(" + ")

        val cellularHint = when {
            hasCellular && caps.linkDownstreamBandwidthKbps >= 100_000 -> "5G / High-Speed LTE"
            hasCellular && caps.linkDownstreamBandwidthKbps >= 10_000 -> "4G LTE"
            hasCellular -> "Cellular"
            else -> "Inactive"
        }

        return NetworkInfo(
            isInternetConnected = isValidated || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isWifiConnected = hasWifi,
            isCellularConnected = hasCellular,
            transportLabel = transportLabel,
            isMetered = isMetered,
            downstreamKbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
            upstreamKbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
            cellularGenerationHint = cellularHint
        )
    }

    fun getSpokenNetworkReport(): ControlResult {
        val info = getNetworkInfo()
        val spoken = if (!info.isInternetConnected) {
            "Your phone is currently offline with no active internet connection."
        } else {
            val downMbps = info.downstreamKbps / 1000f
            val speedText = if (downMbps >= 1f) {
                " Estimated link capacity is ${String.format("%.1f", downMbps)} megabits per second."
            } else ""
            "You are connected via ${info.transportLabel} with active internet access.$speedText"
        }
        return ControlResult.Success(
            spokenResponse = spoken,
            detail = "${info.transportLabel} · Internet: ${if (info.isInternetConnected) "Connected" else "Offline"}"
        )
    }
}
