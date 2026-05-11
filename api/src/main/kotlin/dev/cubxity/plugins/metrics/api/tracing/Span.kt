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

interface Span {
    /**
     * The span's context, suitable for passing as a parent or for inter-process
     * propagation via [Tracer.inject].
     */
    val context: SpanContext

    fun setAttribute(key: String, value: String): Span
    fun setAttribute(key: String, value: Long): Span
    fun setAttribute(key: String, value: Double): Span
    fun setAttribute(key: String, value: Boolean): Span

    /**
     * Mark the span as errored. Subsequent calls have no effect.
     */
    fun setError(message: String? = null): Span

    fun recordException(throwable: Throwable): Span

    /**
     * Finalize the span. Idempotent — repeated calls are no-ops.
     */
    fun end()
}
