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

package dev.cubxity.plugins.metrics.bukkit.metric.regionized

import dev.cubxity.plugins.metrics.api.metric.collector.Collector
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap

/**
 * Per-region metrics for Folia and its forks. Replaces the single global tick
 * collection, which has no meaning on a regionized server.
 */
class FoliaRegionCollection(bootstrap: UnifiedMetricsBukkitBootstrap) : CollectorCollection {
    override val collectors: List<Collector> = listOf(
        FoliaRegionCollector(bootstrap.logger, "${bootstrap.server.name} ${bootstrap.server.version}")
    )

    // The regioniser's computeForAllRegions takes its own lock, so this can
    // run off the global region thread, and it must: collecting on the global
    // region would add the walk to that region's tick.
    override val isAsync: Boolean
        get() = true
}
