package com.giovannimartins.rreader.catalog

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.giovannimartins.rreader.Diagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Whether the phone has a connection to the internet, so the catalogue can say when it is offline. */
interface NetworkStatus {
    /** True while the phone's default network offers internet access; emits each change. */
    val online: Flow<Boolean>
}

/** [NetworkStatus] from the system's default network, watched only while someone collects [online]. */
class ConnectivityNetworkStatus
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkStatus {
        override val online: Flow<Boolean> =
            callbackFlow {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                trySend(connectivity.hasInternet())
                val watching =
                    try {
                        connectivity.registerDefaultNetworkCallback(callback)
                        true
                    } catch (e: SecurityException) {
                        // Without the permission the first answer stays; the catalogue works the same.
                        Diagnostics.recordHandled("watch the network", e)
                        false
                    }
                awaitClose { if (watching) connectivity.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private fun ConnectivityManager.hasInternet(): Boolean =
            activeNetwork?.let(::getNetworkCapabilities)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ==
                true
    }
