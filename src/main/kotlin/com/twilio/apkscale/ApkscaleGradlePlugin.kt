package com.twilio.apkscale

import com.android.build.gradle.LibraryExtension
import com.twilio.apkscale.tasks.MeasureAndroidLibrarySizeTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class ApkscaleGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val libraryExtension = project.extensions.findByType(LibraryExtension::class.java)
                ?: throw error("Apkscale can only be used with an Android Library.")
        val apkscaleExtension = project.extensions.create("apkscale", ApkscaleExtension::class.java)

        MeasureAndroidLibrarySizeTask.create(project, libraryExtension, apkscaleExtension)
    }
}
