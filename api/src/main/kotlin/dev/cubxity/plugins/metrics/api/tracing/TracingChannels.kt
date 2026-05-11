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

package dev.cubxity.plugins.metrics.api.tracing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Plugin-message channel + payload codec used to propagate trace context from a
 * proxy to backend Minecraft servers.
 *
 * Payload format (version 1):
 * ```
 *   byte  version = 1
 *   utf   traceparent      (DataOutputStream#writeUTF: u16 length + UTF-8 bytes)
 *   utf   tracestate       (may be empty)
 * ```
 */
object TracingChannels {
    const val NAMESPACE: String = "unifiedmetrics"
    const val NAME: String = "trace"

    /**
     * Channel identifier in the form expected by Bukkit / Velocity (`namespace:name`).
     */
    const val CHANNEL: String = "$NAMESPACE:$NAME"

    private const val VERSION: Byte = 1

    private const val H_TRACEPARENT = "traceparent"
    private const val H_TRACESTATE = "tracestate"

    fun encode(headers: Map<String, String>): ByteArray? {
        val traceparent = headers[H_TRACEPARENT] ?: return null
        val tracestate = headers[H_TRACESTATE].orEmpty()

        val baos = ByteArrayOutputStream(64)
        DataOutputStream(baos).use { out ->
            out.writeByte(VERSION.toInt())
            out.writeUTF(traceparent)
            out.writeUTF(tracestate)
        }
        return baos.toByteArray()
    }

    fun decode(payload: ByteArray): Map<String, String>? {
        if (payload.isEmpty()) return null
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val version = input.readByte()
                if (version != VERSION) return null
                val traceparent = input.readUTF()
                val tracestate = input.readUTF()
                buildMap {
                    put(H_TRACEPARENT, traceparent)
                    if (tracestate.isNotEmpty()) put(H_TRACESTATE, tracestate)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
