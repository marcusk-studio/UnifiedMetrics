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

    private val activeSessionSpans: MutableMap<UUID, Span> = ConcurrentHashMap()
    private val activeWorldReadySpans: MutableMap<UUID, Span> = ConcurrentHashMap()

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
        activeWorldReadySpans.values.forEach { safe { it.end() } }
        activeWorldReadySpans.clear()
        activeSessionSpans.values.forEach { safe { it.end() } }
        activeSessionSpans.clear()
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != TracingChannels.CHANNEL) return
        safe {
            val headers = TracingChannels.decode(message) ?: return@safe
            val parent = tracer.extract(headers) ?: return@safe

            // End any prior spans for this player (e.g. on backend switch via reconnect).
            activeWorldReadySpans.remove(player.uniqueId)?.end()
            activeSessionSpans.remove(player.uniqueId)?.end()

            val attrs = mapOf(
                "player.username" to player.name,
                "player.uuid" to player.uniqueId.toString(),
                "server.name" to plugin.apiProvider.serverName
            )

            val session = tracer.startSpan(
                "player.backend.session",
                parent = parent,
                attributes = attrs
            )
            activeSessionSpans[player.uniqueId] = session

            if (tracingConfig.spans.worldReady) {
                val worldReady = tracer.startSpan(
                    "player.world_ready",
                    parent = parent,
                    attributes = attrs
                )
                activeWorldReadySpans[player.uniqueId] = worldReady

                // Timeout: if the player never moves within 30 seconds, end the
                // span so it does not leak. This covers AFK joins or unusual
                // client states.
                server.scheduler.runTaskLater(bootstrap, Runnable {
                    safe {
                        activeWorldReadySpans.remove(player.uniqueId)?.let { span ->
                            span.setAttribute("world_ready.timeout", true)
                            span.end()
                        }
                    }
                }, 600L) // 30 seconds = 600 ticks
            }
        }
    }

    /**
     * End the `player.world_ready` span on the player's first movement.
     * Any [PlayerMoveEvent] (including head rotation) indicates the client
     * has received enough world data to interact.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val span = activeWorldReadySpans.remove(event.player.uniqueId) ?: return
        safe { span.end() }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        safe {
            activeWorldReadySpans.remove(event.player.uniqueId)?.end()
            activeSessionSpans.remove(event.player.uniqueId)?.end()
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
