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
import dev.cubxity.plugins.metrics.api.logging.Logger
import dev.cubxity.plugins.metrics.api.metric.MetricsManager
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import dev.cubxity.plugins.metrics.api.metric.MetricsDriverFactory
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.api.platform.Platform
import dev.cubxity.plugins.metrics.api.platform.PlatformType
import dev.cubxity.plugins.metrics.api.tracing.NoopTracer
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingDriver
import dev.cubxity.plugins.metrics.api.tracing.TracingDriverFactory
import dev.cubxity.plugins.metrics.api.tracing.TracingManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Minimal fake implementation of [UnifiedMetrics] for testing.
 */
class FakeUnifiedMetrics : UnifiedMetrics {
    override val platform: Platform = object : Platform {
        override val type: PlatformType = PlatformType.Bukkit
    }
    override val serverName: String = "test-server"
    override val logger: Logger = object : Logger {
        override fun info(message: String) = println("[INFO] $message")
        override fun warn(message: String) = println("[WARN] $message")
        override fun warn(message: String, error: Throwable) { println("[WARN] $message"); error.printStackTrace() }
        override fun severe(message: String) = println("[SEVERE] $message")
        override fun severe(message: String, error: Throwable) { println("[SEVERE] $message"); error.printStackTrace() }
    }
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default
    override val metricsManager: MetricsManager = object : MetricsManager {
        override val collections: List<CollectorCollection> = emptyList()
        override val driver: MetricsDriver? = null
        override fun initialize() {}
        override fun registerCollection(collection: CollectorCollection) {}
        override fun unregisterCollection(collection: CollectorCollection) {}
        override fun registerDriver(name: String, factory: MetricsDriverFactory<out Any>) {}
        override suspend fun collect(): List<Metric> = emptyList()
        override fun dispose() {}
    }
    override val tracingManager: TracingManager = object : TracingManager {
        override val isEnabled: Boolean = false
        override val tracer: Tracer = NoopTracer
        override val driver: TracingDriver? = null
        override fun initialize() {}
        override fun registerDriver(name: String, factory: TracingDriverFactory<out Any>) {}
        override fun dispose() {}
    }
}
