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

package dev.cubxity.plugins.metrics.core.plugin

import dev.cubxity.plugins.metrics.common.plugin.AbstractUnifiedMetricsPlugin
import dev.cubxity.plugins.metrics.influx.InfluxMetricsDriverFactory
import dev.cubxity.plugins.metrics.prometheus.PrometheusMetricsDriverFactory
import dev.cubxity.plugins.metrics.dogstatsd.DogStatsDMetricsDriverFactory
import dev.cubxity.plugins.metrics.tracing.TracingConfig
import dev.cubxity.plugins.metrics.tracing.TracingDriver

abstract class CoreUnifiedMetricsPlugin : AbstractUnifiedMetricsPlugin() {
    private var tracingDriver: TracingDriver? = null

    override fun registerMetricsDrivers() {
        apiProvider.metricsManager.apply {
            registerDriver("influx", InfluxMetricsDriverFactory)
            registerDriver("prometheus", PrometheusMetricsDriverFactory)
            registerDriver("dogstatsd", DogStatsDMetricsDriverFactory)
        }
    }

    /**
     * Initialises the OpenTelemetry SDK using the endpoint from [config.tracing].
     * The endpoint can also be overridden with the OTEL_EXPORTER_OTLP_ENDPOINT env-var.
     */
    override fun registerTracingDriver() {
        val endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") ?: config.tracing.endpoint
        val driverConfig = TracingConfig(endpoint = endpoint)
        val driver = TracingDriver(apiProvider, driverConfig)
        try {
            driver.initialize()
            tracingDriver = driver
        } catch (e: Exception) {
            apiProvider.logger.severe("Failed to initialise APM tracing driver", e)
        }
    }

    /**
     * Shuts down the tracing driver (flushes and closes the OTel SDK).
     * Platform-specific plugins should override this method, dispose their own tracing
     * collections first, and then call [super.disposePlatformTracing].
     */
    override fun disposePlatformTracing() {
        tracingDriver?.close()
        tracingDriver = null
    }
}
