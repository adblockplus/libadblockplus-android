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

object Config {
    const val moduleVersion = "5.0.0"

    const val buildToolsVersion = "30.0.2"
    const val compileSdkVersion = 30
    const val minSdkVersion = 21
    const val webView_minSdkVersion = 21
    const val targetSdkVersion = compileSdkVersion

    const val jvmTarget = "1.8"

    const val ndkVersion = "21.1.6352462"
    const val cmakeVersion = "3.18.1"
}

object GitlabPackageRegistry {
    const val groupId = "org.adblockplus"
    const val repository = "https://gitlab.com/api/v4/projects/8817162/packages/maven"
}
