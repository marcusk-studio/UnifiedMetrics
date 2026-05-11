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

interface TracingManager {
    /**
     * Whether tracing is enabled and a driver has been successfully initialized.
     * When `false`, [tracer] returns [NoopTracer].
     */
    val isEnabled: Boolean

    /**
     * The currently active tracer, or [NoopTracer] when tracing is disabled or no
     * driver has been initialized yet.
     */
    val tracer: Tracer

    val driver: TracingDriver?

    fun initialize()

    fun registerDriver(name: String, factory: TracingDriverFactory<out Any>)

    fun dispose()
}
