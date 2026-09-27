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

package dev.cubxity.plugins.metrics.bukkit.metric.tracker

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reads ShreddedPaper's `optimizations.maximum-trackers-per-entity` through
 * reflection, because the value has no API and the plugin compiles against
 * paper-api alone. The shape it reads, verified with javap against
 * shreddedpaper-1.21.11 build 42:
 *
 *   io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration.get()
 *       .optimizations.maximumTrackersPerEntity
 *
 * Both fields are public, and `get()` is a public static method that
 * `ChunkMap.TrackedEntity.updatePlayersLimitedAndOrdered` itself calls for the
 * same value, so the two always agree.
 *
 * Every other server returns 0, which means "no cap". Paper has no such option,
 * so a 0 there is the truth and not a failure. A rename in a later ShreddedPaper
 * also gives 0, and only the cap gauge and the cap binding gauge go quiet; the
 * churn counters and the tracker distribution do not depend on it.
 */
object TrackerCap {
    private const val CONFIGURATION_CLASS = "io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration"

    private data class Accessor(val get: Method, val optimizations: Field, val cap: Field)

    private val accessor: Accessor? by lazy { resolve() }

    /**
     * The configured cap, or 0 when the server has none. The value is read on
     * every collection, so a config reload arrives without a plugin restart.
     */
    fun value(): Int {
        val accessor = accessor ?: return 0
        return try {
            val configuration = accessor.get.invoke(null) ?: return 0
            val optimizations = accessor.optimizations.get(configuration) ?: return 0
            accessor.cap.getInt(optimizations)
        } catch (_: ReflectiveOperationException) {
            0
        }
    }

    private fun resolve(): Accessor? = try {
        val configuration = Class.forName(CONFIGURATION_CLASS)
        val optimizations = configuration.getField("optimizations")
        Accessor(
            configuration.getMethod("get"),
            optimizations,
            optimizations.type.getField("maximumTrackersPerEntity")
        )
    } catch (_: ReflectiveOperationException) {
        null
    }
}
