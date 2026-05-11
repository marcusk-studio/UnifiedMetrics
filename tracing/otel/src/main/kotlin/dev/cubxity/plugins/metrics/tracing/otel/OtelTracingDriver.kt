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

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.tracing.Span
import dev.cubxity.plugins.metrics.api.tracing.SpanContext
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingDriver
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.util.concurrent.TimeUnit
import io.opentelemetry.api.trace.Span as OtelSpan
import io.opentelemetry.api.trace.Tracer as OtelTracer

class OtelTracingDriver(
    private val api: UnifiedMetrics,
    private val config: OtelConfig
) : TracingDriver {

    private var sdk: OpenTelemetrySdk? = null
    private var spanProcessor: SpanProcessor? = null
    private lateinit var _tracer: TracerImpl

    override val tracer: Tracer
        get() = _tracer

    override fun initialize() {
        val mode = config.mode.lowercase()
        val global = if (mode != "sdk") detectGlobal() else null

        if (global != null) {
            api.logger.info(
                "OTel tracing: GlobalOpenTelemetry returns a non-noop tracer provider (${global.tracerProvider.javaClass.name}); " +
                "using it directly. Spans will flow through whatever has replaced the global provider " +
                "(e.g. an attached auto-instrumentation agent); the OTLP endpoint config is ignored."
            )
            _tracer = TracerImpl(global, global.getTracer("dev.cubxity.unifiedmetrics"))
            return
        }

        if (mode == "global") {
            api.logger.warn(
                "OTel tracing: mode=global but GlobalOpenTelemetry returns the noop tracer provider. Spans will be no-ops."
            )
            // Use the no-op global; spans become no-ops but the API stays consistent.
            val noop = OpenTelemetry.noop()
            _tracer = TracerImpl(noop, noop.getTracer("dev.cubxity.unifiedmetrics"))
            return
        }

        // Build our own SDK + OTLP exporter.
        val serviceName = config.serviceName.ifBlank { api.serverName }
        val deploymentEnv = config.deploymentEnvironment.ifBlank {
            System.getenv("OTEL_DEPLOYMENT_ENVIRONMENT").orEmpty()
        }

        val resourceBuilder = Attributes.builder()
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .put(AttributeKey.stringKey("service.namespace"), config.serviceNamespace)
        if (deploymentEnv.isNotBlank()) {
            resourceBuilder.put(AttributeKey.stringKey("deployment.environment"), deploymentEnv)
        }
        val resource = Resource.getDefault().merge(Resource.create(resourceBuilder.build()))

        val exporter = buildExporter()
        val processor = BatchSpanProcessor.builder(exporter)
            .setScheduleDelay(1, TimeUnit.SECONDS)
            .build()
        spanProcessor = processor

        val sampler = Sampler.parentBased(Sampler.traceIdRatioBased(config.sampleRatio.coerceIn(0.0, 1.0)))

        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(processor)
            .setSampler(sampler)
            .build()

        val propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance())

        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(propagators)
            .build()
        this.sdk = sdk

        api.logger.info("OTel tracing: built local SDK with OTLP exporter (endpoint='${config.endpoint.ifBlank { "<env default>" }}', protocol=${config.protocol}).")
        _tracer = TracerImpl(sdk, sdk.getTracer("dev.cubxity.unifiedmetrics"))
    }

    /**
     * Returns the [GlobalOpenTelemetry] holder if its tracer provider is no longer
     * the no-op default. Auto-instrumentation agents replace the provider in
     * different ways: dd-trace-java does it via bytecode advice on
     * `OpenTelemetry.getTracerProvider()` (only when `DD_TRACE_OTEL_ENABLED=true`),
     * the OTel Java agent does it via `GlobalOpenTelemetry.set(...)`. Either way,
     * the call below sees a non-noop provider and we route through it.
     */
    private fun detectGlobal(): OpenTelemetry? {
        return try {
            val candidate = GlobalOpenTelemetry.get()
            // Compare the tracer provider against the no-op singleton; if it's
            // the no-op, nothing is registered.
            if (candidate.tracerProvider !== TracerProvider.noop()) candidate else null
        } catch (_: Throwable) {
            null
        }
    }

    override fun close() {
        try {
            spanProcessor?.forceFlush()?.join(5, TimeUnit.SECONDS)
        } catch (_: Throwable) {
        }
        try {
            sdk?.close()
        } catch (_: Throwable) {
        }
        sdk = null
        spanProcessor = null
    }

    private fun buildExporter(): SpanExporter {
        val protocol = config.protocol.lowercase()
        return when (protocol) {
            "http", "http/protobuf" -> {
                val builder = OtlpHttpSpanExporter.builder()
                if (config.endpoint.isNotBlank()) builder.setEndpoint(config.endpoint)
                config.headers.forEach { (k, v) -> builder.addHeader(k, v) }
                builder.build()
            }
            else -> {
                val builder = OtlpGrpcSpanExporter.builder()
                if (config.endpoint.isNotBlank()) builder.setEndpoint(config.endpoint)
                config.headers.forEach { (k, v) -> builder.addHeader(k, v) }
                builder.build()
            }
        }
    }

    private class OtelSpanContext(
        override val headers: Map<String, String>,
        override val extra: Any? // io.opentelemetry.context.Context
    ) : SpanContext

    private class SpanImpl(
        private val span: OtelSpan,
        private val propagator: TextMapPropagator
    ) : Span {
        @Volatile
        private var ended = false

        override val context: SpanContext by lazy {
            val ctx = Context.current().with(span)
            val map = HashMap<String, String>(2)
            propagator.inject(ctx, map, MAP_SETTER)
            OtelSpanContext(map, ctx)
        }

        override fun setAttribute(key: String, value: String): Span = also { span.setAttribute(key, value) }
        override fun setAttribute(key: String, value: Long): Span = also { span.setAttribute(key, value) }
        override fun setAttribute(key: String, value: Double): Span = also { span.setAttribute(key, value) }
        override fun setAttribute(key: String, value: Boolean): Span = also { span.setAttribute(key, value) }

        override fun setError(message: String?): Span = also {
            if (message != null) span.setStatus(StatusCode.ERROR, message) else span.setStatus(StatusCode.ERROR)
        }

        override fun recordException(throwable: Throwable): Span = also {
            span.recordException(throwable)
            span.setStatus(StatusCode.ERROR, throwable.message ?: throwable.javaClass.simpleName)
        }

        override fun end() {
            if (ended) return
            ended = true
            span.end()
        }
    }

    private class TracerImpl(
        otel: OpenTelemetry,
        private val otelTracer: OtelTracer
    ) : Tracer {
        private val propagator: TextMapPropagator = otel.propagators.textMapPropagator

        override fun startSpan(
            name: String,
            parent: SpanContext?,
            attributes: Map<String, String>
        ): Span {
            val parentContext = when {
                parent == null -> Context.current()
                parent.extra is Context -> parent.extra as Context
                else -> propagator.extract(Context.current(), parent.headers, MAP_GETTER)
            }

            val builder = otelTracer.spanBuilder(name)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext)
            attributes.forEach { (k, v) -> builder.setAttribute(k, v) }

            return SpanImpl(builder.startSpan(), propagator)
        }

        override fun inject(context: SpanContext): Map<String, String> {
            val ctx = context.extra as? Context ?: return context.headers
            val map = HashMap<String, String>(2)
            propagator.inject(ctx, map, MAP_SETTER)
            return map
        }

        override fun extract(headers: Map<String, String>): SpanContext? {
            val extracted = propagator.extract(Context.root(), headers, MAP_GETTER)
            // If extraction produced no remote span context, return null.
            val sc = OtelSpan.fromContext(extracted).spanContext
            if (!sc.isValid) return null
            val carrier = HashMap<String, String>(2)
            propagator.inject(extracted, carrier, MAP_SETTER)
            return OtelSpanContext(carrier, extracted)
        }
    }

    companion object {
        internal val MAP_SETTER: TextMapSetter<MutableMap<String, String>> =
            TextMapSetter { carrier, key, value -> carrier?.put(key, value) }

        internal val MAP_GETTER: TextMapGetter<Map<String, String>> = object : TextMapGetter<Map<String, String>> {
            override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys
            override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
        }
    }
}
