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

package dev.cubxity.plugins.metrics.bukkit.metric.regionized

import dev.cubxity.plugins.metrics.api.logging.Logger
import java.lang.reflect.Field

/**
 * Resolves Folia's `ServerLevel.regioniser` field once and remembers the
 * answer.
 *
 * The plugin detects Folia by the class
 * `io.papermc.paper.threadedregions.RegionizedServer`. ShreddedPaper ships an
 * empty class under that name so that Folia plugins use its Folia schedulers,
 * but its `ServerLevel` has no `regioniser`: its regions are
 * `io.multipaper.shreddedpaper.region.LevelChunkRegion`, with no walk that
 * this collector can read. Before this class, the collector called
 * `getField("regioniser")` on every collection, the `NoSuchFieldException`
 * escaped through `MetricsManager.collect()`, and the DogStatsD driver dropped
 * every sample of every collector once per second, with a stack trace each
 * time.
 *
 * Now the first miss disables the access, logs one warning that names the
 * platform, and every later call returns `null` at the cost of one volatile
 * read. This class has no Bukkit dependency, so the unit test can drive it
 * with plain objects.
 */
class RegioniserAccess(private val logger: Logger, private val platformName: String) {
    @Volatile
    private var field: Field? = null

    @Volatile
    private var missing: Boolean = false

    /**
     * `true` until the field is found missing on a handle.
     */
    val isAvailable: Boolean
        get() = !missing

    /**
     * The `regioniser` of [handle], or `null` when the handle's class has no
     * such field. The lookup runs once per class of handle; the result is
     * cached and the first miss is logged once.
     */
    fun regioniser(handle: Any): Any? {
        if (missing) return null

        val resolved = field
        if (resolved != null && resolved.declaringClass.isInstance(handle)) {
            return resolved.get(handle)
        }

        val found = try {
            handle.javaClass.getField(FIELD_NAME)
        } catch (_: NoSuchFieldException) {
            missing = true
            logger.warn(
                "This server reports Folia (io.papermc.paper.threadedregions.ThreadedRegionizer is present), " +
                    "but ${handle.javaClass.name} has no '$FIELD_NAME' field on $platformName. " +
                    "It is not a Folia region server, so the regionizedServer collection reports nothing. " +
                    "Set metrics.collectors.regionizedServer to false to remove this warning."
            )
            return null
        }
        field = found
        return found.get(handle)
    }

    private companion object {
        const val FIELD_NAME = "regioniser"
    }
}
