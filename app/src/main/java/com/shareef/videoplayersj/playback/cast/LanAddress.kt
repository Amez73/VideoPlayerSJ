package com.shareef.videoplayersj.playback.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress

object LanAddress {
    /** Best-effort IPv4 address of the network currently carrying traffic, for building http://<ip>:<port>/... URLs. */
    fun currentIPv4(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProperties = cm.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            .map(LinkAddress::getAddress)
            .firstOrNull { it.hostAddress?.contains(':') == false && !it.isLoopbackAddress }
            ?.hostAddress
    }
}
