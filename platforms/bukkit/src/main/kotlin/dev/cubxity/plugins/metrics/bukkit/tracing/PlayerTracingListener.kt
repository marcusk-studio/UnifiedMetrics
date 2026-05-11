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
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerTracingListener(
    private val plugin: UnifiedMetricsBukkitPlugin
) : Listener, PluginMessageListener {
    private val bootstrap get() = plugin.bootstrap
    private val server get() = bootstrap.server

    private val activeSpans: MutableMap<UUID, Span> = ConcurrentHashMap()

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
        activeSpans.values.forEach { safe { it.end() } }
        activeSpans.clear()
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != TracingChannels.CHANNEL) return
        safe {
            val headers = TracingChannels.decode(message) ?: return@safe
            val parent = tracer.extract(headers) ?: return@safe

            // End any prior span for this player (e.g. on backend switch via reconnect).
            activeSpans.remove(player.uniqueId)?.end()

            val span = tracer.startSpan(
                "player.backend.session",
                parent = parent,
                attributes = mapOf(
                    "player.username" to player.name,
                    "player.uuid" to player.uniqueId.toString(),
                    "server.name" to plugin.apiProvider.serverName
                )
            )
            activeSpans[player.uniqueId] = span
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        safe {
            activeSpans.remove(event.player.uniqueId)?.end()
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
