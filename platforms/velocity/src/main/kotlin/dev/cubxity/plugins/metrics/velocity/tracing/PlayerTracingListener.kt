/*
 *     This file is part of UnifiedMetrics.
 *
 *     UnifiedMetrics is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     UnifiedMetrics is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with UnifiedMetrics.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.cubxity.plugins.metrics.velocity.tracing

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import dev.cubxity.plugins.metrics.api.tracing.Span
import dev.cubxity.plugins.metrics.api.tracing.SpanContext
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingChannels
import dev.cubxity.plugins.metrics.common.config.UnifiedMetricsTracingConfig
import dev.cubxity.plugins.metrics.velocity.UnifiedMetricsVelocityPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PlayerTracingListener(
    private val plugin: UnifiedMetricsVelocityPlugin
) {
    private val bootstrap get() = plugin.bootstrap
    private val tracingConfig: UnifiedMetricsTracingConfig get() = plugin.config.tracing

    private val states: MutableMap<UUID, PlayerTraceState> = ConcurrentHashMap()
    private val channel: MinecraftChannelIdentifier =
        MinecraftChannelIdentifier.from(TracingChannels.CHANNEL)

    private val tracer: Tracer
        get() = plugin.apiProvider.tracingManager.tracer

    fun register() {
        bootstrap.server.channelRegistrar.register(channel)
        bootstrap.server.eventManager.register(bootstrap, this)
    }

    fun dispose() {
        bootstrap.server.eventManager.unregisterListener(bootstrap, this)
        bootstrap.server.channelRegistrar.unregister(channel)
        // End any spans that are still open.
        states.values.forEach { state ->
            safe { state.endAll() }
        }
        states.clear()
        pendingByName.values.forEach { state ->
            safe { state.endAll() }
        }
        pendingByName.clear()
    }

    @Subscribe(order = PostOrder.FIRST)
    fun onPreLogin(event: PreLoginEvent) {
        if (!event.result.isAllowed) return
        safe {
            val username = event.username
            val attrs = mutableMapOf(
                "player.username" to username,
                "proxy.event" to "pre_login"
            )
            if (tracingConfig.attributes.includeRemoteAddr) {
                attrs["player.remote_addr"] = event.connection.remoteAddress.address.hostAddress
            }
            event.connection.virtualHost.ifPresent {
                attrs["player.virtual_host"] = "${it.hostString}:${it.port}"
            }

            // We don't have the UUID until login completes; use username keying for now,
            // and migrate to UUID in the player-server choice / connect events.
            val state = pendingByName.computeIfAbsent(username) { PlayerTraceState(tracer, tracingConfig) }
            state.startConnection(username, attrs)
            state.startLogin()
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    fun onChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        // PreLoginEvent fires before the Player object exists; at this point we can rebind
        // the pending state to the player UUID.
        safe {
            val player = event.player
            val pending = pendingByName.remove(player.username)
            val state = pending ?: PlayerTraceState(tracer, tracingConfig).also {
                it.startConnection(player.username, mapOf("player.username" to player.username))
            }
            state.bind(player.uniqueId)
            states[player.uniqueId] = state
            state.endLogin()
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    fun onServerPreConnect(event: ServerPreConnectEvent) {
        if (!event.result.isAllowed) return
        safe {
            val state = states[event.player.uniqueId] ?: return@safe
            val target = event.result.server.orElse(event.originalServer).serverInfo.name
            state.endServerSession()
            state.startBackendConnect(target)
        }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onServerConnected(event: ServerConnectedEvent) {
        safe {
            val player = event.player
            val state = states[player.uniqueId] ?: return@safe
            val target = event.server.serverInfo.name
            state.endBackendConnect(target)
            state.startServerSession(target)

            if (tracingConfig.propagation) {
                // Velocity fires ServerConnectedEvent before committing the new
                // ServerConnection to player.currentServer, so defer until it's populated.
                bootstrap.server.scheduler
                    .buildTask(bootstrap, Runnable { safe { propagateContext(player, state) } })
                    .delay(100L, TimeUnit.MILLISECONDS)
                    .schedule()
            }
        }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onDisconnect(event: DisconnectEvent) {
        safe {
            val state = states.remove(event.player.uniqueId) ?: return@safe
            state.endAll()
        }
    }

    private fun propagateContext(player: Player, state: PlayerTraceState) {
        safe {
            val span = state.activeServerSessionSpan ?: state.activeConnectionSpan ?: return@safe
            val headers = tracer.inject(span.context)
            val payload = TracingChannels.encode(headers) ?: return@safe
            val server = player.currentServer.orElse(null) ?: return@safe
            server.sendPluginMessage(channel, payload)
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            bootstrap.logger.warn("UnifiedMetrics tracing error", error)
        }
    }

    /**
     * Pending-login states keyed by username (UUID isn't available until after PreLogin).
     */
    private val pendingByName: MutableMap<String, PlayerTraceState> = ConcurrentHashMap()
}

internal class PlayerTraceState(
    private val tracer: Tracer,
    private val config: UnifiedMetricsTracingConfig
) {
    var activeConnectionSpan: Span? = null
        private set
    private var activeLoginSpan: Span? = null
    private var activeBackendConnectSpan: Span? = null
    var activeServerSessionSpan: Span? = null
        private set

    private var uuid: UUID? = null

    @Synchronized
    fun startConnection(username: String, attributes: Map<String, String>) {
        if (activeConnectionSpan != null) return
        // Always create the connection span so child spans (login, backend_connect,
        // server_session) share the same trace ID via parent context, even when
        // session tracking is disabled.
        activeConnectionSpan = tracer.startSpan("player.connection", attributes = attributes)
    }

    @Synchronized
    fun bind(uuid: UUID) {
        this.uuid = uuid
        activeConnectionSpan?.setAttribute("player.uuid", uuid.toString())
    }

    @Synchronized
    fun startLogin() {
        if (!config.spans.login) return
        if (activeLoginSpan != null) return
        activeLoginSpan = tracer.startSpan(
            "player.login",
            parent = activeConnectionSpan?.context
        )
    }

    @Synchronized
    fun endLogin() {
        activeLoginSpan?.end()
        activeLoginSpan = null
    }

    @Synchronized
    fun startBackendConnect(target: String) {
        if (!config.spans.backendConnect) return
        if (activeBackendConnectSpan != null) {
            activeBackendConnectSpan?.end()
        }
        activeBackendConnectSpan = tracer.startSpan(
            "player.backend_connect",
            parent = activeConnectionSpan?.context,
            attributes = mapOf("target.server" to target)
        )
    }

    @Synchronized
    fun endBackendConnect(target: String) {
        activeBackendConnectSpan?.setAttribute("target.server", target)
        activeBackendConnectSpan?.end()
        activeBackendConnectSpan = null
    }

    @Synchronized
    fun startServerSession(target: String) {
        if (!config.spans.serverSession) return
        activeServerSessionSpan = tracer.startSpan(
            "player.server_session",
            parent = activeConnectionSpan?.context,
            attributes = mapOf("server.name" to target)
        )
    }

    @Synchronized
    fun endServerSession() {
        activeServerSessionSpan?.end()
        activeServerSessionSpan = null
    }

    @Synchronized
    fun endAll() {
        activeBackendConnectSpan?.setError("disconnected")
        activeBackendConnectSpan?.end()
        activeBackendConnectSpan = null
        activeServerSessionSpan?.end()
        activeServerSessionSpan = null
        activeLoginSpan?.setError("disconnected")
        activeLoginSpan?.end()
        activeLoginSpan = null
        activeConnectionSpan?.end()
        activeConnectionSpan = null
    }
}
