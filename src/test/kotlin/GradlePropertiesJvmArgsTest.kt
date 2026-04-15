package tasks

import com.twilio.apkscale.tasks.MeasureAndroidLibrarySizeTask
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnitParamsRunner::class)
class GradlePropertiesJvmArgsTest {

    private fun createTask(project: Project) {
        project.tasks.register(
            MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME,
            MeasureAndroidLibrarySizeTask::class.java,
            mutableSetOf<String>(),
            true,
            21,
            29,
            emptyMap<String, DependencySet>(),
            "",
            true,
        ).get()
    }

    private fun gradlePropertiesFile(project: Project): File =
        project.layout.buildDirectory.dir("apkscale").get().file("gradle.properties").asFile

    @Test
    fun `gradle properties file should not include jvmargs line when property is not set`() {
        val project = ProjectBuilder.builder().build()
        createTask(project)
        assertEquals("android.useAndroidX=true", gradlePropertiesFile(project).readText())
    }

    @Test
    @Parameters(method = "jvmArgsToGradlePropertiesParameters")
    fun `gradle properties file should contain only memory-related JVM args`(
        jvmArgs: String,
        expectedFileContent: String,
    ) {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties.set("org.gradle.jvmargs", jvmArgs)
        createTask(project)
        assertEquals(expectedFileContent, gradlePropertiesFile(project).readText())
    }

    @Suppress("unused")
    private fun jvmArgsToGradlePropertiesParameters(): Array<Any> {
        return arrayOf(
            // Only non-memory flags: jvmargs line is omitted entirely
            arrayOf("-verbose:gc", "android.useAndroidX=true"),
            arrayOf("-Djava.awt.headless=true", "android.useAndroidX=true"),
            arrayOf("-XX:+PrintGCDetails", "android.useAndroidX=true"),
            // Only memory flags: written verbatim
            arrayOf("-Xmx2g", "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g"),
            arrayOf("-Xms512m", "android.useAndroidX=true\norg.gradle.jvmargs=-Xms512m"),
            arrayOf("-Xss1m", "android.useAndroidX=true\norg.gradle.jvmargs=-Xss1m"),
            arrayOf("-XX:MaxMetaspaceSize=256m", "android.useAndroidX=true\norg.gradle.jvmargs=-XX:MaxMetaspaceSize=256m"),
            arrayOf("-XX:ReservedCodeCacheSize=128m", "android.useAndroidX=true\norg.gradle.jvmargs=-XX:ReservedCodeCacheSize=128m"),
            arrayOf("-XX:+UseCompressedOops", "android.useAndroidX=true\norg.gradle.jvmargs=-XX:+UseCompressedOops"),
            // Mixed: non-memory flags are stripped, memory flags are kept
            arrayOf("-Xmx2g -verbose:gc", "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g"),
            arrayOf("-verbose:gc -Xms512m -XX:+PrintGCDetails -Xmx4g", "android.useAndroidX=true\norg.gradle.jvmargs=-Xms512m -Xmx4g"),
            arrayOf(
                "-Xmx4g -Xms1g -XX:MaxMetaspaceSize=512m -XX:+UseCompressedOops -verbose:gc",
                "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx4g -Xms1g -XX:MaxMetaspaceSize=512m -XX:+UseCompressedOops",
            ),
        )
    }
}
