
package com.example.objectdetection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// A simple data class to hold the discovered service info
data class DiscoveredService(val host: String, val port: Int) {
    fun getStreamUrl(): String {
        return "http://$host:$port/stream"
    }
}

// An enum to represent the state of the discovery
enum class DiscoveryStatus {
    SEARCHING,
    FOUND,
    NOT_FOUND,
    ERROR
}

class ServiceDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveryState = MutableStateFlow<Pair<DiscoveryStatus, DiscoveredService?>>(DiscoveryStatus.SEARCHING to null)
    val discoveryState = _discoveryState.asStateFlow()

    private val SERVICE_TYPE = "_http._tcp."
    private val SERVICE_NAME = "esp32-camera"

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscoveryActive = false

    fun startDiscovery() {
        if (isDiscoveryActive) return
        _discoveryState.value = DiscoveryStatus.SEARCHING to null
        isDiscoveryActive = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NSD", "Service found: ${service.serviceName}")
                // We've found a service, now we need to check if it's the one we want
                if (service.serviceType == SERVICE_TYPE && service.serviceName.contains(SERVICE_NAME)) {
                    nsdManager.resolveService(service, createResolveListener())
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NSD", "Service lost: $service")
                 if (service.serviceName.contains(SERVICE_NAME)) {
                    _discoveryState.value = DiscoveryStatus.NOT_FOUND to null
                 }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i("NSD", "Discovery stopped: $serviceType")
                isDiscoveryActive = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NSD", "Discovery start failed: Error code: $errorCode")
                _discoveryState.value = DiscoveryStatus.ERROR to null
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NSD", "Discovery stop failed: Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        if (isDiscoveryActive && discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener)
            discoveryListener = null
            isDiscoveryActive = false
        }
    }
    
    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSD", "Resolve failed: $errorCode")
                 _discoveryState.value = DiscoveryStatus.ERROR to null
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i("NSD", "Resolve Succeeded: $serviceInfo")
                val discoveredService = DiscoveredService(
                    host = serviceInfo.host.hostAddress ?: "",
                    port = serviceInfo.port
                )
                if (discoveredService.host.isNotEmpty()) {
                    _discoveryState.value = DiscoveryStatus.FOUND to discoveredService
                    // Important: Stop discovery once we've found and resolved our target service
                    // to save battery and network resources.
                    stopDiscovery()
                }
            }
        }
    }
}
