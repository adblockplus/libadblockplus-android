/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java.srcDir("../hermes/buildSrc/src/main/java")
                .exclude("Config.kt")
                .exclude("Dependencies.kt")
    }
}

gradlePlugin {
    plugins {
        create("eyeoCMakePlugin") {
            id = "adblockplus-cmake-plugin"
            implementationClass = "org.adblockplus.cmake.CMakePlugin"
        }

        create("eyeoSharedTestPlugin") {
            id = "adblockplus-shared-test-plugin"
            implementationClass = "org.adblockplus.config.ConfigPlugin"
        }

        create("eyeoNpmPlugin") {
            id = "adblockplus-npm-plugin"
            implementationClass = "org.adblockplus.npm.NpmPlugin"
        }
    }
}
