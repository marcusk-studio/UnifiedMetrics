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

package dev.cubxity.plugins.metrics.tracing

import io.opentelemetry.api.trace.Span
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton service that holds the active OTel [io.opentelemetry.api.trace.Tracer] and
 * manages per-player session spans on the proxy side.
 *
 * Both the proxy ([VelocityTracingCollection]) and backend ([BukkitTracingCollection])
 * interact with this object.  On the proxy it stores the live session [Span] per player
 * UUID so that a child "backend connect" span can be created when the player is routed.
 *
 * The [tracer] field is set by [TracingDriver.initialize] and cleared in [TracingDriver.close].
 */
object PlayerTracingService {
    /**
     * The active OTel tracer.  Null when tracing is disabled or not yet initialized.
     */
    @Volatile
    var tracer: io.opentelemetry.api.trace.Tracer? = null

    /**
     * Live player-session spans keyed by player UUID.  Managed by the proxy.
     */
    val sessionSpans: ConcurrentHashMap<UUID, Span> = ConcurrentHashMap()
}
