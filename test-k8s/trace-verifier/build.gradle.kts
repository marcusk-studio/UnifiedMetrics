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

plugins {
    application
}

apply(plugin = "kotlinx-serialization")

dependencies {
    implementation(project(":unifiedmetrics-api"))
    implementation(project(":unifiedmetrics-tracing-otel"))

    val otelVersion = "1.38.0"
    runtimeOnly("io.opentelemetry:opentelemetry-api:$otelVersion")
    runtimeOnly("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
}

application {
    mainClass.set("dev.cubxity.plugins.metrics.tracing.test.TraceVerifierKt")
}

tasks.named<JavaExec>("run") {
    // allow passing system props via gradle -Pjaeger=...
    systemProperty("otlp.endpoint", System.getProperty("otlp.endpoint", System.getenv("OTLP_ENDPOINT") ?: "http://localhost:4317"))
    systemProperty("jaeger.query", System.getProperty("jaeger.query", System.getenv("JAEGER_QUERY") ?: "http://localhost:16686"))
}
