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

plugins {
    id("com.gradleup.shadow")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":unifiedmetrics-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        relocate("retrofit2", "dev.cubxity.plugins.metrics.libs.retrofit2")
        relocate("com.charleskorn", "dev.cubxity.plugins.metrics.libs.com.charleskorn")
        relocate("com.influxdb", "dev.cubxity.plugins.metrics.libs.com.influxdb")
        relocate("okhttp", "dev.cubxity.plugins.metrics.libs.okhttp")
        relocate("okio", "dev.cubxity.plugins.metrics.libs.okio")
        relocate("com.datadoghq", "dev.cubxity.plugins.metrics.libs.com.datadoghq")
        relocate("io.prometheus", "dev.cubxity.plugins.metrics.libs.io.prometheus")
        relocate("io.opentelemetry", "dev.cubxity.plugins.metrics.libs.io.opentelemetry")
    }
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version
            )
        }
    }
}
