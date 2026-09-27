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

package dev.cubxity.plugins.metrics.common.metric

object Metrics {
    object Events {
        const val Login = "minecraft_events_login_total"
        const val Join = "minecraft_events_join_total"
        const val Quit = "minecraft_events_quit_total"
        const val Chat = "minecraft_events_chat_total"
        const val Ping = "minecraft_events_ping_total"
    }

    object Server {
        const val Plugins = "minecraft_plugins"
        const val PlayersCount = "minecraft_players_count"
        const val PlayersMax = "minecraft_players_max"
        const val TickDurationSeconds = "minecraft_tick_duration_seconds"
        const val WorldEntitiesCount = "minecraft_world_entities_count"
        const val WorldPlayersCount = "minecraft_world_players_count"
        const val WorldLoadedChunks = "minecraft_world_loaded_chunks"
    }

    /**
     * Entity tracker pairs: which players receive updates about which entity.
     * A rank based cap on the trackers of one entity (ShreddedPaper's
     * `maximum-trackers-per-entity`) rebuilds that set on a fixed period, so the
     * players near the rank boundary lose the pair and get it again. The client
     * then drops the entity and adds it at its new position, which looks like a
     * jump. Tick duration stays flat through it, so these are the only server
     * side signal of the condition.
     */
    object Tracker {
        const val PairingsAdded = "minecraft_tracker_pairings_added_total"
        const val PairingsRemoved = "minecraft_tracker_pairings_removed_total"
        const val PairingsLive = "minecraft_tracker_pairings_live"
        const val TrackedEntities = "minecraft_tracker_tracked_entities"
        const val TrackersPerEntity = "minecraft_tracker_trackers_per_entity"
        const val TrackersPerEntityMax = "minecraft_tracker_trackers_per_entity_max"
        const val CapBindingEntities = "minecraft_tracker_cap_binding_entities"
        const val Cap = "minecraft_tracker_cap"
    }

    /**
     * Folia and its forks tick each region on its own thread, so the single
     * server tick has no meaning there and these replace it.
     */
    object RegionizedServer {
        const val RegionCount = "minecraft_regionized_region_count"
        const val RegionTick = "minecraft_regionized_region_tick_total"
        const val RegionEntitiesCount = "minecraft_regionized_region_entities_count"
        const val RegionPlayersCount = "minecraft_regionized_region_players_count"
        const val RegionChunksCount = "minecraft_regionized_region_chunks_count"
    }
}