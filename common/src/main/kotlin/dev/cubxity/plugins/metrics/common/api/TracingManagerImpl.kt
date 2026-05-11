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

package dev.cubxity.plugins.metrics.common.api

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.cubxity.plugins.metrics.api.tracing.NoopTracer
import dev.cubxity.plugins.metrics.api.tracing.Tracer
import dev.cubxity.plugins.metrics.api.tracing.TracingDriver
import dev.cubxity.plugins.metrics.api.tracing.TracingDriverFactory
import dev.cubxity.plugins.metrics.api.tracing.TracingManager
import dev.cubxity.plugins.metrics.common.plugin.UnifiedMetricsPlugin
import java.nio.file.Files
import kotlin.system.measureTimeMillis

class TracingManagerImpl(private val plugin: UnifiedMetricsPlugin) : TracingManager {
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    private val driverDirectory = plugin.bootstrap.configDirectory.resolve("tracing")

    private val tracingDrivers: MutableMap<String, TracingDriverFactory<Any>> = HashMap()

    private var shouldInitialize: Boolean = false
    private var _driver: TracingDriver? = null

    override val isEnabled: Boolean
        get() = _driver != null

    override val driver: TracingDriver?
        get() = _driver

    override val tracer: Tracer
        get() = _driver?.tracer ?: NoopTracer

    override fun initialize() {
        if (!plugin.config.tracing.enabled) return
        shouldInitialize = true

        val driverName = plugin.config.tracing.driver
        val factory = tracingDrivers[driverName]

        Files.createDirectories(driverDirectory)

        if (factory !== null) {
            initializeDriver(driverName, factory)
        } else {
            plugin.bootstrap.logger.warn("Tracing driver '$driverName' not found. Tracing will be enabled when the driver is loaded.")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun registerDriver(name: String, factory: TracingDriverFactory<out Any>) {
        tracingDrivers[name] = factory as TracingDriverFactory<Any>

        if (shouldInitialize && _driver === null) {
            if (name == plugin.config.tracing.driver) {
                initializeDriver(name, factory)
            }
        }
    }

    override fun dispose() {
        shouldInitialize = false
        try {
            _driver?.close()
        } catch (error: Throwable) {
            plugin.bootstrap.logger.warn("An error occurred whilst closing tracing driver", error)
        }
        _driver = null
    }

    private fun initializeDriver(name: String, factory: TracingDriverFactory<Any>) {
        plugin.bootstrap.logger.info("Initializing tracing driver '$name'.")
        val time = measureTimeMillis {
            try {
                val file = driverDirectory.toFile().resolve("$name.yml")

                val serializer = factory.configSerializer
                val config = when {
                    file.exists() -> yaml.decodeFromString(serializer, file.readText())
                    else -> factory.defaultConfig
                }

                try {
                    file.writeText(yaml.encodeToString(serializer, config))
                } catch (exception: Exception) {
                    plugin.apiProvider.logger.severe("An error occurred whilst saving tracing driver config file ", exception)
                }

                val driver = factory.createDriver(plugin.apiProvider, config)
                driver.initialize()

                this._driver = driver
            } catch (error: Throwable) {
                plugin.apiProvider.logger.severe("An error occurred whilst initializing tracing driver $name", error)
                return
            }
        }
        plugin.bootstrap.logger.info("Tracing driver '$name' initialized ($time ms).")
    }
}
