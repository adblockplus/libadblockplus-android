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

import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

internal open class CMakeConventionPlugin(private val project: Project) {

    val debugTasks = mutableListOf<TaskProvider<out Task>>()
    val releaseTasks = mutableListOf<TaskProvider<out Task>>()
    private val sdkDir: Path
    private val ndkDir: Path
    private val cmakeDir: Path

    init {
        sdkDir = findAndroidSdk()
        checkPath(sdkDir) {
            "$sdkDir doesn't exists or is not a directory"
        }
        val sdkManagerPath = sdkDir.resolve(Paths.get("tools", "bin", "sdkmanager"))
        ndkDir = sdkDir.resolve(Paths.get("ndk", Config.ndkVersion))
        checkPath(ndkDir) {
            """
                $ndkDir doesn't exists or is not a directory. If needed, please install the needed
                ndk by running:
                
                $sdkManagerPath --install "ndk;${Config.ndkVersion}"
            """.trimIndent()
        }
        cmakeDir = sdkDir.resolve(Paths.get("cmake", Config.cmakeVersion))
        checkPath(cmakeDir) {
            """
                $cmakeDir doesn't exists or is not a directory. If needed, please install the needed
                cmake by running:
                
                $sdkManagerPath --install "cmake;${Config.cmakeVersion}"
            """.trimIndent()
        }
    }

    private fun checkPath(sdkDir: Path, function: () -> String) {
        val f = sdkDir.toFile()
        if (!f.exists() || !f.isDirectory) {
            throw GradleException(function())
        }
    }

    private fun findAndroidSdk(): Path {
        System.getenv("ANDROID_HOME")?.let {
            return Paths.get(it)
        }
        System.getenv("ANDROID_SDK_ROOT")?.let {
            return Paths.get(it)
        }
        val localProperties = File(project.rootProject.rootDir, "local.properties")
        if (localProperties.isFile) {
            val properties = Properties()
            localProperties.inputStream().let {
                properties.load(it)
                it.close()
            }
            properties.getProperty("sdk.dir")?.let {
                return Paths.get(it)
            }
        }
        throw GradleException("""
            Can't find the Android SDK, please put its path in ANDROID_HOME environment variable or
            put it in the sdk.dir property in ${project.rootDir.absolutePath}/local.properties
            """.trimIndent())
    }

    @Suppress("DefaultLocale")
    fun cmake(closure: Closure<Any>) {
        val config = CMakeConfig()
        closure.delegate = config
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        val baseWorkDir = Paths.get(project.buildDir.absolutePath, "cmake", config.name!!)
        val baseConfigTaskName = "configure${config.name!!.capitalize()}"
        val baseBuildTaskName = "build${config.name!!.capitalize()}"
        val systemOsProp = System.getProperty("os.name").toLowerCase()

        val cmakeCmd = cmakeDir.resolve(Paths.get("bin", "cmake"))
        val buildCmd = cmakeDir.resolve(Paths.get("bin", "ninja"))

        val osName = when {
            config.os == Os.ANDROID -> "android"
            systemOsProp.startsWith("windows") -> "win" // Simplifying the task names on Windows
            systemOsProp.startsWith("mac") -> "osx" // Simplifying the task names on Mac
            else -> systemOsProp
        }

        config.arches.map { arch ->
            listOf(true, false).map { debug ->
                val mode = if (debug) "debug" else "release"

                val workDir = baseWorkDir.resolve(Paths.get(mode, osName, arch))
                // i.e. configureHermesWinAmd64Debug
                val configTaskName = "${baseConfigTaskName}${osName.capitalize()}${arch.capitalize()}${mode.capitalize()}"
                // i.e. buildHermesLinuxX86Release
                val buildTaskName = "${baseBuildTaskName}${osName.capitalize()}${arch.capitalize()}${mode.capitalize()}"

                val cmakeParams = HashMap(config.params)
                cmakeParams += if (debug) config.paramsDebug else config.paramsRelease

                // We want to overwrite certain params
                cmakeParams["CMAKE_BUILD_TYPE"] = mode.capitalize()
                val toolchainPath = Paths.get("build", "cmake", "android.toolchain.cmake")
                if (config.os == Os.ANDROID) {
                    cmakeParams["CMAKE_TOOLCHAIN_FILE"] = ndkDir.resolve(toolchainPath).toString()
                    cmakeParams["CMAKE_MAKE_PROGRAM"] = buildCmd.toString()
                    cmakeParams["ANDROID_ABI"] = arch
                    cmakeParams["ANDROID_NATIVE_API_LEVEL"] = "21"
                }

                val paramList = cmakeParams.map { "-D${it.key}=${it.value}" }

                val cmakeFilePath = Paths.get(config.source!!).resolve("CMakeLists.txt")

                // trying to use WSL on Windows to launch cmake
                val cmakeCmdList = mutableListOf(cmakeCmd.toString(), "-G").apply {
                    add(if (osName == "win") "Visual Studio 16 2019" else "Ninja")
                    addAll(paramList)
                    if (osName == "win" && arch =="amd64") add("-Ax64")
                    add(config.source!!)
                }

                val configTask = project.tasks.register(configTaskName, Exec::class.java) {
                    commandLine = cmakeCmdList
                    workingDir = workDir.toFile()

                    group = "configure"

                    // Let's make sure to not run the task if the CMakeLists.txt file didn't change
                    inputs.file(cmakeFilePath)
                    outputs.dir(workDir)

                    doFirst {
                        workingDir.mkdirs()
                    }
                }

                val buildTask = project.tasks.register(buildTaskName, Exec::class.java) {
                    commandLine = if (osName == "win")
                        mutableListOf("MSBuild.exe",
                                "ALL_BUILD.vcxproj",
                                "/p:Configuration=${mode.capitalize()}")
                    else
                        mutableListOf(buildCmd.toString()).apply { addAll(config.targets) }

                    workingDir = workDir.toFile()

                    dependsOn(configTask)

                    group = "build"

                    // We don't check if stuff changed for the build task as running ninja is more
                    // efficient than the gradle incremental build system.
                }

                if (debug) {
                    debugTasks.add(buildTask)
                } else {
                    releaseTasks.add(buildTask)
                }
            }
        }
    }
}

/**
 * A plugin that adds the `cmake` extension to the current project.
 *
 * ```groovy
 * cmake {
 *   name 'nativelib'
 *   source '/path/to/your/source'
 *   // os can be Android, Host or Default. You can also omit this
 *   os Android
 *   // arches can be omitted for Host and Default
 *   arches 'x86', 'x84_64', 'armeabi-v7a', 'arm64-v8a'
 *   param "CMAKE_PARAM", "true"
 * }
 * ```
 */
class CMakePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val cmakeExt = CMakeConventionPlugin(project)
        project.convention.plugins["cmake"] = cmakeExt
        project.afterEvaluate {
            val assembleDebug = project.tasks.register("assembleDebug") {
                dependsOn(cmakeExt.debugTasks.toTypedArray())
                group = "build"
            }
            val assembleRelease = project.tasks.register("assembleRelease") {
                dependsOn(cmakeExt.releaseTasks.toTypedArray())
                group = "build"
            }
            project.tasks.register("assemble") {
                dependsOn(assembleDebug, assembleRelease)
                group = "build"
            }
        }
    }
}
