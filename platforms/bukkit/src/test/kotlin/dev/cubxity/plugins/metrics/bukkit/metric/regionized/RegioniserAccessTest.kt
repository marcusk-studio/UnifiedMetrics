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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RegioniserAccessTest {
    private class RecordingLogger : Logger {
        val warnings = ArrayList<String>()
        override fun info(message: String) = Unit
        override fun warn(message: String) { warnings.add(message) }
        override fun warn(message: String, error: Throwable) { warnings.add(message) }
        override fun severe(message: String) = error("unexpected severe: $message")
        override fun severe(message: String, error: Throwable) = error("unexpected severe: $message")
    }

    /** Stands in for a Folia `ServerLevel`. */
    @Suppress("unused")
    class FoliaLevel {
        @JvmField
        val regioniser: Any = Object()
    }

    /** Stands in for a ShreddedPaper or Paper `ServerLevel`: no such field. */
    class PlainLevel

    @Test
    fun `a missing field returns null, does not throw, and warns once`() {
        val logger = RecordingLogger()
        val access = RegioniserAccess(logger, "ShreddedPaper 1.21.11-test")
        val level = PlainLevel()

        repeat(3) {
            assertNull(access.regioniser(level))
        }

        assertFalse(access.isAvailable)
        assertEquals(1, logger.warnings.size, "one warning for three misses")
        val warning = logger.warnings.single()
        assertTrue(warning.contains("ShreddedPaper 1.21.11-test"), "the warning names the platform: $warning")
        assertTrue(warning.contains("regioniser"), "the warning names the field: $warning")
        assertTrue(warning.contains(PlainLevel::class.java.name), "the warning names the handle class: $warning")
    }

    @Test
    fun `a present field is returned and nothing is logged`() {
        val logger = RecordingLogger()
        val access = RegioniserAccess(logger, "Folia test")
        val level = FoliaLevel()

        assertSame(level.regioniser, access.regioniser(level))
        assertSame(level.regioniser, access.regioniser(level), "the cached field still resolves")
        assertTrue(access.isAvailable)
        assertTrue(logger.warnings.isEmpty())
    }
}
