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
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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

            val state = pendingByName.computeIfAbsent(username) { PlayerTraceState(tracer, tracingConfig) }
            state.startConnect(username, attrs)
            state.startLogin()
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    fun onChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        safe {
            val player = event.player
            val pending = pendingByName.remove(player.username)
            val state = pending ?: PlayerTraceState(tracer, tracingConfig).also {
                it.startConnect(player.username, mapOf("player.username" to player.username))
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
            state.startServerConnect(target)
        }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onServerConnected(event: ServerConnectedEvent) {
        safe {
            val player = event.player
            val state = states[player.uniqueId] ?: return@safe
            val target = event.server.serverInfo.name
            state.endServerConnect(target)

            if (tracingConfig.propagation) {
                bootstrap.server.scheduler
                    .buildTask(bootstrap, Runnable { safe { propagateContext(player, state) } })
                    .delay(100L, TimeUnit.MILLISECONDS)
                    .schedule()
            }
        }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onKickedFromServer(event: KickedFromServerEvent) {
        safe {
            val state = states[event.player.uniqueId] ?: return@safe
            val server = event.server.serverInfo.name
            val reason = event.serverKickReason
                .map { PlainTextComponentSerializer.plainText().serialize(it) }
                .orElse(null)
            state.recordKick(server, reason, event.kickedDuringServerConnect())
        }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onDisconnect(event: DisconnectEvent) {
        safe {
            val state = states.remove(event.player.uniqueId) ?: return@safe
            state.endAll(event.loginStatus.name.lowercase())
        }
    }

    private fun propagateContext(player: Player, state: PlayerTraceState) {
        safe {
            val ctx = state.traceContext ?: return@safe
            val headers = tracer.inject(ctx)
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
    /** Trace context from the anchor span. All child spans use this as parent. */
    var traceContext: SpanContext? = null
        private set

    private var activeLoginSpan: Span? = null
    private var activeServerConnectSpan: Span? = null
    private var activeDisconnectSpan: Span? = null

    private var uuid: UUID? = null
    private var username: String? = null
    private var connectTimeMs: Long = 0

    /**
     * Create an instant `player.connect` anchor span. This establishes the
     * trace ID that all subsequent child spans inherit. The span is ended
     * immediately so no long-lived span sits in memory.
     */
    @Synchronized
    fun startConnect(username: String, attributes: Map<String, String>) {
        if (traceContext != null) return
        this.username = username
        connectTimeMs = System.currentTimeMillis()
        val anchor = tracer.startSpan("player.connect", attributes = attributes)
        traceContext = anchor.context
        anchor.end()
    }

    @Synchronized
    fun bind(uuid: UUID) {
        this.uuid = uuid
    }

    @Synchronized
    fun startLogin() {
        if (!config.spans.login) return
        if (activeLoginSpan != null) return
        activeLoginSpan = tracer.startSpan(
            "player.login",
            parent = traceContext
        )
    }

    @Synchronized
    fun endLogin() {
        activeLoginSpan?.end()
        activeLoginSpan = null
    }

    @Synchronized
    fun startServerConnect(target: String) {
        if (!config.spans.serverConnect) return
        // End any previous server_connect span (defensive; should already be ended).
        activeServerConnectSpan?.end()
        activeServerConnectSpan = tracer.startSpan(
            "player.server_connect",
            parent = traceContext,
            attributes = mapOf("server.name" to target)
        )
    }

    @Synchronized
    fun endServerConnect(target: String) {
        activeServerConnectSpan?.setAttribute("server.name", target)
        activeServerConnectSpan?.end()
        activeServerConnectSpan = null
    }

    /**
     * Record a kick event. Marks the in-flight server_connect span as error
     * if the kick happened during connect, and starts the disconnect span
     * (since a kick initiates the disconnect sequence).
     */
    @Synchronized
    fun recordKick(server: String, reason: String?, duringConnect: Boolean) {
        if (duringConnect) {
            activeServerConnectSpan?.setAttribute("kick.server", server)
            if (reason != null) activeServerConnectSpan?.setAttribute("kick.reason", reason)
            activeServerConnectSpan?.setError(reason ?: "kicked")
        }

        // Start the disconnect span early; the kick is the beginning of disconnect.
        startDisconnect(
            reason = reason ?: "kicked",
            kickServer = server,
            kickReason = reason
        )
        activeDisconnectSpan?.setError(reason ?: "kicked")
    }

    /**
     * End all open spans. Called from [DisconnectEvent].
     */
    @Synchronized
    fun endAll(reason: String? = null) {
        val hadOpenLogin = activeLoginSpan != null
        val hadOpenServerConnect = activeServerConnectSpan != null

        if (hadOpenServerConnect) {
            activeServerConnectSpan?.setError(reason ?: "disconnected")
        }
        activeServerConnectSpan?.end()
        activeServerConnectSpan = null

        if (hadOpenLogin) {
            activeLoginSpan?.setError(reason ?: "disconnected")
        }
        activeLoginSpan?.end()
        activeLoginSpan = null

        // Create the disconnect span if not already started by a kick.
        if (activeDisconnectSpan == null) {
            startDisconnect(reason = reason)
        } else if (reason != null) {
            activeDisconnectSpan?.setAttribute("disconnect.reason", reason)
        }

        if (hadOpenLogin || hadOpenServerConnect) {
            activeDisconnectSpan?.setError(reason ?: "disconnected")
        }

        activeDisconnectSpan?.end()
        activeDisconnectSpan = null
        traceContext = null
    }

    private fun startDisconnect(
        reason: String?,
        kickServer: String? = null,
        kickReason: String? = null
    ) {
        if (!config.spans.disconnect) return
        if (activeDisconnectSpan != null) return

        val attrs = mutableMapOf<String, String>()
        username?.let { attrs["player.username"] = it }
        uuid?.let { attrs["player.uuid"] = it.toString() }
        if (reason != null) attrs["disconnect.reason"] = reason
        if (kickServer != null) attrs["kick.server"] = kickServer
        if (kickReason != null) attrs["kick.reason"] = kickReason
        if (connectTimeMs > 0) {
            attrs["session.duration_ms"] = (System.currentTimeMillis() - connectTimeMs).toString()
        }

        activeDisconnectSpan = tracer.startSpan(
            "player.disconnect",
            parent = traceContext,
            attributes = attrs
        )
    }
}
