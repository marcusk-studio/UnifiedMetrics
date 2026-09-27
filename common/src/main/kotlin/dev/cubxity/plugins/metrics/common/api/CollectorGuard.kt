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

package dev.cubxity.plugins.metrics.common.api

import dev.cubxity.plugins.metrics.api.logging.Logger
import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.api.util.fastForEach
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects one collector at a time and keeps a failure in one collector away
 * from the samples of the others.
 *
 * Before this guard, `MetricsManager.collect()` flat-mapped every collector
 * into one list, and every driver wrapped that call and its write in one
 * `try`. One collector that threw on every collection therefore dropped the
 * whole sample set of every collection, once per second, and logged a stack
 * trace once per second. Observed on ShreddedPaper: `FoliaRegionCollector`
 * threw `NoSuchFieldException: regioniser` 719 times in 12 minutes and no
 * sample reached DogStatsD.
 *
 * The first failure of each collector instance goes to the log with its stack
 * trace. Later failures of the same instance are silent, so a collector that
 * fails every second logs once per plugin load.
 */
class CollectorGuard(private val logger: Logger) {
    private val failed: MutableSet<Collector> = ConcurrentHashMap.newKeySet()

    /**
     * Appends the samples of every collector in [collection] to [sink]. A
     * collector that throws contributes nothing; the others still contribute.
     */
    fun collectInto(sink: MutableList<Metric>, collection: CollectorCollection) {
        collection.collectors.fastForEach { collector ->
            try {
                sink.addAll(collector.collect())
            } catch (error: Throwable) {
                if (failed.add(collector)) {
                    logger.warn(
                        "Collector ${collector.javaClass.name} failed. Its samples are dropped; " +
                            "the other collectors still report. This message is logged once per collector.",
                        error
                    )
                }
            }
        }
    }
}
