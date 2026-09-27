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
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CollectorGuardTest {
    private class RecordingLogger : Logger {
        val warnings = ArrayList<Pair<String, Throwable?>>()
        override fun info(message: String) = Unit
        override fun warn(message: String) { warnings.add(message to null) }
        override fun warn(message: String, error: Throwable) { warnings.add(message to error) }
        override fun severe(message: String) = error("unexpected severe: $message")
        override fun severe(message: String, error: Throwable) = error("unexpected severe: $message")
    }

    private class Throwing : Collector {
        var calls = 0
        override fun collect(): List<Metric> {
            calls++
            throw NoSuchFieldException("regioniser")
        }
    }

    private class Healthy : Collector {
        override fun collect(): List<Metric> = listOf(GaugeMetric("healthy", value = 1.0))
    }

    private class Collection(override val collectors: List<Collector>) : CollectorCollection

    @Test
    fun `a collector that throws drops only its own samples`() {
        val logger = RecordingLogger()
        val guard = CollectorGuard(logger)
        val throwing = Throwing()
        val collection = Collection(listOf(throwing, Healthy()))

        val sink = ArrayList<Metric>()
        guard.collectInto(sink, collection)

        assertEquals(listOf("healthy"), sink.map { it.name })
        assertEquals(1, throwing.calls)
    }

    @Test
    fun `the first failure of a collector is logged once with its cause`() {
        val logger = RecordingLogger()
        val guard = CollectorGuard(logger)
        val throwing = Throwing()
        val collection = Collection(listOf(throwing, Healthy()))

        val sink = ArrayList<Metric>()
        repeat(5) { guard.collectInto(sink, collection) }

        assertEquals(5, throwing.calls, "the collector is still called; it is not unregistered")
        assertEquals(5, sink.size, "the healthy collector reported on every round")
        assertEquals(1, logger.warnings.size, "one warning for five failures")
        val (message, error) = logger.warnings.single()
        assertEquals(true, message.contains(Throwing::class.java.name))
        assertEquals("regioniser", error?.message)
    }

    @Test
    fun `two failing instances of one class each log once`() {
        val logger = RecordingLogger()
        val guard = CollectorGuard(logger)
        val first = Throwing()
        val second = Throwing()

        val sink = ArrayList<Metric>()
        repeat(3) { guard.collectInto(sink, Collection(listOf(first, second))) }

        assertEquals(2, logger.warnings.size)
        assertSame(logger.warnings[0].second!!::class.java, logger.warnings[1].second!!::class.java)
    }
}
