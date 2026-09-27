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

package dev.cubxity.plugins.metrics.bukkit.util

enum class BukkitPlatform {
    Bukkit,
    Paper,

    /**
     * Folia and its forks (Canvas, ...): a regionized server with no single
     * main thread. `Bukkit.getScheduler()` throws on these servers, and the
     * global tick events never fire, so the plugin uses the global region
     * scheduler and collects per-region tick data instead.
     *
     * ShreddedPaper is detected as Folia too: it ships an empty
     * `io.papermc.paper.threadedregions.RegionizedServer` so that Folia
     * plugins use its Folia schedulers. It has no `ServerLevel.regioniser`,
     * so code on this path must not assume Folia's internals are present;
     * see `RegioniserAccess`.
     */
    Folia;

    companion object {
        val current: BukkitPlatform by lazy {
            when {
                classExists("io.papermc.paper.threadedregions.RegionizedServer") -> Folia
                classExists("com.destroystokyo.paper.event.server.ServerTickStartEvent") -> Paper
                else -> Bukkit
            }
        }
    }
}
