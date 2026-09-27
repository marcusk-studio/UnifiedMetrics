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

import dev.cubxity.plugins.metrics.api.metric.DistributionSink
import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.collector.Histogram
import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap
import dev.cubxity.plugins.metrics.bukkit.util.BukkitPlatform
import dev.cubxity.plugins.metrics.common.metric.Metrics

/** Milliseconds. A keepalive round trip; 5 s is the point at which a client gives up. */
private val pingBuckets = doubleArrayOf(
    5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0
)

/**
 * Milliseconds. Dense around the 50 ms budget, because that is where a tick
 * turns from fine to late, and wide above it so a 333 ms tick has a bucket of
 * its own instead of sharing "over 250 ms" with everything worse.
 */
private val tickBuckets = doubleArrayOf(
    5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 75.0, 100.0, 150.0, 200.0,
    300.0, 500.0, 1000.0, 2000.0, 5000.0
)

/** Bytes, powers of four from 1 KiB to 16 MiB. Netty's default high water mark is 64 KiB. */
private val pendingBytesBuckets = doubleArrayOf(
    1024.0, 4096.0, 16384.0, 65536.0, 262144.0, 1048576.0, 4194304.0, 16777216.0
)

/**
 * Ticks the ring holds between two drains: 3.4 minutes at 20 TPS. DogStatsD
 * drains every second and a Prometheus scrape every minute or so; anything
 * slower reads the dropped counter.
 */
private const val TICK_RING_CAPACITY = 4096

/**
 * Server side latency: the numbers that move when a player feels the server
 * lag, measured where the server can see them.
 *
 * A 500 player load test on ShreddedPaper held TPS at 20.0 for its whole run
 * while single ticks reached 333 ms and the server side keepalive ping went
 * from 2 ms at 100 players to a 140 ms median and a 639 ms max. Players saw
 * position updates arrive late. No existing metric moved, because each one was
 * an average over a second or more, and a 333 ms tick among nineteen 5 ms
 * ticks averages to 21 ms. Distributions and maxima are the fix:
 *
 * - `minecraft_player_ping_ms` (histogram) and `_max` (gauge): every online
 *   player's keepalive round trip, sampled per collection.
 * - `minecraft_tick_duration_ms` (histogram) and `_max` (gauge): every tick,
 *   taken from Paper's `ServerTickEndEvent` through a single writer ring, so
 *   the tick thread allocates nothing. Paper and its forks with a server
 *   thread (ShreddedPaper) only: Folia never fires the event and the
 *   `regionizedServer` collection covers it there; Spigot has no event that
 *   carries the duration.
 * - `minecraft_connection_pending_bytes` (histogram) and `_max` (gauge), off by
 *   default: the netty outbound backlog per connection, the server side
 *   precursor of a ping spike. See [ConnectionBacklog] for the reflection.
 *
 * Per region tick times do not exist on ShreddedPaper. `ShreddedPaperChunkTicker
 * .tickRegion` returns a `CompletableFuture` that the server thread joins, and
 * neither it nor `LevelChunkRegion` records a duration (javap: no `nanoTime`
 * call in either class, no timing field on the region). The global tick is the
 * join of all regions, so its max is the max region tick plus the serial work,
 * which is the number the player waits for anyway.
 *
 * The state collectors observe into their histograms, so each one runs before
 * its histogram reports. [collectors] keeps that order. Everything here runs on
 * the collection thread and reads only what [PlayerPingCollector] and
 * [ConnectionBacklog] prove safe off-thread, so the collection is async.
 */
class LatencyCollection(
    private val bootstrap: UnifiedMetricsBukkitBootstrap,
    distributionSink: DistributionSink? = null,
    connection: Boolean = false
) : CollectorCollection {
    private val playerPing = Histogram(
        Metrics.Latency.PlayerPingMs,
        upperBounds = pingBuckets,
        distributionSink = distributionSink
    )

    private val connectionPendingBytes: Histogram? = when {
        !connection -> null
        ConnectionBacklog.isAvailable -> Histogram(
            Metrics.Latency.ConnectionPendingBytes,
            upperBounds = pendingBytesBuckets,
            distributionSink = distributionSink
        )
        else -> {
            bootstrap.logger.warn(
                "latencyConnection is on, but this server does not expose the connection chain " +
                    "(CraftPlayer.getHandle().connection.connection.channel); pending bytes are off."
            )
            null
        }
    }

    private val tickDuration = Histogram(
        Metrics.Latency.TickDurationMs,
        upperBounds = tickBuckets,
        distributionSink = distributionSink
    )

    private val ticks = TickDurationRing(TICK_RING_CAPACITY)

    /**
     * Paper only. Folia never fires the tick event and Spigot does not have it;
     * the listener class is separate so that Spigot never loads the event type.
     */
    private val tickListener: TickDurationListener? = when (BukkitPlatform.current) {
        BukkitPlatform.Paper -> TickDurationListener(ticks, bootstrap)
        else -> null
    }

    override val collectors: List<Collector> = buildList {
        add(PlayerPingCollector(bootstrap.server, playerPing, connectionPendingBytes))
        add(playerPing)
        if (connectionPendingBytes != null) add(connectionPendingBytes)
        if (tickListener != null) {
            add(TickDurationCollector(ticks, tickDuration))
            add(tickDuration)
        }
    }

    override val isAsync: Boolean
        get() = true

    override fun initialize() {
        tickListener?.initialize()
    }

    override fun dispose() {
        tickListener?.dispose()
    }
}
