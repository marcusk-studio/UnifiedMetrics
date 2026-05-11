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

package dev.cubxity.plugins.metrics.tracing.otel

import dev.cubxity.plugins.metrics.api.tracing.TracingChannels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TracingChannelsTest {
    @Test
    fun `encode and decode round trips traceparent`() {
        val headers = mapOf(
            "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
        )
        val payload = TracingChannels.encode(headers)
        assertNotNull(payload)
        val decoded = TracingChannels.decode(payload)
        assertNotNull(decoded)
        assertEquals(headers["traceparent"], decoded["traceparent"])
        assertNull(decoded["tracestate"])
    }

    @Test
    fun `encode and decode round trips tracestate`() {
        val headers = mapOf(
            "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
            "tracestate" to "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE"
        )
        val decoded = TracingChannels.decode(TracingChannels.encode(headers)!!)
        assertNotNull(decoded)
        assertEquals(headers["traceparent"], decoded["traceparent"])
        assertEquals(headers["tracestate"], decoded["tracestate"])
    }

    @Test
    fun `encode without traceparent returns null`() {
        assertNull(TracingChannels.encode(emptyMap()))
    }

    @Test
    fun `decode garbage returns null`() {
        assertNull(TracingChannels.decode(byteArrayOf(0x42, 0x43, 0x44)))
        assertNull(TracingChannels.decode(byteArrayOf()))
    }
}
