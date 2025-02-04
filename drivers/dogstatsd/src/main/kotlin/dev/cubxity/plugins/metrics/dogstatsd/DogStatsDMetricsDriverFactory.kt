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

package dev.cubxity.plugins.metrics.dogstatsd

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import dev.cubxity.plugins.metrics.api.metric.MetricsDriverFactory
import kotlinx.serialization.KSerializer

object DogStatsDMetricsDriverFactory : MetricsDriverFactory<DogStatsDConfig> {
    override val configSerializer: KSerializer<DogStatsDConfig>
        get() = DogStatsDConfig.serializer()

    override val defaultConfig: DogStatsDConfig
        get() = DogStatsDConfig("")

    override fun createDriver(api: UnifiedMetrics, config: DogStatsDConfig): MetricsDriver = DogStatsDConfigMetricsDriver(api, config)
}