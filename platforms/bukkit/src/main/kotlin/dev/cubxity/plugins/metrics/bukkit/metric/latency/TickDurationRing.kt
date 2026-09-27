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

import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicLong
import java.util.function.DoubleConsumer

/**
 * A ring of tick durations with one writer and one reader. The writer, the
 * server tick thread, does one array store and one release store per tick and
 * nothing else; the reader, the collection thread, drains the ring once per
 * interval.
 *
 * Observing into the histogram on the tick thread would cost more than it
 * looks: `Histogram.plusAssign` forwards every value to the distribution sink,
 * and the DogStatsD driver builds a tag list and splits an environment variable
 * per call. That allocates on every tick. The ring moves that work off the tick
 * thread, and the histogram then sees the same values a collection later.
 *
 * Publication: the writer stores the value into its slot, then publishes the
 * count with a release store ([AtomicLong.lazySet]); the reader loads the count
 * with a volatile read. That pair orders the slot store before the reader's
 * slot load, so a plain [DoubleArray] is enough.
 *
 * Overrun: the writer never waits. When the reader falls a lap behind, the
 * writer overwrites unread slots, and the reader counts those as dropped
 * instead of reporting a torn or newer value. The writer stores index `i` into
 * slot `i mod capacity` and then publishes `i + 1`, so it reuses the slot of
 * index `r` for index `r + capacity`, and it begins that store only after it
 * has published `r + capacity`. The reader therefore re-reads the published
 * count after each slot load: while it is still below `r + capacity`, the load
 * saw the value for `r`.
 */
class TickDurationRing(capacity: Int) {
    init {
        require(capacity > 1 && capacity and (capacity - 1) == 0) { "capacity must be a power of two" }
    }

    private val capacity = capacity.toLong()
    private val mask = (capacity - 1).toLong()
    private val slots = DoubleArray(capacity)

    /** Index of the next value to store. Only the writer touches it. */
    private var next = 0L

    /** Count of values published so far. The writer stores it; the reader loads it. */
    private val published = AtomicLong()

    /** Index of the next value to take. Only the reader touches it. */
    private var taken = 0L

    /**
     * Stores one tick duration. Call it from one thread only; a second writer
     * would race on [next] and on the slot it selects.
     */
    fun record(value: Double) {
        val index = next
        slots[(index and mask).toInt()] = value
        next = index + 1
        published.lazySet(index + 1)
    }

    /**
     * Passes every value published since the last drain to [consumer], oldest
     * first, and returns how many the writer overwrote before the reader got to
     * them. Call it from one thread at a time: the caller holds the lock, not
     * the ring, so the tick thread never meets one.
     */
    fun drain(consumer: DoubleConsumer): Long {
        var dropped = 0L
        val end = published.get()
        var index = taken

        // A lap or more behind: everything older than the newest capacity - 1
        // values is overwritten or being overwritten. Skip to the oldest safe one.
        val oldest = end - (capacity - 1)
        if (index < oldest) {
            dropped += oldest - index
            index = oldest
        }

        while (index < end) {
            val value = slots[(index and mask).toInt()]
            // See the class comment: once the writer has published index + capacity
            // it may be storing into this slot, and the load above is not trusted.
            //
            // The fence is load-bearing. A volatile load is an acquire: it keeps
            // later loads from moving before it, and does nothing to the earlier
            // slot load, which the JIT or the CPU may run after the recheck. The
            // ring test caught exactly that: one lapped value in two million with
            // the recheck alone. The fence pins the slot load before the recheck.
            VarHandle.loadLoadFence()
            if (published.get() - index >= capacity) dropped++ else consumer.accept(value)
            index++
        }

        taken = end
        return dropped
    }
}
