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

package dev.cubxity.plugins.metrics.velocity.metric.tracing

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import dev.cubxity.plugins.metrics.tracing.PlayerTracingService
import dev.cubxity.plugins.metrics.velocity.bootstrap.UnifiedMetricsVelocityBootstrap
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context

/**
 * Instruments Velocity proxy events with OpenTelemetry tracing.
 *
 * Trace topology:
 * ```
 *  [player.connect] (SERVER span, proxy side)
 *      └─ [player.backend_connect] (CLIENT span, proxy side)
 *             │  ← traceparent forwarded via plugin channel →
 *             └─ [player.server_session] (SERVER span, backend side)
 * ```
 *
 * The `traceparent` payload is forwarded to the backend via the Minecraft plugin-messaging
 * channel `unifiedmetrics:tracing` immediately after the player has been connected to the
 * backend.  The backend plugin ([BukkitTracingCollection]) receives it and creates the
 * child span there.
 *
 * This class is registered/unregistered by [UnifiedMetricsVelocityPlugin].
 */
@Suppress("UNUSED_PARAMETER")
class VelocityTracingCollection(private val bootstrap: UnifiedMetricsVelocityBootstrap) {

    companion object {
        /** Plugin-messaging channel used to forward trace context to backend servers. */
        val CHANNEL: MinecraftChannelIdentifier = MinecraftChannelIdentifier.create("unifiedmetrics", "tracing")
    }

    fun initialize() {
        bootstrap.server.channelRegistrar.register(bootstrap, CHANNEL)
        bootstrap.server.eventManager.register(bootstrap, this)
    }

    fun dispose() {
        bootstrap.server.eventManager.unregisterListener(bootstrap, this)
        bootstrap.server.channelRegistrar.unregister(CHANNEL)
    }

    // ── Event handlers ───────────────────────────────────────────────────────

    /**
     * Creates a parent "player.connect" span when the player has authenticated.
     * The span remains open until [onDisconnect].
     */
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val tracer = PlayerTracingService.tracer ?: return
        val player = event.player

        val span = tracer.spanBuilder("player.connect")
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("player.uuid", player.uniqueId.toString())
            .setAttribute("player.username", player.username)
            .setAttribute("player.address", player.remoteAddress.address.hostAddress)
            .startSpan()

        PlayerTracingService.sessionSpans[player.uniqueId] = span
    }

    /**
     * Creates a child "player.backend_connect" span and forwards the W3C `traceparent`
     * to the backend server via the plugin-messaging channel so the backend can create a
     * linked child span there.
     */
    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val tracer = PlayerTracingService.tracer ?: return
        val player = event.player
        val sessionSpan = PlayerTracingService.sessionSpans[player.uniqueId] ?: return

        val parentContext = Context.current().with(sessionSpan)
        val backendSpan = tracer.spanBuilder("player.backend_connect")
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("server.name", event.server.serverInfo.name)
            .setAttribute("player.uuid", player.uniqueId.toString())
            .setAttribute("player.username", player.username)
            .startSpan()

        try {
            // Build W3C traceparent: "00-{traceId}-{spanId}-{flags}"
            val sc = backendSpan.spanContext
            val traceparent = "00-${sc.traceId}-${sc.spanId}-${sc.traceFlags.asHex()}"
            val payload = traceparent.toByteArray(Charsets.UTF_8)

            // Send to the backend — player.currentServer is non-null here as the player
            // has just connected, but we guard with ifPresent for safety.
            player.currentServer.ifPresent { conn ->
                conn.sendPluginMessage(CHANNEL, payload)
            }

            backendSpan.setStatus(StatusCode.OK)
        } catch (e: Exception) {
            backendSpan.setStatus(StatusCode.ERROR, e.message ?: "unknown error")
        } finally {
            backendSpan.end()
        }
    }

    /**
     * Ends the player's session span when they disconnect from the proxy.
     */
    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        PlayerTracingService.sessionSpans.remove(event.player.uniqueId)?.end()
    }
}
