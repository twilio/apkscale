package util

import org.junit.rules.TemporaryFolder

class AndroidLibraryProject(
    private val projectFolder: TemporaryFolder = TemporaryFolder(),
    private val abis: MutableSet<String> = mutableSetOf(),
    private val buildTypes: MutableSet<String> = mutableSetOf(),
    private val productFlavors: MutableSet<Pair<String, String>> = mutableSetOf(),
    private var ndkVersion: String? = null
) {

    fun setup() {
        projectFolder.newFolder("src", "main")
        projectFolder.newFile("gradle.properties").apply {
            writeText(
                """
                android.useAndroidX=true
                """.trimIndent()
            )
        }
        projectFolder.newFile("/src/main/AndroidManifest.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.twilio.apkscale.test">
                    <application/>
                </manifest>
                """.trimIndent()
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

    fun writeBuildFile() {
        projectFolder.newFile("build.gradle").apply {
            writeText(
                """
                buildscript {
                  repositories {
                    google()
                    jcenter()
                    mavenLocal()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:4.0.0'
                  }
                }
                plugins {
                  id 'com.android.library'
                  id 'com.twilio.apkscale'
                }
                ${resolveApkscaleConfig()}
                android {
                  compileSdkVersion 29
                  ${resolveNdkVersion()}
                  buildToolsVersion "29.0.2"
                  defaultConfig {
                    minSdkVersion 21
                    targetSdkVersion 29
                  }
                  compileOptions {
                      sourceCompatibility 1.8
                      targetCompatibility 1.8
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
                  jcenter()
                }
                """.trimIndent()
            )
        }
    }

    private fun resolveNdkVersion(): String {
        return ndkVersion?.let {
            "ndkVersion = \"$ndkVersion\""
        } ?: ""
    }

    private fun resolveApkscaleConfig(): String {
        return if (abis.isEmpty()) "" else {
            """
            apkscale {
              abis = ${abis.joinToString(prefix = "[", postfix = "]") { "\"${it}\"" }}
            }
            """.trimIndent()
        }
    }

    private fun resolveProductFlavors(): String {
        return if (productFlavors.isEmpty()) "" else {
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
}
