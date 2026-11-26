package com.example.myvpn

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class FreeServer(
    val host: String,
    val ip: String,
    val cc: String,
    val country: String,
    val score: Long,
    val ping: Int,
    val speedMbps: Double,
    val ovpn: String
)

/** Public server list of VPN Gate (University of Tsukuba): volunteer-run OpenVPN servers. */
object VpnGate {
    private const val API = "https://www.vpngate.net/api/iphone/"

    suspend fun fetch(): List<FreeServer> = withContext(Dispatchers.IO) {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        try {
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    // CSV columns: HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,
    // Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
    fun parse(text: String): List<FreeServer> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = line.split(",")
                if (f.size < 15) return@mapNotNull null
                try {
                    FreeServer(
                        host = f[0],
                        ip = f[1],
                        score = f[2].toLongOrNull() ?: 0,
                        ping = f[3].toIntOrNull() ?: 0,
                        speedMbps = (f[4].toLongOrNull() ?: 0) / 1_000_000.0,
                        country = f[5],
                        cc = f[6].uppercase(),
                        ovpn = String(Base64.decode(f[14], Base64.DEFAULT))
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .toList()
}
