import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twilio.apkscale.model.ApkscaleReport
import com.twilio.apkscale.tasks.MeasureAndroidLibrarySizeTask
import java.io.File
import junit.framework.TestCase.assertEquals
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import util.AndroidLibraryProject

private val STAGING_BUILD_TYPE = setOf("staging")
private val ALL_ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
private val PRODUCT_FLAVORS = setOf(
        Pair("demo", "mode"),
        Pair("full", "mode"),
        Pair("minApi24", "api"),
        Pair("minApi23", "api"),
        Pair("minApi21", "api")
)

@RunWith(JUnitParamsRunner::class)
class ApkscaleGradlePluginTest {
    @get:Rule
    val testProjectDir = TemporaryFolder()
    private val androidLibraryProject = AndroidLibraryProject(testProjectDir)
    private val gson = Gson()
    private val apkscaleDir by lazy { File("${testProjectDir.root}/build/apkscale") }
    private val apkscaleOutputDir by lazy { File("$apkscaleDir/build/outputs/reports") }
    private val gradleRunner by lazy {
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
    }

    @Before
    fun setup() {
        androidLibraryProject.setup()
    }

    @Test(expected = IllegalStateException::class)
    fun `it can only be used with Android library projects`() {
        GradleRunner.create()
                .withProjectDir(TemporaryFolder().root)
                .withPluginClasspath()
                .build()
    }

    @Test
    fun `it should provide empty output when there is no library built to measure`() {
        androidLibraryProject.writeBuildFile()
        val result = gradleRunner.withArguments(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME)
                .withArguments(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME)
                .build()
        assertMeasureTaskSucceeded(result)
        assertThat(getApkScaleReports()).isEmpty()
    }

    @Test
    fun `it should measure after assemble tasks`() {
        androidLibraryProject.writeBuildFile()
        val result = gradleRunner.withArguments(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME)
                .withArguments(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME, "assemble")
                .build()
        assertMeasureTaskSucceeded(result)
        assertThat(getApkScaleReports()).isNotEmpty()
    }

    @Test
    fun `it should only measure assembled variants`() {
        androidLibraryProject.writeBuildFile()
        val result = gradleRunner.withArguments(MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME, "assembleRelease")
                .build()
        assertMeasureTaskSucceeded(result)

        /*
         * By default the test project only contains a debug and release build type and no product flavors. Therefore,
         * the invocation of assembleRelease should only result in one report.
         */
        assertEquals(1, getApkScaleReports().size)
    }

    @Test
    @Parameters(method = "measureLibrarySizeParameters")
    fun `it should measure the size of a library project`(
        abis: Set<String>,
        buildTypes: Set<String>,
        productFlavors: Set<Pair<String, String>>
    ) {
        androidLibraryProject.addAbis(abis)
        androidLibraryProject.addBuildTypes(buildTypes)
        androidLibraryProject.addProductFlavors(productFlavors)
        androidLibraryProject.writeBuildFile()
        val result = gradleRunner.withArguments("assemble", MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME)
                .build()
        assertMeasureTaskSucceeded(result)
        val apkScaleReports = getApkScaleReports()

        assertThat(apkScaleReports).isNotEmpty()

        apkScaleReports.forEach { apkScaleReport ->
            assertThat(apkScaleReport.library).isNotEmpty()
            assertThat(apkScaleReport.sizeMap)
                    .hasSize(abis.size + 1)
            abis.plus("universal").forEach { abi ->
                assertThat(apkScaleReport.sizeMap).containsKey(abi)
                assertThat(apkScaleReport.sizeMap[abi]).matches("^\\d+\\w+$")
            }
        }
    }

    private fun getApkScaleReports(): List<ApkscaleReport> {
        val apkScaleReportListType = object : TypeToken<List<ApkscaleReport>>() {}.type
        val apkscaleReportFile = File(apkscaleOutputDir, "apkscale.json")
        val string = apkscaleReportFile.readTemaven { url 'https://repo.gradle.org/gradle/libs-releases' }xt()

        return gson.fromJson<List<ApkscaleReport>>(string, apkScaleReportListType)
    }

    private fun assertMeasureTaskSucceeded(buildResult: BuildResult) {
        assertEquals(TaskOutcome.SUCCESS,
                buildResult.task(":${MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME}")?.outcome)
    }

    // Provides test parameters
    @Suppress("unused")
    private fun measureLibrarySizeParameters(): Array<Any>? {
        return arrayOf(
                arrayOf(emptySet<String>(), emptySet<String>(), emptySet<Pair<String, String>>()),
                arrayOf(ALL_ABIS, emptySet<String>(), emptySet<Pair<String, String>>()),
                arrayOf(emptySet<String>(), STAGING_BUILD_TYPE, emptySet<Pair<String, String>>()),
                arrayOf(emptySet<String>(), emptySet<String>(), PRODUCT_FLAVORS),
                arrayOf(emptySet<String>(), STAGING_BUILD_TYPE, PRODUCT_FLAVORS),
                arrayOf(ALL_ABIS, emptySet<String>(), PRODUCT_FLAVORS),
                arrayOf(ALL_ABIS, STAGING_BUILD_TYPE, emptySet<Pair<String, String>>()),
                arrayOf(ALL_ABIS, STAGING_BUILD_TYPE, PRODUCT_FLAVORS)
        )
    }
}
