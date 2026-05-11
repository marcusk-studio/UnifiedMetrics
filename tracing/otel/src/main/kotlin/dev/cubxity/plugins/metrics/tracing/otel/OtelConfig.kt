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

import kotlinx.serialization.Serializable

@Serializable
data class OtelConfig(
    /**
     * Tracer provider mode.
     *
     * - `auto` (default): if calling `GlobalOpenTelemetry.get().getTracerProvider()`
     *   returns something other than the no-op provider, use it directly. This
     *   is what happens when an auto-instrumentation agent is attached and has
     *   replaced the global tracer provider (e.g. `dd-trace-java` rewrites
     *   `OpenTelemetry.getTracerProvider()` via bytecode advice; that requires
     *   `DD_TRACE_OTEL_ENABLED=true`, it is off by default). Otherwise fall back
     *   to building our own SDK + OTLP exporter.
     * - `global`: always use whatever `GlobalOpenTelemetry.get()` returns. If
     *   nothing has replaced it, spans become no-ops.
     * - `sdk`: always build our own SDK + OTLP exporter, regardless of any
     *   global tracer provider.
     */
    val mode: String = "auto",
    /**
     * Logical service name. Falls back to `UnifiedMetrics.serverName` when blank.
     */
    val serviceName: String = "",
    /**
     * OTLP endpoint URL (e.g. `http://localhost:4317` for gRPC, `http://localhost:4318/v1/traces` for HTTP).
     * If blank, the OTel SDK's default (env vars `OTEL_EXPORTER_OTLP_*`) is used.
     * Only used in `sdk` mode (or `auto` mode when no global tracer is registered).
     */
    val endpoint: String = "",
    /**
     * `grpc` or `http/protobuf`.
     */
    val protocol: String = "grpc",
    /**
     * Extra OTLP exporter headers, e.g. `DD-API-KEY: <key>` for direct Datadog ingest.
     */
    val headers: Map<String, String> = emptyMap(),
    /**
     * Parent-based ratio sampler. 1.0 = sample all, 0.0 = drop all.
     */
    val sampleRatio: Double = 1.0,
    /**
     * `service.namespace` resource attribute.
     */
    val serviceNamespace: String = "minecraft",
    /**
     * `deployment.environment` resource attribute. Falls back to env `OTEL_DEPLOYMENT_ENVIRONMENT`.
     */
    val deploymentEnvironment: String = ""
)
