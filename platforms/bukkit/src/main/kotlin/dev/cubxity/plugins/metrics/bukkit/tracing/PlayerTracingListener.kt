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

package dev.cubxity.plugins.metrics.bukkit.tracing

import dev.cubxity.plugins.metrics.api.tracing.Span
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingChannels
import dev.cubxity.plugins.metrics.bukkit.UnifiedMetricsBukkitPlugin
import dev.cubxity.plugins.metrics.common.config.UnifiedMetricsTracingConfig
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerTracingListener(
    private val plugin: UnifiedMetricsBukkitPlugin
) : Listener, PluginMessageListener {
    private val bootstrap get() = plugin.bootstrap
    private val server get() = bootstrap.server
    private val tracingConfig: UnifiedMetricsTracingConfig get() = plugin.config.tracing

    /**
     * Tracks the world-ready span alongside the nanoTime at which it was
     * created. The timestamp lets [onMove] skip server-initiated movements
     * (spawn teleport, respawn, etc.) that fire within the first 150 ms of
     * the join sequence.
     */
    private data class WorldReadyState(val span: Span, val startNanos: Long)

    private val activeWorldReadySpans: MutableMap<UUID, WorldReadyState> = ConcurrentHashMap()

    private val tracer: Tracer
        get() = plugin.apiProvider.tracingManager.tracer

    fun register() {
        val messenger = server.messenger
        messenger.registerIncomingPluginChannel(bootstrap, TracingChannels.CHANNEL, this)
        server.pluginManager.registerEvents(this, bootstrap)
    }

    fun dispose() {
        try {
            server.messenger.unregisterIncomingPluginChannel(bootstrap, TracingChannels.CHANNEL, this)
        } catch (_: Throwable) {
        }
        HandlerList.unregisterAll(this)
        activeWorldReadySpans.values.forEach { safe { it.span.end() } }
        activeWorldReadySpans.clear()
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != TracingChannels.CHANNEL) return
        safe {
            val headers = TracingChannels.decode(message) ?: return@safe
            val parent = tracer.extract(headers) ?: return@safe

            // End any prior world_ready span for this player (e.g. on backend switch).
            activeWorldReadySpans.remove(player.uniqueId)?.span?.end()

            if (tracingConfig.spans.worldReady) {
                val attrs = mapOf(
                    "player.username" to player.name,
                    "player.uuid" to player.uniqueId.toString(),
                    "server.name" to plugin.apiProvider.serverName
                )

                val worldReady = tracer.startSpan(
                    "player.world_ready",
                    parent = parent,
                    attributes = attrs
                )
                activeWorldReadySpans[player.uniqueId] =
                    WorldReadyState(worldReady, System.nanoTime())

                // Timeout: if the player never moves within 30 seconds, end the
                // span so it does not leak. This covers AFK joins or unusual
                // client states.
                server.scheduler.runTaskLater(bootstrap, Runnable {
                    safe {
                        activeWorldReadySpans.remove(player.uniqueId)?.let { state ->
                            state.span.setAttribute("world_ready.timeout", true)
                            state.span.end()
                        }
                    }
                }, 600L) // 30 seconds = 600 ticks
            }
        }
    }

    /**
     * End the `player.world_ready` span on the player's first genuine
     * movement. Two filters prevent false positives:
     *
     * 1. **Position check** -- head-only rotation (same x/y/z, different
     *    yaw/pitch) is ignored; the player must change position.
     * 2. **Grace period** -- movements within the first 150 ms after the
     *    trace context arrives are skipped. This covers the spawn teleport
     *    and any other server-initiated repositioning during the join
     *    sequence.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val state = activeWorldReadySpans[event.player.uniqueId] ?: return
        val from = event.from
        val to = event.to ?: return
        // Skip head-only rotation.
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        // Skip server-initiated movements during the join sequence.
        if (System.nanoTime() - state.startNanos < 150_000_000L) return
        activeWorldReadySpans.remove(event.player.uniqueId) ?: return
        safe { state.span.end() }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        safe {
            activeWorldReadySpans.remove(event.player.uniqueId)?.span?.end()
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            bootstrap.logger.warn("UnifiedMetrics tracing error", error)
        }
    }
}
