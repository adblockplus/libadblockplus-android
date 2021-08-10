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

package org.adblockplus.npm

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.io.File
import java.io.FileReader
import java.util.Properties

class NpmPlugin : Plugin<Project> {

    private val npmConvention = NpmConvention()

    override fun apply(project: Project) {
        project.convention.plugins[CONVENTION_NAME] = npmConvention

        val npmPath = getNpmPath(project)

        if (!npmPath.isFile) {
            throw GradleException(errorMessage)
        }

        project.tasks.register("modules", Exec::class.java) {
            commandLine(npmPath.toString(), "ci")
            group="build"

            inputs.file(PACKAGE_JSON)
            outputs.dir(project.file("node_modules"))
        }

        project.tasks.register("build", Exec::class.java) {
            commandLine(npmPath.toString(), "run", npmConvention.config.buildScript!!)
            group="build"
            
            inputs.files(project.fileTree(project.projectDir) {
                exclude("**/node_modules/**")
                include("**/*.js")
            })

            outputs.dir(npmConvention.config.buildDir!!)

            dependsOn("modules")
        }

        project.tasks.register("clean", Exec::class.java) {
            commandLine(npmPath.toString(), "run", npmConvention.config.cleanScript)
            group="clean"
        }
    }

    private fun getNpmPath(project: Project): File {
        System.getenv("NPM_PATH")?.let {
            return File(it)
        }
        val localPropertiesFile = project.rootProject.file(LOCAL_PROPERTIES)
        if (!localPropertiesFile.isFile) {
            throw GradleException(errorMessage)
        }

        return with(FileReader(localPropertiesFile)) {
            val properties = Properties()
            properties.load(this)
            close()
            val npmPath: String? = properties.getProperty(NPM_PATH_KEY)
            if (npmPath.isNullOrEmpty()) {
                throw GradleException(errorMessage)
            }
            File(properties.getProperty(NPM_PATH_KEY))
        }
    }

    companion object {
        const val NPM_PATH_KEY = "npm.path"
        const val LOCAL_PROPERTIES = "local.properties"
        const val CONVENTION_NAME = "eyeo-npm-plugin-convention"
        const val PACKAGE_JSON = "package.json"

        val errorMessage = """
            Can't find npm install directory. Please, add it to ${LOCAL_PROPERTIES}:
            
            ${NPM_PATH_KEY}=<absolute_path_to_npm>
        """.trimIndent()
    }
}
