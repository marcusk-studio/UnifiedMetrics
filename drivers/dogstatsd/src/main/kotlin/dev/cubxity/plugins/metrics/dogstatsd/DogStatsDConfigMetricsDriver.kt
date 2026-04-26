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

package dev.cubxity.plugins.metrics.dogstatsd

import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.metric.DistributionSink
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import dev.cubxity.plugins.metrics.api.metric.data.CounterMetric
import dev.cubxity.plugins.metrics.api.metric.data.GaugeMetric
import dev.cubxity.plugins.metrics.api.metric.data.HistogramMetric
import dev.cubxity.plugins.metrics.api.metric.data.Labels
import dev.cubxity.plugins.metrics.api.metric.data.Metric
import dev.cubxity.plugins.metrics.api.util.fastForEach
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.system.measureTimeMillis
import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;
import jnr.unixsocket.UnixSocketAddress
import java.io.File
import java.util.*

class DogStatsDConfigMetricsDriver(private val api: UnifiedMetrics, private val config: DogStatsDConfig) : MetricsDriver, DistributionSink {
    private val coroutineScope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    private var statsdClient: StatsDClient? = null

    override fun initialize() {
        val builder = NonBlockingStatsDClientBuilder()

        val url = System.getenv("DD_DOGSTATSD_URL")
        val host = System.getenv("DD_DOGSTATSD_HOST")

        when {
            url != null && url.startsWith("unix://") -> {
                val socketPath = url.removePrefix("unix://")
                builder.addressLookup { UnixSocketAddress(File(socketPath)) }
            }
            host != null -> builder.hostname(host)
            else -> builder.hostname(config.host)
        }

        val entityId = System.getenv("DD_ENTITY_ID")
        if (entityId != null) {
            builder.entityID(entityId)
        }

        statsdClient = builder.build()
        scheduleTasks()
    }

    override fun close() {
        coroutineScope.cancel()
        statsdClient?.close()
        statsdClient = null
    }

    override fun recordDistribution(name: String, value: Double, labels: Labels) {
        val tags = mutableListOf<String>()
        for (entry in labels.entries) {
            tags.add("${entry.key}:${entry.value}")
        }
        addDataDogInternalTags(tags)
        statsdClient?.distribution(name, value, *tags.toTypedArray())
    }

    private fun scheduleTasks() {
        val interval = 1000

        coroutineScope.launch {
            while (true) {
                val time = measureTimeMillis {
                    try {
                        val metrics = api.metricsManager.collect()
                        writeMetrics(metrics)
                    } catch (error: Throwable) {
                        api.logger.severe("An error occurred whilst writing samples to DataDog", error)
                    }
                }
                delay(max(0, interval - time))
            }
        }
    }

    private fun writeMetrics(metrics: List<Metric>) {
        metrics.fastForEach { metric ->
            val intMutableList: MutableList<String> = mutableListOf<String>()
            for (entry in metric.labels.entries) {
                intMutableList.add(entry.key + ":" + entry.value);
            }
            addDataDogInternalTags(intMutableList)
            when (metric) {
                is GaugeMetric -> {
                    statsdClient?.gauge(metric.name, metric.value, *intMutableList.toTypedArray())
                }
                is CounterMetric -> {
                    // Counters in UnifiedMetrics are cumulative totals (e.g. total threads started),
                    // not deltas. Use gauge (|g|) to report the absolute value; Datadog can derive
                    // rate() from it. Using count (|c|) would treat the cumulative total as
                    // an increment, inflating the value on every flush.
                    statsdClient?.gauge(metric.name, metric.value, *intMutableList.toTypedArray())
                }
                is HistogramMetric -> {
                    // For histograms, send only the aggregate statistics (sum and count) as gauges
                    // The individual distribution values are sent via recordDistribution() when observed
                    statsdClient?.gauge("${metric.name}.sum", metric.sampleSum, *intMutableList.toTypedArray())
                    statsdClient?.gauge("${metric.name}.count", metric.sampleCount, *intMutableList.toTypedArray())
                }
            }
        }
    }

    private fun addDataDogInternalTags(intMutableList: MutableList<String>) {
        val tagDetails: String = System.getenv("DD_DOGSTATSD_TAGS") ?: return
        for (tag in tagDetails.split("\\s+".toRegex())) {
            if (tag.contains(":")) {
                intMutableList.add(tag)
            }
        }
    }
}