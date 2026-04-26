/*
 * Diagnostic test: capture what DogStatsD actually sends for each metric type,
 * simulating the exact flow from writeMetrics and recordDistribution.
 */

package dev.cubxity.plugins.metrics.dogstatsd

import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import kotlin.test.assertTrue
import kotlin.test.assertContains

class MetricFormatTest {

    private fun capturePackets(action: (com.timgroup.statsd.StatsDClient) -> Unit): List<String> {
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port = socket.localPort
        socket.soTimeout = 3000

        val client = NonBlockingStatsDClientBuilder()
            .hostname("127.0.0.1")
            .port(port)
            .build()

        try {
            action(client)
            Thread.sleep(1000) // let the non-blocking client flush

            val packets = mutableListOf<String>()
            try {
                while (true) {
                    val buf = ByteArray(8192)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    // DogStatsD may batch multiple metrics in one datagram, split on newline
                    data.split("\n").filter { it.isNotBlank() }.forEach { packets.add(it) }
                }
            } catch (_: java.net.SocketTimeoutException) {
                // done receiving
            }
            return packets
        } finally {
            client.close()
            socket.close()
        }
    }

    /**
     * Verify gauge format: name:value|g|#tags
     */
    @Test
    fun `gauge metric produces correct DogStatsD wire format`() {
        val packets = capturePackets { client ->
            client.gauge("minecraft_player_count", 42.0, "server:hub")
        }
        println("Gauge packets: $packets")
        assertTrue(packets.isNotEmpty(), "Should receive gauge packets")
        val gaugePacket = packets.find { it.contains("minecraft_player_count") }!!
        // Should be: minecraft_player_count:42|g|#server:hub
        assertContains(gaugePacket, "|g", message = "Should use gauge type |g|")
        assertContains(gaugePacket, "42", message = "Should contain the value 42")
        assertTrue(!gaugePacket.startsWith("statsd."), "Should not have 'statsd.' prefix")
    }

    /**
     * Verify counter format: now sent as gauge (|g|) since values are cumulative totals.
     *
     * Previously this used |c| (delta) which caused massive inflation because
     * the Counter collector returns cumulative totals, not deltas.
     */
    @Test
    fun `counter metric sent as gauge for cumulative values`() {
        val packets = capturePackets { client ->
            // Simulating the FIXED writeMetrics: CounterMetric.value sent as gauge
            client.gauge("jvm_threads_started_total", 150.0, "server:hub")
        }
        println("Counter packets: $packets")
        val counterPacket = packets.find { it.contains("jvm_threads_started_total") }!!
        // Should now be: jvm_threads_started_total:150|g|#server:hub
        assertContains(counterPacket, "|g", message = "Should use gauge type |g| for cumulative counters")
        assertTrue(!counterPacket.contains("|c"), "Should NOT use count type |c| for cumulative values")
    }

    /**
     * Verify histogram .sum/.count format sent as gauges.
     *
     * The sum and count are cumulative values, and gauge (|g|) is correct for
     * reporting an absolute value — Datadog can derive rate from gauge.
     */
    @Test
    fun `histogram sum and count produce correct DogStatsD wire format`() {
        val packets = capturePackets { client ->
            // Simulating what writeMetrics does for HistogramMetric
            // sampleSum is cumulative total of all observed values (e.g. 45.2 seconds)
            // sampleCount is cumulative count of observations (e.g. 1000 ticks)
            client.gauge("minecraft_tick_duration_seconds.sum", 45.2, "server:hub")
            client.gauge("minecraft_tick_duration_seconds.count", 1000.0, "server:hub")
        }
        println("Histogram aggregate packets: $packets")
        assertTrue(packets.any { it.contains("tick_duration_seconds.sum") }, "Should have .sum packet")
        assertTrue(packets.any { it.contains("tick_duration_seconds.count") }, "Should have .count packet")
        // These are correctly sent as gauge |g| — cumulative values reported as absolute
        // No 'statsd.' prefix
        val sumPacket = packets.find { it.contains("tick_duration_seconds.sum") }!!
        assertTrue(!sumPacket.startsWith("statsd."), "Should not have 'statsd.' prefix")
    }

    /**
     * Verify distribution format (real-time per-tick path): name:value|d|#tags
     */
    @Test
    fun `distribution metric produces correct DogStatsD wire format`() {
        val packets = capturePackets { client ->
            // Simulating what recordDistribution does for each tick
            // Value is in seconds (e.g. 0.042 = 42ms tick)
            client.distribution("minecraft_tick_duration_seconds", 0.042, "server:hub")
        }
        println("Distribution packets: $packets")
        val distPacket = packets.find { it.contains("tick_duration_seconds") }!!
        // Should be: minecraft_tick_duration_seconds:0.042|d|#server:hub
        assertContains(distPacket, "|d", message = "Should use distribution type |d|")
        assertContains(distPacket, "0.042", message = "Should contain the value 0.042 (seconds)")
        assertTrue(!distPacket.startsWith("statsd."), "Should not have 'statsd.' prefix")
    }
}
