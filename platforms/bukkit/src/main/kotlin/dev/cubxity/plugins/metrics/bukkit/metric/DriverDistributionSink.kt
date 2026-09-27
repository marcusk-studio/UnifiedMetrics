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

package dev.cubxity.plugins.metrics.bukkit.metric

import dev.cubxity.plugins.metrics.api.metric.DistributionSink
import dev.cubxity.plugins.metrics.api.metric.MetricsManager
import dev.cubxity.plugins.metrics.api.metric.data.Labels

/**
 * A [DistributionSink] that forwards to whichever driver the [manager] holds
 * at the moment a sample is recorded.
 *
 * The plugin builds its collections before the metrics manager initializes
 * the driver, so a sink captured at construction time is always null and the
 * histograms would count and sum their samples while the driver never saw a
 * single raw value. Resolving the driver per record costs one volatile read
 * and one type check, and it also follows a driver swap at runtime.
 *
 * A driver that is not a [DistributionSink] (Prometheus, Influx) drops the
 * sample here, which is the same as the null sink it replaces.
 */
class DriverDistributionSink(private val manager: MetricsManager) : DistributionSink {
    override fun recordDistribution(name: String, value: Double, labels: Labels) {
        (manager.driver as? DistributionSink)?.recordDistribution(name, value, labels)
    }
}
