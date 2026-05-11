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

apply(plugin = "kotlinx-serialization")

dependencies {
    compileOnly(project(":unifiedmetrics-api"))

    val otelVersion = "1.38.0"
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk-trace:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-common:$otelVersion")
    // gRPC/HTTP transport for the OTLP exporter. Without this on the runtime
    // classpath, the exporter logs `No sender available` and silently drops spans.
    implementation("io.opentelemetry:opentelemetry-exporter-sender-okhttp:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-context:$otelVersion")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.25.0-alpha")

    testImplementation(project(":unifiedmetrics-api"))
    testImplementation(kotlin("test"))
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:$otelVersion")
}

tasks.test {
    useJUnitPlatform()
}
