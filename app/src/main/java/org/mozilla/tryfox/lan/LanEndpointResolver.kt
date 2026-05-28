package org.mozilla.tryfox.lan

import java.net.Inet4Address
import java.net.NetworkInterface

fun resolveLanIpv4Address(): String? =
    NetworkInterface.getNetworkInterfaces()
        ?.toList()
        .orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { address ->
            !address.isLoopbackAddress &&
                !address.isLinkLocalAddress &&
                address.isSiteLocalAddress
        }
        ?.hostAddress
