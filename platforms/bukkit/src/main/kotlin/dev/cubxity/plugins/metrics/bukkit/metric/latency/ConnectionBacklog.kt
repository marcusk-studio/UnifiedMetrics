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

package dev.cubxity.plugins.metrics.bukkit.metric.latency

import org.bukkit.entity.Player
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reads the bytes a player's connection has queued for the socket and not yet
 * written, through reflection, because the netty channel has no API and the
 * plugin compiles against paper-api alone. The chain it reads, verified with
 * javap against shreddedpaper-1.21.11, paper-1.21.11 build 132 and
 * netty-transport 4.2.7.Final (the netty Paper 1.21.11 bundles):
 *
 *   ((CraftPlayer) player).getHandle()          public method
 *       .connection                             public ServerGamePacketListenerImpl
 *       .connection                             public final net.minecraft.network.Connection
 *       .channel                                public io.netty.channel.Channel
 *       .unsafe().outboundBuffer()              Channel.Unsafe, interface methods
 *       .totalPendingWriteBytes()               getfield of a private volatile long
 *
 * Every step is a field read or a call that returns one, and the two that
 * matter for thread safety are volatile: `AbstractChannel.AbstractUnsafe
 * .outboundBuffer` and `ChannelOutboundBuffer.totalPendingSize`. Netty updates
 * the size with an `AtomicLongFieldUpdater` from the event loop; a read from
 * the collection thread takes no lock and touches no event loop state. This is
 * the same read that `Channel.bytesBeforeUnwritable()` does before it subtracts
 * from the high water mark.
 *
 * The names are Mojang names, which Paper runs with since 1.20.5 and Spigot
 * does not. The methods and interfaces on the netty side are public API since
 * netty 4.0. Resolution happens once; a rename leaves [isAvailable] false, the
 * collection logs that at startup, and the ping and tick metrics do not depend
 * on it.
 *
 * `outboundBuffer()` returns null once the channel has closed, and a player can
 * still be listed at that moment. That reads as -1, which the caller skips.
 */
object ConnectionBacklog {
    private const val CRAFT_PLAYER_CLASS = "org.bukkit.craftbukkit.entity.CraftPlayer"
    private const val CHANNEL_CLASS = "io.netty.channel.Channel"
    private const val UNSAFE_CLASS = "io.netty.channel.Channel\$Unsafe"
    private const val OUTBOUND_BUFFER_CLASS = "io.netty.channel.ChannelOutboundBuffer"

    private class Accessor(
        val handle: Method,
        val listener: Field,
        val connection: Field,
        val channel: Field,
        val unsafe: Method,
        val outboundBuffer: Method,
        val totalPendingWriteBytes: Method
    )

    private val accessor: Accessor? by lazy { resolve() }

    /** Whether every member of the chain resolved on this server. */
    val isAvailable: Boolean
        get() = accessor != null

    /**
     * Bytes queued on the player's channel and not yet written to the socket,
     * or -1 when the chain cannot be read for this player right now.
     */
    fun pendingBytes(player: Player): Long {
        val accessor = accessor ?: return -1
        return try {
            val handle = accessor.handle.invoke(player) ?: return -1
            val listener = accessor.listener.get(handle) ?: return -1
            val connection = accessor.connection.get(listener) ?: return -1
            val channel = accessor.channel.get(connection) ?: return -1
            val unsafe = accessor.unsafe.invoke(channel) ?: return -1
            val buffer = accessor.outboundBuffer.invoke(unsafe) ?: return -1
            accessor.totalPendingWriteBytes.invoke(buffer) as Long
        } catch (_: ReflectiveOperationException) {
            -1
        } catch (_: ClassCastException) {
            -1
        }
    }

    private fun resolve(): Accessor? = try {
        val handle = Class.forName(CRAFT_PLAYER_CLASS).getMethod("getHandle")
        val listener = handle.returnType.getField("connection")
        val connection = listener.type.getField("connection")
        val channel = connection.type.getField("channel")
        Accessor(
            handle,
            listener,
            connection,
            channel,
            Class.forName(CHANNEL_CLASS).getMethod("unsafe"),
            Class.forName(UNSAFE_CLASS).getMethod("outboundBuffer"),
            Class.forName(OUTBOUND_BUFFER_CLASS).getMethod("totalPendingWriteBytes")
        )
    } catch (_: ReflectiveOperationException) {
        null
    }
}
