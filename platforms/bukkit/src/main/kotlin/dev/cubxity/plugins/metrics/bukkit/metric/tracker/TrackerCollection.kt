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

package dev.cubxity.plugins.metrics.bukkit.metric.tracker

import dev.cubxity.plugins.metrics.api.metric.DistributionSink
import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.collector.Counter
import dev.cubxity.plugins.metrics.api.metric.collector.Histogram
import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap
import dev.cubxity.plugins.metrics.common.metric.Metrics
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * The buckets count players, not seconds, so the histogram cannot use the
 * default latency buckets. The bounds bracket the tracker caps that
 * ShreddedPaper ships with (128 and 500).
 */
private val trackerBuckets = doubleArrayOf(
    1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 96.0,
    128.0, 192.0, 256.0, 384.0, 512.0, 768.0, 1024.0
)

/**
 * Entity tracker metrics: how many players track each entity, and how often the
 * server makes and breaks those pairs.
 *
 * A rank based tracker cap (ShreddedPaper's `maximum-trackers-per-entity`) keeps
 * the nearest N players for an entity. The membership of that set is unstable by
 * construction: when more players than the cap are candidates, the set is
 * rebuilt every `tracker-full-update-frequency` ticks, and a player near the
 * rank boundary loses the pair and gets it again. The client drops the entity
 * and adds it back at its new position, which a human sees as a jump instead of
 * smooth movement. TPS and MSPT stay perfect through all of it, so these
 * counters are the only server side signal of the condition.
 *
 * Paper fires [PlayerTrackEntityEvent] and [PlayerUntrackEntityEvent] for every
 * pair it makes and breaks, on the thread that owns the entity. On a regionized
 * server (Folia, ShreddedPaper) that is any one of many region threads at the
 * same time, so every write below goes to a concurrent structure:
 *
 * - the two counters use the default `DoubleAdder` store, like the join and quit
 *   counters in `EventsCollection`;
 * - the per entity tracker counts live in a [ConcurrentHashMap] of
 *   [AtomicInteger], so an event costs one hash lookup and one CAS, and
 *   allocates nothing once the entity has an entry.
 *
 * The map is the only per entity state the plugin keeps, because reading the
 * count from the API instead is not safe here: `CraftEntity.getTrackedBy()`
 * iterates `ChunkMap.TrackedEntity.seenBy`, a plain fastutil
 * `ReferenceOpenHashSet`, and takes no lock while it does it. A collection
 * thread that walked it could see a rehash in progress.
 *
 * Every path that breaks a pair in the server (`TrackedEntity.removePlayer`,
 * `broadcastRemoved`, and the moonrise `clearPlayers` and
 * `removeNonTickThreadPlayers` paths) calls `ServerEntity.removePairing`, which
 * calls `Entity.stopSeenByPlayer`, which fires the untrack event. The counts
 * therefore return to zero and the entry goes away. Only a reload of the plugin
 * with players already online can bias them, because the pairs that already
 * exist produce no track event; that bias decays as those pairs break.
 */
class TrackerCollection(
    private val bootstrap: UnifiedMetricsBukkitBootstrap,
    distributionSink: DistributionSink? = null
) : CollectorCollection, Listener {
    private val pairingsAdded = Counter(Metrics.Tracker.PairingsAdded)
    private val pairingsRemoved = Counter(Metrics.Tracker.PairingsRemoved)

    private val trackersPerEntity = Histogram(
        Metrics.Tracker.TrackersPerEntity,
        upperBounds = trackerBuckets,
        distributionSink = distributionSink
    )

    /** Live tracker count per entity. An entry exists while the count is above zero. */
    private val trackers = ConcurrentHashMap<Entity, AtomicInteger>()

    /**
     * The mapping function for `computeIfAbsent`, held in a field on purpose. A
     * lambda written at the call site compiles to an `invokedynamic` that wraps
     * the Kotlin function in a `java.util.function.Function` on every call, so
     * the hot path would allocate once per event. One stored instance cannot.
     */
    private val newCounter = Function<Entity, AtomicInteger> { AtomicInteger() }

    /**
     * The state collector observes into [trackersPerEntity], so it must run
     * before the histogram reports. [collect] keeps this order.
     */
    override val collectors: List<Collector> = listOf(
        TrackerStateCollector(trackers, trackersPerEntity),
        trackersPerEntity,
        pairingsAdded,
        pairingsRemoved
    )

    /**
     * The collector reads the concurrent map alone and never touches entity
     * state, so it must not take a region thread to do it.
     */
    override val isAsync: Boolean
        get() = true

    override fun initialize() {
        bootstrap.server.pluginManager.registerEvents(this, bootstrap)
    }

    override fun dispose() {
        HandlerList.unregisterAll(this)
        trackers.clear()
    }

    /**
     * MONITOR with `ignoreCancelled`, so the count follows the pairs the server
     * makes: a cancelled event shows the entity to nobody.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTrack(event: PlayerTrackEntityEvent) {
        pairingsAdded.inc()
        trackers.computeIfAbsent(event.entity, newCounter).incrementAndGet()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onUntrack(event: PlayerUntrackEntityEvent) {
        pairingsRemoved.inc()
        // Absent means the pair was made before this listener existed. The
        // collector prunes the entry once the count reaches zero; the hot path
        // never removes, so it cannot race the prune.
        trackers[event.entity]?.decrementAndGet()
    }
}
