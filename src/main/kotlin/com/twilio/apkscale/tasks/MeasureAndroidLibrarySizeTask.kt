package com.twilio.apkscale.tasks

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.utils.toImmutableSet
import com.google.common.annotations.VisibleForTesting
import com.google.gson.Gson
import com.twilio.apkscale.ApkscaleExtension
import com.twilio.apkscale.model.ApkScaleReport
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private const val UNIVERSAL_ABI = "universal"

open class MeasureAndroidLibrarySizeTask @Inject constructor(private val abis: Set<String>) : DefaultTask() {
    companion object {
        const val MEASURE_TASK_NAME = "measureSize"

        fun create(project: Project, libraryExtension: LibraryExtension, apkscaleExtension: ApkscaleExtension) {
            project.afterEvaluate {
                val measureTask = project.tasks.create(MEASURE_TASK_NAME,
                        MeasureAndroidLibrarySizeTask::class.java,
                        apkscaleExtension.abis)

                // Ensure that measure task runs after assemble tasks
                measureTask.mustRunAfter(project.tasks.named("assemble"))
                libraryExtension.buildTypes.forEach {
                    measureTask.mustRunAfter(project.tasks.named("assemble${it.name.capitalize()}"))
                }
                libraryExtension.libraryVariants.forEach {
                    measureTask.mustRunAfter(project.tasks.named("assemble${it.name.capitalize()}"))
                }
            }
        }
    }

    private val outputAarDir = project.buildDir.resolve("outputs/aar")
    private val apkscaleDir = File("${project.buildDir}/apkscale")
    private val appMainDir = File("${apkscaleDir}/src/main")
    private val apkscaleOutputDir = File("${apkscaleDir}/build/outputs/reports")
    private val buildFile = File(apkscaleDir, "build.gradle")
    private val settingsFile = File(apkscaleDir, "settings.gradle")
    private val gradlePropertiesFile = File(apkscaleDir, "gradle.properties")
    private val manifestFile = File(appMainDir, "AndroidManifest.xml")
    private val gson = Gson()
    private val apkscaleReportFile = File(apkscaleOutputDir, "apkscale.json")

    init {
        setupAndroidProject()
    }

    @TaskAction
    fun measureAndroidLibrarySize() {
        val apkScaleReports = mutableListOf<ApkScaleReport>()
        outputAarDir.walkTopDown().filter { !it.isDirectory }.forEach { aarFile ->
            // Write the build.gradle file to the apkscale project
            writeBuildFile(aarFile)

            // Assemble an apkscale release build
            val connection = GradleConnector.newConnector()
                    .forProjectDirectory(apkscaleDir)
                    .connect()
            connection.use {
                it.newBuild().forTasks("assembleRelease").run()
            }

            val sizeMap = mutableMapOf<String, String>()
            abis.plus(UNIVERSAL_ABI).forEach { abi ->
                val outputStream = ByteArrayOutputStream()
                val abiSuffix = resolveApkAbiSuffix(abi)
                project.exec {
                    it.workingDir(project.rootDir)
                    it.commandLine("apkanalyzer",
                            "--human-readable",
                            "apk",
                            "compare",
                            "--different-only",
                            "${apkscaleDir}/build/outputs/apk/withoutLibrary/release/apkscale-withoutLibrary${abiSuffix}release-unsigned.apk",
                            "${apkscaleDir}/build/outputs/apk/withLibrary/release/apkscale-withLibrary${abiSuffix}release-unsigned.apk")
                    it.standardOutput = outputStream
                }
                /*
                 * The line format of apkanalyzer is
                 *
                 * old size / new size / size difference / path
                 *
                 * The first line represents the difference between the entire APKs followed by file
                 * and directory differrences. Extract the total size difference to determine the size.
                 */
                val size = outputStream.toString().split("\\s+".toRegex())[2]
                sizeMap[abi] = size
            }
            apkScaleReports.add(ApkScaleReport(aarFile.name, sizeMap))
        }
        logger.quiet(gson.toJson(apkScaleReports).toString())
        apkscaleReportFile.writeText(gson.toJson(apkScaleReports).toString())
    }

    /*
     * Create a simple Android application project
     */
    private fun setupAndroidProject() {
        if (apkscaleDir.exists()) {
            apkscaleDir.deleteRecursively()
        }
        apkscaleDir.mkdirs()
        if (appMainDir.exists()) {
            appMainDir.deleteRecursively()
        }
        appMainDir.mkdirs()
        if (apkscaleOutputDir.exists()) {
            apkscaleOutputDir.deleteRecursively()
        }
        apkscaleOutputDir.mkdirs()
        gradlePropertiesFile.writeText(
                """
                android.useAndroidX=true
                """.trimIndent()
        )
        settingsFile.writeText(
                """
                include ':apkscale'
                """.trimIndent()
        )
        manifestFile.writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.twilio.apkscale">
                    <application/>
                </manifest>
                """.trimIndent()
        )
    }

    /*
     * Write a build.gradle file that depends on the provided path to the Android library AAR file. The method
     * configures APK splits based on the measured library's supported ABIs.
     */
    private fun writeBuildFile(aarLibraryFile: File) {
        if (buildFile.exists()) {
            buildFile.delete()
        }
        buildFile.writeText(
                """
                buildscript {
                  repositories {
                    mavenLocal()
                    google()
                    jcenter()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:4.0.0'
                  }
                }
                apply plugin: 'com.android.application'
                android {
                  compileSdkVersion 29
                  buildToolsVersion "29.0.2"
                  defaultConfig {
                      applicationId "com.twilio.apkscale"
                      minSdkVersion 21
                      targetSdkVersion 29
                  }
                  compileOptions {
                      sourceCompatibility 1.8
                      targetCompatibility 1.8
                  }
                  splits {
                      abi {
                          enable true
                          reset()
                          ${resolveIncludedAbis()}
                          universalApk true
                      }
                  }
                  flavorDimensions "appType"

                  productFlavors {
                      withoutLibrary {
                          dimension "appType"
                          applicationId "com.twilio.apkscale.withoutlibrary"
                      }

                      withLibrary {
                          dimension "appType"
                          applicationId "com.twilio.apkscale.withlibrary"
                      }
                  }
                }
                repositories {
                  mavenLocal()
                  google()
                  jcenter()
                }
                dependencies {
                    withLibraryImplementation files("${aarLibraryFile.absolutePath}")
                }
                """.trimIndent()
        )
    }

    /*
     * This method returns the build.gradle line that includes ABIs for APK splits.
     *
     * eg. include "arm64-v8a", "x86"
     *
     * The method will return an empty line if no abis are set.
     */
    @VisibleForTesting
    internal fun resolveIncludedAbis() : String {
        return if (abis.isEmpty()) "" else "include ${abis.joinToString { "\"${it}\"" }}"
    }

    /*
     * This method returns the portion of the file path that includes the ABI. If no ABIs were provided, this method
     * will return "-".
     */
    @VisibleForTesting
    internal fun resolveApkAbiSuffix(abi: String) : String {
        return if (abi == UNIVERSAL_ABI && abis.isEmpty()) "-" else "-${abi}-"
    }
}