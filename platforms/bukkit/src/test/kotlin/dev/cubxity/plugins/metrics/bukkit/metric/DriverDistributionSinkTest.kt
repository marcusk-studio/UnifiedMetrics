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
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import dev.cubxity.plugins.metrics.api.metric.MetricsDriverFactory
import dev.cubxity.plugins.metrics.api.metric.MetricsManager
import dev.cubxity.plugins.metrics.api.metric.collector.CollectorCollection
import dev.cubxity.plugins.metrics.api.metric.collector.Histogram
import dev.cubxity.plugins.metrics.api.metric.data.Labels
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import kotlin.test.Test
import kotlin.test.assertEquals

class DriverDistributionSinkTest {
    /** A manager whose driver appears after the collections exist, as in `enable()`. */
    private class LateDriverManager : MetricsManager {
        override var driver: MetricsDriver? = null
        override val collections: List<CollectorCollection> get() = emptyList()
        override fun initialize() = Unit
        override fun registerCollection(collection: CollectorCollection) = Unit
        override fun unregisterCollection(collection: CollectorCollection) = Unit
        override fun registerDriver(name: String, factory: MetricsDriverFactory<out Any>) = Unit
        override suspend fun collect(): List<Metric> = emptyList()
        override fun dispose() = Unit
    }

    private class RecordingDriver : MetricsDriver, DistributionSink {
        val samples = ArrayList<Triple<String, Double, Labels>>()
        override fun initialize() = Unit
        override fun close() = Unit
        override fun recordDistribution(name: String, value: Double, labels: Labels) {
            samples.add(Triple(name, value, labels))
        }
    }

    private class PlainDriver : MetricsDriver {
        override fun initialize() = Unit
        override fun close() = Unit
    }

    @Test
    fun `samples recorded after the driver appears reach it`() {
        val manager = LateDriverManager()
        val histogram = Histogram("t", distributionSink = DriverDistributionSink(manager))

        histogram += 1.0 // no driver yet: dropped, not thrown
        val driver = RecordingDriver()
        manager.driver = driver
        histogram += 2.0
        histogram += 3.0

        assertEquals(listOf(2.0, 3.0), driver.samples.map { it.second })
        assertEquals("t", driver.samples.first().first)
    }

    @Test
    fun `a driver that is not a sink drops the sample`() {
        val manager = LateDriverManager()
        manager.driver = PlainDriver()
        val histogram = Histogram("t", distributionSink = DriverDistributionSink(manager))

        histogram += 1.0

        // The histogram still counted the sample; only the raw value has no taker.
        assertEquals(1.0, histogram.collect().single().let { (it as dev.cubxity.plugins.metrics.api.metric.data.HistogramMetric).sampleCount })
    }
}
