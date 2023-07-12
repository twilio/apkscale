package util

import org.junit.rules.TemporaryFolder

class AndroidLibraryProject(
    private val projectFolder: TemporaryFolder = TemporaryFolder(),
    private val abis: MutableSet<String> = mutableSetOf(),
    private val buildTypes: MutableSet<String> = mutableSetOf(),
    private val productFlavors: MutableSet<Pair<String, String>> = mutableSetOf(),
    private val dependencies: MutableSet<Pair<String, String>> = mutableSetOf(),
    private var ndkVersion: String? = null,
    var humanReadable: Boolean = true,
) {

    fun setup() {
        projectFolder.newFolder("src", "main")
        projectFolder.newFile("gradle.properties").apply {
            writeText(
                """
                android.useAndroidX=true
                android.defaults.buildfeatures.buildconfig=true
                """.trimIndent(),
            )
        }
        projectFolder.newFile("/src/main/AndroidManifest.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application/>
                </manifest>
                """.trimIndent(),
            )
        }
    }

    fun addAbis(abis: Set<String>) {
        this.abis.addAll(abis)
    }

    fun addBuildTypes(buildTypes: Set<String>) {
        this.buildTypes.addAll(buildTypes)
    }

    fun addProductFlavors(productFlavors: Set<Pair<String, String>>) {
        this.productFlavors.addAll(productFlavors)
    }

    fun setNdkVersion(ndkVersion: String) {
        this.ndkVersion = ndkVersion
    }

    fun addDependency(configuration: String, dependency: String) {
        this.dependencies.add(Pair(configuration, dependency))
    }

    fun writeBuildFile() {
        projectFolder.newFile("build.gradle").apply {
            writeText(
                """
                buildscript {
                  repositories {
                    google()
                    mavenLocal()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:8.0.2'
                  }
                }
                plugins {
                  id 'com.android.library'
                  id 'com.twilio.apkscale'
                }
                ${resolveApkscaleConfig()}
                android {
                  compileSdkVersion 31
                  ${resolveNdkVersion()}
                  namespace 'com.twilio.apkscale'
                  defaultConfig {
                    minSdkVersion 21
                    targetSdkVersion 31
                  }
                  compileOptions {
                      sourceCompatibility JavaVersion.VERSION_11
                      targetCompatibility JavaVersion.VERSION_11
                  }
                  buildTypes {
                    debug {}
                    release {}
                    ${buildTypes.joinToString(separator = "\n") { "$it {}" }}
                  }
                  ${resolveProductFlavors()}
                }
                repositories {
                  mavenLocal()
                  google()
                  mavenCentral()
                }
                dependencies {
                    ${resolveDependencies()}
                }
                """.trimIndent(),
            )
        }
    }

    private fun resolveNdkVersion(): String {
        return ndkVersion?.let {
            "ndkVersion = \"$ndkVersion\""
        } ?: ""
    }

    private fun resolveApkscaleConfig(): String {
        return """
            apkscale {
              ${resolveApkscaleAbis()}
              humanReadable = $humanReadable
            }
        """.trimIndent()
    }

    private fun resolveApkscaleAbis(): String {
        return if (abis.isEmpty()) {
            ""
        } else "abis = ${abis.joinToString(prefix = "[", postfix = "]") {
            "\"${it}\""
        }}".trimIndent()
    }

    private fun resolveProductFlavors(): String {
        return if (productFlavors.isEmpty()) {
            ""
        } else {
            val flavorDimensions = mutableSetOf<String>()
            productFlavors.forEach {
                flavorDimensions.add(it.second)
            }
            """
            flavorDimensions ${flavorDimensions.joinToString { "\"${it}\"" }}
            productFlavors {
              ${productFlavors.joinToString(separator = "\n") { "${it.first} { dimension \"${it.second}\" }" }}
            }
            """.trimIndent()
        }
    }

    private fun resolveDependencies(): String {
        return if (dependencies.isEmpty()) {
            ""
        } else {
            dependencies.joinToString(separator = "\n") { "${it.first} \"${it.second}\"" }
        }
    }
}
