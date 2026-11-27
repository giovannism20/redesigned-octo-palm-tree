package com.giovannimartins.rreader.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The kind of connection the phone has. */
enum class Connection {
    NONE,

    /** Wi-Fi or another network that does not count against a data plan. */
    UNMETERED,

    /** Mobile data, or another network that may cost the listener. */
    METERED,
}

/** Tells which kind of connection the phone has now, so large downloads can ask before using mobile data. */
fun interface ConnectionCheck {
    fun current(): Connection
}

/** [ConnectionCheck] from the system's default network. */
class SystemConnectionCheck
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ConnectionCheck {
        override fun current(): Connection {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
            return when {
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> Connection.NONE
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> Connection.UNMETERED
                else -> Connection.METERED
            }
        }
    }
