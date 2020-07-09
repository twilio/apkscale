package tasks

import com.twilio.apkscale.tasks.MeasureAndroidLibrarySizeTask
import junit.framework.Assert.assertEquals
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class MeasureAndroidLibrarySizeTaskTest {
    private val project by lazy {
        ProjectBuilder.builder()
                .build()
    }
    private val abis = mutableSetOf<String>()
    private val measureAndroidLibrarySizeTask: MeasureAndroidLibrarySizeTask by lazy {
        project.tasks.create(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME,
                MeasureAndroidLibrarySizeTask::class.java,
                abis)
    }

    @Test
    @Parameters(method = "apkSplitAbiParameters")
    fun `resolveIncludedAbis should provide abi section of APK splits`(abis: Set<String>,
                                                                       expectedOutput: String) {
        this.abis.addAll(abis)
        assertEquals(expectedOutput, measureAndroidLibrarySizeTask.resolveIncludedAbis())
    }

    @Test
    @Parameters(method = "apkAbiSuffixParameters")
    fun `should resolve APK abi suffix`(abis: Set<String>,
                                        abi: String,
                                        expectedOutput: String) {
        this.abis.addAll(abis)
        assertEquals(expectedOutput, measureAndroidLibrarySizeTask.resolveApkAbiSuffix(abi))
    }

    // Test parameters
    @Suppress("unused")
    private fun apkSplitAbiParameters(): Array<Any>? {
        return arrayOf(
                arrayOf(emptySet<String>(), ""),
                arrayOf(setOf("arm64-v8a"), "include \"arm64-v8a\""),
                arrayOf(setOf("arm64-v8a", "x86_64"), "include \"arm64-v8a\", \"x86_64\"")
        )
    }

    // Test parameters
    @Suppress("unused")
    private fun apkAbiSuffixParameters(): Array<Any>? {
        return arrayOf(
                arrayOf(emptySet<String>(), "universal", "-"),
                arrayOf(setOf("arm64-v8a"), "universal", "-universal-"),
                arrayOf(setOf("x86_64"), "x86_64", "-x86_64-")
        )
    }
}