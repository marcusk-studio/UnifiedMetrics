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
import dev.cubxity.plugins.metrics.api.metric.data.CounterMetric
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.common.metric.Metrics
import java.util.function.DoubleConsumer

/**
 * Drains the tick ring into the tick duration histogram and reports the longest
 * tick of the interval. The histogram gives p95 and p99 (bucketed for
 * Prometheus, per sample for a distribution sink); the max gauge names the
 * single worst tick, which no percentile of a second's worth of ticks does.
 *
 * The collector is its own [DoubleConsumer], so the drain passes no lambda and
 * boxes no value. The max resets on every collection; the dropped count does
 * not, because a counter that only rises is what a rate() wants.
 *
 * A tick that has not ended is not in the ring. A hard stall therefore shows as
 * an interval with no samples, and the long tick lands in the collection after
 * the server resumes. The histogram count is the signal for the stall itself.
 *
 * `collect` holds a lock while it drains, because two drivers (or two
 * Prometheus scrapes) could collect at once, and the ring allows one reader at
 * a time. The tick thread never takes that lock: it writes to the ring only.
 */
class TickDurationCollector(
    private val ring: TickDurationRing,
    private val tickDuration: Histogram
) : Collector, DoubleConsumer {
    private var max = 0.0
    private var dropped = 0L

    override fun accept(value: Double) {
        tickDuration += value
        if (value > max) max = value
    }

    override fun collect(): List<Metric> = synchronized(this) {
        max = 0.0
        dropped += ring.drain(this)
        listOf(
            GaugeMetric(Metrics.Latency.TickDurationMsMax, value = max),
            CounterMetric(Metrics.Latency.TickDurationSamplesDropped, value = dropped)
        )
    }
}
