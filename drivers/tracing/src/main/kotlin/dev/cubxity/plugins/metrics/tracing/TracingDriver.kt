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

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor

/**
 * Metrics driver that owns the OpenTelemetry SDK lifecycle.
 *
 * On [initialize] it builds an [OpenTelemetrySdk] with an OTLP gRPC span exporter and
 * stores the resulting [io.opentelemetry.api.trace.Tracer] in [PlayerTracingService].
 * Platform-specific tracing collections ([VelocityTracingCollection],
 * [BukkitTracingCollection]) obtain the tracer from there.
 *
 * On [close] all active session spans are ended and the SDK is shut down cleanly.
 */
class TracingDriver(private val api: UnifiedMetrics, private val config: TracingConfig) : MetricsDriver {
    private var sdk: OpenTelemetrySdk? = null

    override fun initialize() {
        val serviceName = System.getenv("OTEL_SERVICE_NAME") ?: api.serverName

        val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.of(
                    AttributeKey.stringKey("service.name"), serviceName
                )
            )
        )

        val spanExporter = when (config.exporter.lowercase()) {
            "otlp" -> OtlpGrpcSpanExporter.builder()
                .setEndpoint(config.endpoint)
                .build()
            else -> return // exporter type "none" — tracing disabled
        }

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()

        this.sdk = sdk
        PlayerTracingService.tracer = sdk.getTracer("unifiedmetrics")
    }

    override fun close() {
        PlayerTracingService.tracer = null
        // End any leaked session spans before shutting down the SDK.
        PlayerTracingService.sessionSpans.values.forEach { it.end() }
        PlayerTracingService.sessionSpans.clear()
        sdk?.close()
        sdk = null
    }
}
