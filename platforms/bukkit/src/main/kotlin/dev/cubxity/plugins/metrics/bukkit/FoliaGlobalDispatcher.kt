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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.isActive
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * Coroutine dispatcher for Folia and its forks. Folia has no main thread and
 * `Bukkit.getScheduler()` throws `UnsupportedOperationException`, so work that
 * [BukkitDispatcher] would put on the main thread goes to the global region
 * scheduler instead. The global region owns the world list, the player list
 * and the other server-wide state the collectors read.
 *
 * The scheduler API is part of paper-api since 1.20, so this compiles against
 * the same artifact as the rest of the platform; the class is only
 * instantiated when [dev.cubxity.plugins.metrics.bukkit.util.BukkitPlatform]
 * detects a regionized server.
 */
@OptIn(InternalCoroutinesApi::class)
class FoliaGlobalDispatcher(private val plugin: JavaPlugin) : CoroutineDispatcher(), Delay {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!context.isActive) {
            return
        }
        plugin.server.globalRegionScheduler.execute(plugin, block)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        // The scheduler rejects a delay below one tick.
        val ticks = max(1L, ceil(timeMillis / MILLIS_PER_TICK).toLong())
        val task = plugin.server.globalRegionScheduler.runDelayed(
            plugin,
            Consumer<ScheduledTask> { continuation.apply { resumeUndispatched(Unit) } },
            ticks
        )
        continuation.invokeOnCancellation { task.cancel() }
    }

    private companion object {
        const val MILLIS_PER_TICK = 50.0
    }
}
