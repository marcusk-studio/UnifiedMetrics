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

package dev.cubxity.plugins.metrics.bukkit

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.metric.DistributionSink
import dev.cubxity.plugins.metrics.bukkit.metric.DriverDistributionSink
import dev.cubxity.plugins.metrics.bukkit.bootstrap.UnifiedMetricsBukkitBootstrap
import dev.cubxity.plugins.metrics.bukkit.metric.events.EventsCollection
import dev.cubxity.plugins.metrics.bukkit.metric.latency.LatencyCollection
import dev.cubxity.plugins.metrics.bukkit.metric.regionized.FoliaRegionCollection
import dev.cubxity.plugins.metrics.bukkit.metric.server.ServerCollection
import dev.cubxity.plugins.metrics.bukkit.metric.tick.TickCollection
import dev.cubxity.plugins.metrics.bukkit.metric.tracker.TrackerCollection
import dev.cubxity.plugins.metrics.bukkit.metric.world.WorldCollection
import dev.cubxity.plugins.metrics.bukkit.tracing.PlayerTracingListener
import dev.cubxity.plugins.metrics.bukkit.util.BukkitPlatform
import dev.cubxity.plugins.metrics.bukkit.util.classExists
import dev.cubxity.plugins.metrics.core.plugin.CoreUnifiedMetricsPlugin
import org.bukkit.plugin.ServicePriority
import java.util.concurrent.Executors

class UnifiedMetricsBukkitPlugin(
        override val bootstrap: UnifiedMetricsBukkitBootstrap
) : CoreUnifiedMetricsPlugin() {
    private val executor = Executors.newScheduledThreadPool(1)
    private var tracingListener: PlayerTracingListener? = null

    override fun disable() {
        executor.shutdownNow()
        super.disable()
    }

    override fun registerPlatformService(api: UnifiedMetrics) {
        bootstrap.server.servicesManager.register(UnifiedMetrics::class.java, api, bootstrap, ServicePriority.Normal)
    }

    override fun registerPlatformMetrics() {
        super.registerPlatformMetrics()

        apiProvider.metricsManager.apply {
            // The driver does not exist yet: enable() registers the platform
            // metrics before it initializes the metrics manager, so a
            // `driver as? DistributionSink` read here is always null and no
            // histogram sample would ever reach the driver. The sink resolves
            // the driver on each record instead.
            val sink: DistributionSink = DriverDistributionSink(this)
            with(config.metrics.collectors) {
                if (server) registerCollection(ServerCollection(bootstrap))
                if (world) registerCollection(WorldCollection(bootstrap))
                // Folia ticks each region on its own thread and never fires the
                // global ServerTickEndEvent, so the single tick histogram would
                // stay empty there; the region collection carries the tick data.
                val regionized = BukkitPlatform.current == BukkitPlatform.Folia
                if (tick && !regionized) registerCollection(TickCollection(bootstrap, sink))
                if (events) registerCollection(EventsCollection(bootstrap))
                if (regionizedServer && regionized) registerCollection(FoliaRegionCollection(bootstrap))
                // The tracker collection listens for the Paper track events,
                // which Spigot and older Paper do not have.
                if (tracker && classExists("io.papermc.paper.event.player.PlayerTrackEntityEvent")) {
                    registerCollection(TrackerCollection(bootstrap, sink))
                }
                // Player ping on every platform; tick durations on Paper only,
                // which the collection decides for itself.
                if (latency) {
                    registerCollection(LatencyCollection(bootstrap, sink, latencyConnection))
                }
            }
        }
    }

    override fun registerPlatformTracing() {
        val listener = PlayerTracingListener(this)
        listener.register()
        tracingListener = listener
    }

    override fun disposePlatformTracing() {
        tracingListener?.dispose()
        tracingListener = null
    }
}