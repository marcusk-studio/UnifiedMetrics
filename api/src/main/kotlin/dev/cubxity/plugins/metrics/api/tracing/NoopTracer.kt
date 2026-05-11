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
 * No-op [Tracer] returned when tracing is disabled or no driver is loaded.
 */
object NoopTracer : Tracer {
    private val noopContext = object : SpanContext {
        override val headers: Map<String, String> = emptyMap()
        override val extra: Any? = null
    }

    private val noopSpan = object : Span {
        override val context: SpanContext = noopContext
        override fun setAttribute(key: String, value: String): Span = this
        override fun setAttribute(key: String, value: Long): Span = this
        override fun setAttribute(key: String, value: Double): Span = this
        override fun setAttribute(key: String, value: Boolean): Span = this
        override fun setError(message: String?): Span = this
        override fun recordException(throwable: Throwable): Span = this
        override fun end() {}
    }

    override fun startSpan(name: String, parent: SpanContext?, attributes: Map<String, String>): Span = noopSpan
    override fun inject(context: SpanContext): Map<String, String> = emptyMap()
    override fun extract(headers: Map<String, String>): SpanContext? = null
}
