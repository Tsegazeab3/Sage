package com.example.objectdetection

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Gets the first available non-loopback IPv4 address from the device's network interfaces.
     * This is more reliable for finding the local network IP, whether on Wi-Fi or a hotspot.
     */
    fun getLocalIpAddress(): String? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                // Check if the interface is up and not a loopback or virtual interface
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        // Find the first non-loopback IPv4 address
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            // Ignore exceptions during IP address retrieval
        }
        return null
    }
}