package com.example.myvpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream

/** Drives the real WireGuard engine (wireguard-go via GoBackend). */
object VpnManager {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    data class Status(
        val state: State = State.DISCONNECTED,
        val serverName: String = "",
        val rx: Long = 0,
        val tx: Long = 0,
        val since: Long = 0,
        val error: String? = null
    )

    private const val TUNNEL_NAME = "myvpn"

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {}
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var backend: GoBackend? = null
    private var statsJob: Job? = null

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext).also { backend = it }

    /** If the tunnel is still up from a previous app session, reflect that in the UI. */
    fun sync(ctx: Context) {
        scope.launch {
            lock.withLock {
                try {
                    val b = backend(ctx)
                    if (b.runningTunnelNames.contains(TUNNEL_NAME) &&
                        _status.value.state == State.DISCONNECTED
                    ) {
                        _status.value = Status(State.CONNECTED, "Active tunnel", since = System.currentTimeMillis())
                        startStats(b)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun connect(ctx: Context, server: Server) {
        scope.launch {
            lock.withLock {
                statsJob?.cancel()
                _status.value = Status(State.CONNECTING, server.name)
                try {
                    val b = backend(ctx)
                    val cfg = Config.parse(ByteArrayInputStream(server.config.toByteArray()))
                    b.setState(tunnel, Tunnel.State.UP, cfg)
                    _status.value = Status(State.CONNECTED, server.name, since = System.currentTimeMillis())
                    startStats(b)
                } catch (e: Exception) {
                    _status.value = Status(State.ERROR, server.name, error = e.localizedMessage ?: e.toString())
                }
            }
        }
    }

    fun disconnect(ctx: Context) {
        scope.launch {
            lock.withLock {
                statsJob?.cancel()
                try {
                    backend(ctx).setState(tunnel, Tunnel.State.DOWN, null)
                    _status.value = Status()
                } catch (e: Exception) {
                    _status.value = Status(State.ERROR, error = e.localizedMessage ?: e.toString())
                }
            }
        }
    }

    private fun startStats(b: GoBackend) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                try {
                    val s = b.getStatistics(tunnel)
                    _status.update { it.copy(rx = s.totalRx(), tx = s.totalTx()) }
                } catch (_: Exception) {
                }
                delay(1000)
            }
        }
    }
}
