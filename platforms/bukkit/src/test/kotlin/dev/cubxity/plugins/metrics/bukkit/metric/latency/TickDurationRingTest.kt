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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.DoubleConsumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickDurationRingTest {
    private class Sink : DoubleConsumer {
        val values = ArrayList<Double>()
        override fun accept(value: Double) {
            values.add(value)
        }
    }

    @Test
    fun `drains what was recorded, in order, once`() {
        val ring = TickDurationRing(8)
        val sink = Sink()

        ring.record(1.0)
        ring.record(2.0)
        ring.record(3.0)
        assertEquals(0L, ring.drain(sink))
        assertEquals(listOf(1.0, 2.0, 3.0), sink.values)

        sink.values.clear()
        assertEquals(0L, ring.drain(sink))
        assertTrue(sink.values.isEmpty(), "a second drain returns nothing new")

        ring.record(4.0)
        assertEquals(0L, ring.drain(sink))
        assertEquals(listOf(4.0), sink.values)
    }

    @Test
    fun `an overrun drops the oldest values and keeps the newest capacity - 1`() {
        val capacity = 8
        val ring = TickDurationRing(capacity)
        val sink = Sink()

        val written = 20
        for (i in 0 until written) ring.record(i.toDouble())

        val dropped = ring.drain(sink)
        val kept = capacity - 1
        assertEquals((written - kept).toLong(), dropped)
        assertEquals((written - kept until written).map { it.toDouble() }, sink.values)
    }

    /**
     * The value stored at index i is i itself, so a torn, stale or lapped read
     * shows up as a value that is not the next larger integer. The reader must
     * see a strictly rising sequence of integers, and reads plus drops must
     * account for every publication.
     */
    @Test
    fun `a concurrent writer never makes the reader see a wrong value`() {
        val ring = TickDurationRing(16)
        val total = 2_000_000L
        val stop = AtomicBoolean(false)

        val writer = Thread {
            var i = 0L
            while (i < total) {
                ring.record(i.toDouble())
                i++
            }
            stop.set(true)
        }

        var last = -1.0
        var seen = 0L
        var dropped = 0L
        val checker = DoubleConsumer { value ->
            assertTrue(value > last, "read $value after $last")
            assertEquals(value, Math.floor(value), "torn read $value")
            last = value
            seen++
        }

        writer.start()
        while (!stop.get()) dropped += ring.drain(checker)
        dropped += ring.drain(checker)
        writer.join()

        assertEquals(total, seen + dropped, "every publication is read or counted as dropped")
        assertTrue(seen > 0, "the reader saw something")
        assertTrue(dropped > 0, "the writer lapped the reader at least once, so the guard was exercised")
    }
}
