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

import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.Histogram
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.common.metric.Metrics
import org.bukkit.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns the live tracker counts into the per collection samples. One pass over
 * the map gives every number, so the cost is one walk of the entities that at
 * least one player tracks, not one walk per metric.
 *
 * The walk uses the weakly consistent [ConcurrentHashMap] iterator, so region
 * threads keep writing through it and it never throws. An entry at zero is a
 * pair that ended, and the walk removes it, but only after it claims the counter with
 * [TRACKER_TOMBSTONE], because an increment can land while the walk is deciding.
 *
 * An entity is "cap binding" when its tracker count reaches the configured cap.
 * The server keeps the nearest N players, so a count equal to the cap means the
 * candidates reached or passed it and the entity is on the ordered, rebuilt
 * path. That path is the one that makes a client drop the entity and add it
 * again, which a player sees as a jump.
 */
class TrackerStateCollector(
    private val trackers: ConcurrentHashMap<Entity, AtomicInteger>,
    private val trackersPerEntity: Histogram
) : Collector {
    override fun collect(): List<Metric> {
        val cap = TrackerCap.value()

        var entities = 0
        var pairings = 0L
        var max = 0
        var capBinding = 0

        val iterator = trackers.entries.iterator()
        while (iterator.hasNext()) {
            val counter = iterator.next().value
            val count = counter.get()
            if (count <= 0) {
                // Claim the counter before removing the entry. The hot path reaches
                // this same AtomicInteger through the map and increments it without
                // holding any bin lock, so "read zero, then remove" can delete a pair
                // that was created between the two steps, and that pair is then lost
                // for good: the next track event builds a fresh counter from zero.
                //
                // If the CAS fails, an increment beat us to it. Leave the entry alone
                // and let the next collection count it.
                if (counter.compareAndSet(count, TRACKER_TOMBSTONE)) iterator.remove()
                continue
            }

            entities++
            pairings += count
            if (count > max) max = count
            if (cap > 0 && count >= cap) capBinding++
            trackersPerEntity += count
        }

        return listOf(
            GaugeMetric(Metrics.Tracker.TrackedEntities, value = entities),
            GaugeMetric(Metrics.Tracker.PairingsLive, value = pairings),
            GaugeMetric(Metrics.Tracker.TrackersPerEntityMax, value = max),
            GaugeMetric(Metrics.Tracker.CapBindingEntities, value = capBinding),
            GaugeMetric(Metrics.Tracker.Cap, value = cap)
        )
    }
}
