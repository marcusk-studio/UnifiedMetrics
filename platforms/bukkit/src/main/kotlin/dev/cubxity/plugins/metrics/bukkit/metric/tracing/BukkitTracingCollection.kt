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

package dev.cubxity.plugins.metrics.bukkit.metric.tracing

import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap
import dev.cubxity.plugins.metrics.tracing.PlayerTracingService
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Instruments backend (Bukkit/Paper/MultiPaper/ShreddedPaper/Leaf/Fabric) server events
 * with OpenTelemetry tracing.
 *
 * When the Velocity proxy routes a player to this server it sends a W3C `traceparent`
 * payload over the `unifiedmetrics:tracing` plugin-messaging channel. This class receives
 * that payload, reconstructs the remote span context, and starts a child span named
 * `player.server_session` that lasts until the player leaves the server.
 *
 * Trace topology (from the backend's perspective):
 * ```
 *  [player.backend_connect] (CLIENT span on proxy)  ← parent
 *      └─ [player.server_session] (SERVER span on this backend)
 * ```
 */
class BukkitTracingCollection(
    private val bootstrap: UnifiedMetricsBukkitBootstrap
) : Listener, PluginMessageListener {

    companion object {
        const val CHANNEL = "unifiedmetrics:tracing"
    }

    /** Active server-session spans keyed by player UUID. */
    private val serverSessionSpans: ConcurrentHashMap<UUID, io.opentelemetry.api.trace.Span> =
        ConcurrentHashMap()

    fun initialize() {
        bootstrap.server.messenger.registerIncomingPluginChannel(bootstrap, CHANNEL, this)
        bootstrap.server.pluginManager.registerEvents(this, bootstrap)
    }

    fun dispose() {
        bootstrap.server.messenger.unregisterIncomingPluginChannel(bootstrap, CHANNEL, this)
        HandlerList.unregisterAll(this)
        // End any spans that were not closed by a PlayerQuitEvent.
        serverSessionSpans.values.forEach { it.end() }
        serverSessionSpans.clear()
    }

    // ── PluginMessageListener ────────────────────────────────────────────────

    /**
     * Receives the `traceparent` forwarded by the proxy, creates a child span, and stores
     * it under the player's UUID until [onQuit] fires.
     */
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        val tracer = PlayerTracingService.tracer ?: return

        val traceparent = message.toString(Charsets.UTF_8)
        val spanContext = parseTraceparent(traceparent) ?: return

        val remoteContext = Context.root().with(io.opentelemetry.api.trace.Span.wrap(spanContext))
        val span = tracer.spanBuilder("player.server_session")
            .setParent(remoteContext)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("player.uuid", player.uniqueId.toString())
            .setAttribute("player.username", player.name)
            .setAttribute("server.name", bootstrap.server.name)
            .startSpan()

        serverSessionSpans[player.uniqueId] = span
    }

    // ── Bukkit event handlers ────────────────────────────────────────────────

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        serverSessionSpans.remove(event.player.uniqueId)?.end()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Parses a W3C `traceparent` header value into an OTel [SpanContext].
     *
     * Format: `00-{32 hex traceId}-{16 hex spanId}-{2 hex flags}`
     */
    private fun parseTraceparent(traceparent: String): SpanContext? {
        val parts = traceparent.split("-")
        if (parts.size < 4) return null
        val version = parts[0]
        if (version != "00") return null
        val traceId = parts[1]
        val spanId = parts[2]
        val flags = parts[3]
        if (traceId.length != 32 || spanId.length != 16 || flags.length != 2) return null

        return try {
            SpanContext.createFromRemoteParent(
                traceId,
                spanId,
                TraceFlags.fromHex(flags, 0),
                TraceState.getDefault()
            )
        } catch (e: Exception) {
            null
        }
    }
}
