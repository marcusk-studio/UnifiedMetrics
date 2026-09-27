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

package dev.cubxity.plugins.metrics.bukkit.metric.latency

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

/**
 * Feeds the tick ring from Paper's [ServerTickEndEvent]. Its own class, so that
 * a server without the event (Spigot) never loads the event type: Bukkit
 * reflects over a listener's methods when it registers one, and [LatencyCollection]
 * makes this listener on Paper only.
 *
 * The event carries the wall time of the tick that just ended. On ShreddedPaper
 * `MinecraftServer.tickServer` constructs it with
 * `(System.nanoTime() - currentTickStart) / 1e6` (javap, shreddedpaper-1.21.11),
 * on the server thread, after it has joined the futures of every region tick
 * for that tick. A 333 ms region tick therefore shows here as a 333 ms tick,
 * which is the value the average TPS hid.
 *
 * One thread fires the event, so the ring's single writer rule holds.
 */
class TickDurationListener(
    private val ring: TickDurationRing,
    private val bootstrap: UnifiedMetricsBukkitBootstrap
) : Listener {
    fun initialize() {
        bootstrap.server.pluginManager.registerEvents(this, bootstrap)
    }

    fun dispose() {
        HandlerList.unregisterAll(this)
    }

    /**
     * One array store and one release store; see [TickDurationRing.record].
     * `getTickDuration` is a field read of a `double` already in milliseconds.
     */
    @EventHandler
    fun onTickEnd(event: ServerTickEndEvent) {
        ring.record(event.tickDuration)
    }
}
