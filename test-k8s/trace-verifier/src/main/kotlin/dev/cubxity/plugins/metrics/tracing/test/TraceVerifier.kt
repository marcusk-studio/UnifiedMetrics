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

package dev.cubxity.plugins.metrics.tracing.test

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.logging.Logger
import dev.cubxity.plugins.metrics.api.metric.MetricsManager
import dev.cubxity.plugins.metrics.api.platform.Platform
import dev.cubxity.plugins.metrics.api.platform.PlatformType
import dev.cubxity.plugins.metrics.api.tracing.TracingChannels
import dev.cubxity.plugins.metrics.api.tracing.TracingManager
import dev.cubxity.plugins.metrics.tracing.otel.OtelConfig
import dev.cubxity.plugins.metrics.tracing.otel.OtelTracingDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * End-to-end trace verifier for the UnifiedMetrics OTel tracing driver.
 *
 * Simulates the full proxy→backend tracing flow in two separate
 * [OtelTracingDriver] instances (mimicking two JVMs) sharing one Jaeger backend:
 *
 *   1. Proxy emits `player.connection` (root) → `player.login`,
 *      `player.backend_connect`, `player.server_session`.
 *   2. Headers are injected via the Tracer and serialized through
 *      [TracingChannels.encode] / [TracingChannels.decode] (the wire format).
 *   3. Backend extracts the context and emits `player.backend.session` as a
 *      remote-parented child.
 *   4. After flushing, the program queries Jaeger's HTTP API to assert that
 *      both services reported spans for the trace and that the backend span
 *      links back to the proxy trace id.
 */
fun main() {
    val otlpEndpoint = System.getProperty("otlp.endpoint")
        ?: System.getenv("OTLP_ENDPOINT")
        ?: "http://localhost:4317"
    val jaegerQuery = System.getProperty("jaeger.query")
        ?: System.getenv("JAEGER_QUERY")
        ?: "http://localhost:16686"
    val protocol = System.getProperty("otlp.protocol")
        ?: System.getenv("OTLP_PROTOCOL")
        ?: "grpc"

    println("[verifier] otlp.endpoint = $otlpEndpoint")
    println("[verifier] jaeger.query  = $jaegerQuery")
    println("[verifier] otlp.protocol = $protocol")

    val proxyDriver = OtelTracingDriver(
        FakeApi("unifiedmetrics-proxy"),
        OtelConfig(
            serviceName = "unifiedmetrics-proxy",
            endpoint = otlpEndpoint,
            protocol = protocol
        )
    ).also { it.initialize() }

    val backendDriver = OtelTracingDriver(
        FakeApi("unifiedmetrics-backend"),
        OtelConfig(
            serviceName = "unifiedmetrics-backend",
            endpoint = otlpEndpoint,
            protocol = protocol
        )
    ).also { it.initialize() }

    val proxy = proxyDriver.tracer
    val backend = backendDriver.tracer

    // Simulate proxy lifecycle.
    val connection = proxy.startSpan(
        "player.connection",
        attributes = mapOf("player.username" to "Notch")
    )
    val login = proxy.startSpan("player.login", parent = connection.context)
    Thread.sleep(20)
    login.end()

    val backendConnect = proxy.startSpan(
        "player.backend_connect",
        parent = connection.context,
        attributes = mapOf("target.server" to "lobby")
    )
    Thread.sleep(20)
    backendConnect.end()

    val serverSession = proxy.startSpan(
        "player.server_session",
        parent = connection.context,
        attributes = mapOf("server.name" to "lobby")
    )

    // Wire transfer: proxy → backend via TracingChannels payload.
    val proxyHeaders = proxy.inject(serverSession.context)
    val payload = requireNotNull(TracingChannels.encode(proxyHeaders)) { "encode returned null" }
    val backendHeaders = requireNotNull(TracingChannels.decode(payload)) { "decode returned null" }
    require(proxyHeaders["traceparent"] == backendHeaders["traceparent"]) {
        "traceparent did not survive wire encode/decode"
    }
    println("[verifier] traceparent on wire = ${backendHeaders["traceparent"]}")

    val parent = requireNotNull(backend.extract(backendHeaders)) {
        "backend tracer failed to extract context"
    }
    val backendSpan = backend.startSpan(
        "player.backend.session",
        parent = parent,
        attributes = mapOf(
            "player.username" to "Notch",
            "server.name" to "lobby"
        )
    )
    Thread.sleep(50)
    backendSpan.end()
    serverSession.end()
    connection.end()

    // The proxy traceparent looks like: 00-<traceId>-<spanId>-<flags>.
    val traceId = backendHeaders["traceparent"]!!.split("-")[1]
    println("[verifier] expected traceId = $traceId")

    // Force-flush and shut both SDKs down so the BatchSpanProcessor exports.
    proxyDriver.close()
    backendDriver.close()

    // Poll Jaeger for the trace.
    val ok = pollJaeger(jaegerQuery, traceId)
    if (ok) {
        println("[verifier] PASS: trace observed in Jaeger with both services and expected operations")
        kotlin.system.exitProcess(0)
    } else {
        println("[verifier] FAIL: trace not found in Jaeger or did not contain expected spans")
        kotlin.system.exitProcess(1)
    }
}

private fun pollJaeger(base: String, traceId: String): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
    val url = URL("$base/api/traces/$traceId")
    val expected = setOf(
        "player.connection",
        "player.login",
        "player.backend_connect",
        "player.server_session",
        "player.backend.session"
    )
    val expectedServices = setOf("unifiedmetrics-proxy", "unifiedmetrics-backend")

    while (System.nanoTime() < deadline) {
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val opsFound = expected.filter { body.contains("\"operationName\":\"$it\"") }.toSet()
                val servicesFound = expectedServices.filter { body.contains("\"serviceName\":\"$it\"") }.toSet()
                println("[verifier] jaeger ops=$opsFound services=$servicesFound")
                if (opsFound == expected && servicesFound == expectedServices) {
                    return true
                }
            } else {
                println("[verifier] jaeger query http=$code")
            }
            conn.disconnect()
        } catch (e: Throwable) {
            println("[verifier] jaeger query error: ${e.message}")
        }
        Thread.sleep(2000)
    }
    return false
}

private class FakeApi(private val name: String) : UnifiedMetrics {
    override val platform: Platform = object : Platform {
        override val type: PlatformType = PlatformType.Velocity
    }
    override val serverName: String = name
    override val logger: Logger = object : Logger {
        override fun info(message: String) = println("[$name] INFO  $message")
        override fun warn(message: String) = println("[$name] WARN  $message")
        override fun warn(message: String, error: Throwable) {
            println("[$name] WARN  $message"); error.printStackTrace()
        }
        override fun severe(message: String) = println("[$name] SEVERE $message")
        override fun severe(message: String, error: Throwable) {
            println("[$name] SEVERE $message"); error.printStackTrace()
        }
    }
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default
    override val metricsManager: MetricsManager
        get() = error("metrics not used by verifier")
    override val tracingManager: TracingManager
        get() = error("tracingManager not used by verifier")
}
