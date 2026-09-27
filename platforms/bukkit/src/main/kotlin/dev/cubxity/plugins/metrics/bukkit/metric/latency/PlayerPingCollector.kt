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

import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.Histogram
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.common.metric.Metrics
import org.bukkit.Server

/**
 * Samples every online player's ping once per collection, and the outbound
 * backlog of each connection when [connectionPendingBytes] is set. One walk of
 * the player list gives every number.
 *
 * The walk runs on the collection thread, off every region thread, and that is
 * safe because of what the two reads are. Verified with javap against
 * shreddedpaper-1.21.11, paper-1.21.11 build 132 and canvas-1.21.11 build 794:
 *
 * - `CraftServer.getOnlinePlayers()` returns `playerView`, which is
 *   `Collections.unmodifiableList(Lists.transform(PlayerList.players, ...))`,
 *   and `PlayerList.players` is a `CopyOnWriteArrayList`. The iterator is a
 *   snapshot: a join or quit during the walk cannot throw or skip.
 * - `CraftPlayer.getPing()` is `getHandle()` (a field read of
 *   `CraftEntity.entity`), then `getfield ServerPlayer.connection`, then
 *   `ServerGamePacketListenerImpl.latency()`, which is `getfield latency` on a
 *   `volatile int`. No world, chunk or entity state, and no lock. The keepalive
 *   handler writes that field on the netty thread, so a region thread would be
 *   no safer a reader than this one.
 *
 * `getPing()` throws `UnsupportedOperationException` while `connection` is
 * null. A listed player has one (the list add follows the assignment in
 * `PlayerList.placeNewPlayer`), but the guard is one catch, so it stays.
 *
 * A player's ping is the round trip of the last keepalive, which the server
 * sends every 15 s (`LATENCY_CHECK_INTERVAL`) and answers on the netty thread.
 * A keepalive that waits behind a backlog of packets, or behind a long tick on
 * a server that flushes from the tick, measures that wait. That is why the
 * value rose from 2 ms to a 140 ms median at 500 players while TPS held 20.0:
 * it is a server side number that carries the player's experience.
 */
class PlayerPingCollector(
    private val server: Server,
    private val playerPing: Histogram,
    private val connectionPendingBytes: Histogram?
) : Collector {
    override fun collect(): List<Metric> {
        var maxPing = 0
        var maxPendingBytes = 0L

        for (player in server.onlinePlayers) {
            val ping = try {
                player.ping
            } catch (_: UnsupportedOperationException) {
                continue
            }
            playerPing += ping.toDouble()
            if (ping > maxPing) maxPing = ping

            if (connectionPendingBytes != null) {
                val bytes = ConnectionBacklog.pendingBytes(player)
                if (bytes < 0) continue
                connectionPendingBytes += bytes.toDouble()
                if (bytes > maxPendingBytes) maxPendingBytes = bytes
            }
        }

        return if (connectionPendingBytes == null) {
            listOf(GaugeMetric(Metrics.Latency.PlayerPingMsMax, value = maxPing))
        } else {
            listOf(
                GaugeMetric(Metrics.Latency.PlayerPingMsMax, value = maxPing),
                GaugeMetric(Metrics.Latency.ConnectionPendingBytesMax, value = maxPendingBytes)
            )
        }
    }
}
