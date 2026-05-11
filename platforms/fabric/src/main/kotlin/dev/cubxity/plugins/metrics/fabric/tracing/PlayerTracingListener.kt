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

package dev.cubxity.plugins.metrics.fabric.tracing

import dev.cubxity.plugins.metrics.api.tracing.Span
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingChannels
import dev.cubxity.plugins.metrics.fabric.UnifiedMetricsFabricPlugin
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerTracingListener(
    private val plugin: UnifiedMetricsFabricPlugin
) {
    private val activeSpans: MutableMap<UUID, Span> = ConcurrentHashMap()

    private val tracer: Tracer
        get() = plugin.apiProvider.tracingManager.tracer

    fun register() {
        // Register the custom payload type once. Idempotent across re-enables.
        try {
            PayloadTypeRegistry.playC2S().register(TracePayload.ID, TracePayload.CODEC)
        } catch (_: IllegalArgumentException) {
            // already registered
        }

        ServerPlayNetworking.registerGlobalReceiver(TracePayload.ID) { payload, context ->
            try {
                val headers = TracingChannels.decode(payload.bytes) ?: return@registerGlobalReceiver
                val parent = tracer.extract(headers) ?: return@registerGlobalReceiver

                val player = context.player()
                activeSpans.remove(player.uuid)?.end()

                val span = tracer.startSpan(
                    "player.backend.session",
                    parent = parent,
                    attributes = mapOf(
                        "player.username" to player.name.string,
                        "player.uuid" to player.uuid.toString(),
                        "server.name" to plugin.apiProvider.serverName
                    )
                )
                activeSpans[player.uuid] = span
            } catch (error: Throwable) {
                plugin.bootstrap.logger.warn("UnifiedMetrics tracing error", error)
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            try {
                activeSpans.remove(handler.player.uuid)?.end()
            } catch (error: Throwable) {
                plugin.bootstrap.logger.warn("UnifiedMetrics tracing error", error)
            }
        }
    }

    fun dispose() {
        activeSpans.values.forEach {
            try { it.end() } catch (_: Throwable) {}
        }
        activeSpans.clear()
    }
}

/**
 * Custom-payload wrapper for the `unifiedmetrics:trace` channel. The byte
 * payload is identical to the [TracingChannels] wire format (W3C `traceparent`
 * + optional `tracestate`).
 */
class TracePayload(val bytes: ByteArray) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<TracePayload> = CustomPayload.Id(
            Identifier.of(TracingChannels.NAMESPACE, TracingChannels.NAME)
        )

        val CODEC: PacketCodec<PacketByteBuf, TracePayload> = PacketCodec.of(
            { value, buf -> buf.writeByteArray(value.bytes) },
            { buf -> TracePayload(buf.readByteArray()) }
        )
    }
}
