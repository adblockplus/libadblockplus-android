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

package org.adblockplus.cmake

internal enum class Os {
    ANDROID,
    HOST;
}

internal class CMakeConfig {
    internal var name: String? = null
    internal var source: String? = null
    internal var targets = arrayOf("all")
    internal var params = mutableMapOf<String, String>()
    internal var paramsDebug = mutableMapOf<String, String>()
    internal var paramsRelease = mutableMapOf<String, String>()
    internal var os = default
    internal var arches = arrayOf(System.getProperty("os.arch"))

    fun name(value: Any): Unit { this.name = value.toString() }
    fun source(value: Any) { this.source = value.toString() }
    fun targets(vararg values: String) { this.targets = arrayOf(*values) }
    fun os(value: Os) { this.os = value }
    fun arches(vararg values: String) { this.arches = arrayOf(*values) }
    fun param(name: String, value: String) { params[name] = value }
    fun paramDebug(name: String, value: String) { paramsDebug[name] = value }
    fun paramRelease(name: String, value: String) { paramsRelease[name] = value }

    companion object {
        val default = Os.HOST
        val host = Os.HOST
        val android = Os.ANDROID
    }
}
