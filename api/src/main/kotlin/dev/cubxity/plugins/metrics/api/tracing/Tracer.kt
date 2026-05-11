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

interface Tracer {
    /**
     * Start a new span.
     *
     * @param name The operation name (e.g. `player.login`).
     * @param parent An optional parent context (local span or remote-extracted).
     * @param attributes Optional initial attributes.
     */
    fun startSpan(
        name: String,
        parent: SpanContext? = null,
        attributes: Map<String, String> = emptyMap()
    ): Span

    /**
     * Serialize a context into a header map for cross-process propagation.
     */
    fun inject(context: SpanContext): Map<String, String>

    /**
     * Reconstruct a context from headers received from another process.
     * Returns `null` if no valid context could be extracted.
     */
    fun extract(headers: Map<String, String>): SpanContext?
}
