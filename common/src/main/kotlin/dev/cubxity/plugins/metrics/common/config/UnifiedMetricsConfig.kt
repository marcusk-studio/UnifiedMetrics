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

package dev.cubxity.plugins.metrics.common.config

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedMetricsConfig(
    val server: UnifiedMetricsServerConfig = UnifiedMetricsServerConfig(),
    val metrics: UnifiedMetricsMetricsConfig = UnifiedMetricsMetricsConfig(),
    val tracing: UnifiedMetricsTracingConfig = UnifiedMetricsTracingConfig()
)

@Serializable
data class UnifiedMetricsServerConfig(
    val name: String = env("SERVER_NAME", "global")
)

@Serializable
data class UnifiedMetricsMetricsConfig(
    val enabled: Boolean = true,
    val driver: String = "prometheus",
    val collectors: UnifiedMetricsCollectorsConfig = UnifiedMetricsCollectorsConfig()
)

@Serializable
data class UnifiedMetricsCollectorsConfig(
    val systemGc: Boolean = true,
    val systemMemory: Boolean = true,
    val systemProcess: Boolean = true,
    val systemThread: Boolean = true,
    val server: Boolean = true,
    val world: Boolean = true,
    val tick: Boolean = true,
    val events: Boolean = true,
    // Per-region metrics on Folia and its forks; ignored on other servers.
    val regionizedServer: Boolean = true,
    /**
     * Entity tracker pairs and their churn. Needs the Paper track events,
     * present in paper-api 1.20.2 and later, so Spigot and older Paper skip it.
     *
     * The events fire once per pair the server makes or breaks, which is often:
     * a crowd of several hundred players makes tens of thousands per second.
     * Paper builds the event object only while a plugin listens, so this toggle
     * decides that cost. Each event then costs one hash lookup and one CAS here.
     */
    val tracker: Boolean = true
)

@Serializable
data class UnifiedMetricsTracingConfig(
    val enabled: Boolean = true,
    val driver: String = "otel",
    /**
     * Whether to propagate trace context from the proxy to backend Minecraft
     * servers via the `unifiedmetrics:trace` plugin-message channel.
     */
    val propagation: Boolean = true,
    val spans: UnifiedMetricsTracingSpansConfig = UnifiedMetricsTracingSpansConfig(),
    val attributes: UnifiedMetricsTracingAttributesConfig = UnifiedMetricsTracingAttributesConfig()
)

@Serializable
data class UnifiedMetricsTracingSpansConfig(
    /** `player.login` (PreLogin -> ChooseInitialServer). */
    val login: Boolean = true,
    /** `player.server_connect` (ServerPreConnect -> ServerConnected). */
    val serverConnect: Boolean = true,
    /** `player.disconnect` — captures teardown, disconnect reason, and session duration. */
    val disconnect: Boolean = true,
    /**
     * `player.world_ready` (backend only) — measures time from trace-context
     * arrival until the player's first movement, approximating how long the
     * world takes to become playable after connecting to a server.
     */
    val worldReady: Boolean = true
)

@Serializable
data class UnifiedMetricsTracingAttributesConfig(
    /**
     * Whether to attach the player's remote IP as the `player.remote_addr`
     * attribute. Disabled by default for privacy.
     */
    val includeRemoteAddr: Boolean = false
)

private fun env(name: String, default: String): String =
    System.getenv("UNIFIEDMETRICS_$name") ?: default
