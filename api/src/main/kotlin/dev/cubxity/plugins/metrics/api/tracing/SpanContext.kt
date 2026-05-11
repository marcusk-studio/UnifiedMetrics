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

/**
 * Opaque holder for a remote or local span context.
 *
 * Drivers are free to attach extra state via [extra]; the [headers] map is used
 * for cross-process propagation (W3C `traceparent` / `tracestate`, etc.).
 */
interface SpanContext {
    /**
     * Propagation headers (e.g. `traceparent`, `tracestate`).
     */
    val headers: Map<String, String>

    /**
     * Driver-specific state. Opaque to callers.
     */
    val extra: Any?
}
