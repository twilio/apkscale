package com.twilio.apkscale.tasks

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import com.google.gson.Gson
import com.twilio.apkscale.ApkscaleExtension
import com.twilio.apkscale.model.ApkscaleReport
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.GradleConnector
import org.jetbrains.kotlin.com.google.common.annotations.VisibleForTesting
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.util.Locale
import javax.inject.Inject

private const val UNIVERSAL_ABI = "universal"

open class MeasureAndroidLibrarySizeTask @Inject constructor(
    private val abis: Set<String>,
    private val humanReadable: Boolean,
    private val minSdkVersion: Int,
    private val targetSdkVersion: Int,
    private val variantDependencies: Map<String, DependencySet>,
    private val ndkVersion: String,
) : DefaultTask() {
    companion object {
        const val MEASURE_TASK_NAME = "measureSize"

        fun create(project: Project, libraryExtension: LibraryExtension, apkscaleExtension: ApkscaleExtension) {
            project.afterEvaluate {
                val measureTask = project.tasks.create(
                    MEASURE_TASK_NAME,
                    MeasureAndroidLibrarySizeTask::class.java,
                    apkscaleExtension.abis,
                    apkscaleExtension.humanReadable,
                    libraryExtension.defaultConfig.minSdkVersion?.apiLevel,
                    libraryExtension.defaultConfig.targetSdkVersion?.apiLevel,
                    getVariantDependencies(libraryExtension.libraryVariants),
                    libraryExtension.ndkVersion ?: "",
                )

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

        private fun getVariantDependencies(libraryVariants: DomainObjectSet<LibraryVariant>): Map<String, DependencySet> {
            /*
             * Create a map of the library variants to the variant's dependencies so that apkscale pulls in the
             * correct dependencies for each variant that is measured.
             */
            return libraryVariants.associate {
                it.name.lowercase(Locale.getDefault()) to it.compileConfiguration.allDependencies
            }
        }
    }

    private val outputAarDir = project.buildDir.resolve("outputs/aar")
    private val apkscaleDir = File("${project.buildDir}/apkscale")
    private val appMainDir = File("$apkscaleDir/src/main")
    private val apkscaleOutputDir = File("$apkscaleDir/build/outputs/reports")
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
        val apkScaleReports = mutableListOf<ApkscaleReport>()
        outputAarDir.walkTopDown().filter { !it.isDirectory }.forEach { aarFile ->
            // Write the build.gradle file to the apkscale project
            writeBuildFile(aarFile)

            // Assemble an apkscale release build
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(apkscaleDir)
                .useBuildDistribution()
                .connect()
            try {
                connection.use {
                    it.newBuild()
                        .forTasks("assembleRelease")
                        .setStandardOutput(System.out)
                        .run()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection.close()
            }
            val sizeMap = mutableMapOf<String, String>()
            abis.plus(UNIVERSAL_ABI).forEach { abi ->
                val outputStream = ByteArrayOutputStream()
                val abiSuffix = resolveApkAbiSuffix(abi)
                project.exec {
                    it.workingDir(project.rootDir)
                    val apkanalyzerCommand = mutableListOf("apkanalyzer")
                    if (humanReadable) {
                        apkanalyzerCommand.add("--human-readable")
                    }
                    apkanalyzerCommand.addAll(
                        listOf(
                            "apk",
                            "compare",
                            "--different-only",
                            "$apkscaleDir/build/outputs/apk/withoutLibrary/release/apkscale-withoutLibrary${abiSuffix}release-unsigned.apk",
                            "$apkscaleDir/build/outputs/apk/withLibrary/release/apkscale-withLibrary${abiSuffix}release-unsigned.apk",
                        ),
                    )
                    it.commandLine(apkanalyzerCommand)
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
            apkScaleReports.add(ApkscaleReport(aarFile.name, sizeMap))
        }
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
            """.trimIndent(),
        )
        settingsFile.writeText(
            """
                include ':apkscale'
            """.trimIndent(),
        )
        manifestFile.writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application/>
                </manifest>
            """.trimIndent(),
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
        val dependencyConfiguration = "withLibraryImplementation"
        buildFile.writeText(
            """
                buildscript {
                  repositories {
                    mavenLocal()
                    google()
                    mavenCentral()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:8.0.2'
                  }
                }
                apply plugin: 'com.android.application'
                android {
                  compileSdkVersion $targetSdkVersion
                  ${resolveNdkVersion()}
                  namespace 'com.twilio.apkscale'
                  defaultConfig {
                      applicationId "com.twilio.apkscale"
                      minSdkVersion $minSdkVersion
                      targetSdkVersion $targetSdkVersion
                  }
                  compileOptions {
                      sourceCompatibility JavaVersion.VERSION_11
                      targetCompatibility JavaVersion.VERSION_11
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
                  mavenCentral()
                }
                dependencies {
                    ${resolveDependencies(dependencyConfiguration, aarLibraryFile)}
                    $dependencyConfiguration files("${aarLibraryFile.absolutePath}")
                }
            """.trimIndent(),
        )
    }

    @VisibleForTesting
    internal fun resolveNdkVersion(): String {
        return if (ndkVersion.isNotEmpty()) {
            "ndkVersion = \"$ndkVersion\""
        } else {
            ""
        }
    }

    /*
     * This method returns the build.gradle line that includes ABIs for APK splits.
     *
     * eg. include "arm64-v8a", "x86"
     *
     * The method will return an empty line if no abis are set.
     */
    @VisibleForTesting
    internal fun resolveIncludedAbis(): String {
        return if (abis.isEmpty()) "" else "include ${abis.joinToString { "\"${it}\"" }}"
    }

    /*
     * This method returns the portion of the file path that includes the ABI. If no ABIs were provided, this method
     * will return "-".
     */
    @VisibleForTesting
    internal fun resolveApkAbiSuffix(abi: String): String {
        return if (abi == UNIVERSAL_ABI && abis.isEmpty()) "-" else "-$abi-"
    }

    /*
     * Return a string representation of dependencies for a given library variant.
     */
    private fun resolveDependencies(dependencyConfiguration: String, aarLibraryFile: File): String {
        val variant = getVariant(aarLibraryFile)
        /* filter out internal deps that don't have groups/etc.. */
        return variantDependencies[variant]?.filter {
            it.group != null && it.version != null
        }?.joinToString(separator = "\n") {
            "$dependencyConfiguration \"${it.group}:${it.name}:${it.version}\""
        } ?: ""
    }

    /*
     * Extract an all lower case string representation of the variant based on the AAR output.
     */
    private fun getVariant(aarLibraryFile: File): String {
        val aarFileName = aarLibraryFile.name

        /*
         * The Android Gradle Plugin builds Android libraries with the following name format:
         *
         * [library-name]-[the-build-variant].aar
         *
         * Remove the project name, the file suffix, replace the hyphens, and then
         * convert the remaining build variant to a lower case string.
         */
        return aarFileName.substringAfter("${project.name}-")
            .substringBefore(".aar")
            .replace("-", "")
            .lowercase(Locale.getDefault())
    }
}
