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

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OtelPropagationTest {
    @Test
    fun `inject and extract preserves trace id across processes`() {
        val exporter = InMemorySpanExporter.create()
        val tp = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tp)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        // Build a Tracer wrapper using reflection through the public API would be heavy.
        // Instead, use the OTel SDK directly to verify the W3C round-trip the driver relies on.
        val otelTracer = sdk.getTracer("test")
        val span = otelTracer.spanBuilder("parent").startSpan()
        span.makeCurrent().use {
            val carrier = HashMap<String, String>()
            sdk.propagators.textMapPropagator.inject(
                io.opentelemetry.context.Context.current(),
                carrier
            ) { c, k, v -> c?.put(k, v) }

            // Headers should now contain a traceparent.
            assertNotNull(carrier["traceparent"])

            // Extract on a "remote" SDK and verify the trace id matches.
            val remoteCtx = sdk.propagators.textMapPropagator.extract(
                io.opentelemetry.context.Context.root(),
                carrier,
                object : io.opentelemetry.context.propagation.TextMapGetter<Map<String, String>> {
                    override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys
                    override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
                }
            )
            val remoteSpanContext = io.opentelemetry.api.trace.Span.fromContext(remoteCtx).spanContext
            assertEquals(span.spanContext.traceId, remoteSpanContext.traceId)
            assertEquals(span.spanContext.spanId, remoteSpanContext.spanId)
        }
        span.end()
        sdk.close()
    }
}
