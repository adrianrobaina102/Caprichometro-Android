package com.example.caprichometro.infrastructure.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observa en tiempo real la conectividad de red del dispositivo para mostrar avisos offline
 * y disparar reintentos o resincronizaciones al recuperar conexión.
 */
class NetworkConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkIsOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkIsOnlineNow()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = hasInternet && isValidated
        }
    }

    init {
        register()
    }

    private fun register() {
        val cm = connectivityManager ?: return
        if (isRegistered) return
        runCatching {
            cm.registerDefaultNetworkCallback(networkCallback)
            isRegistered = true
        }
    }

    fun checkIsOnlineNow(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternet && hasValidated
    }

    fun refresh(): Boolean {
        val online = checkIsOnlineNow()
        _isOnline.value = online
        return online
    }

    fun unregister() {
        val cm = connectivityManager ?: return
        if (!isRegistered) return
        runCatching {
            cm.unregisterNetworkCallback(networkCallback)
        }
        isRegistered = false
    }
}
