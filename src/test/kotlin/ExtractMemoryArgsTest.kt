import com.twilio.apkscale.tasks.MeasureAndroidLibrarySizeTask
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.gradle.api.artifacts.DependencySet
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class ExtractMemoryArgsTest {
    private val project by lazy { ProjectBuilder.builder().build() }
    private val task: MeasureAndroidLibrarySizeTask by lazy {
        project.tasks.register(
            MeasureAndroidLibrarySizeTask.MEASURE_TASK_NAME,
            MeasureAndroidLibrarySizeTask::class.java,
            mutableSetOf<String>(), true, 21, 29,
            emptyMap<String, DependencySet>(), "", true,
        ).get()
    }

    @Test
    @Parameters(method = "extractMemoryArgsParameters")
    fun `extractMemoryArgs should return only memory-related JVM args`(
        jvmArgs: String,
        expectedOutput: String,
    ) {
        Assert.assertEquals(expectedOutput, task.extractMemoryArgs(jvmArgs))
    }

    @Suppress("unused")
    private fun extractMemoryArgsParameters(): Array<Any> {
        return arrayOf(
            // Memory flags: each supported prefix is kept
            arrayOf("-Xmx2g", "-Xmx2g"),
            arrayOf("-Xms512m", "-Xms512m"),
            arrayOf("-Xss1m", "-Xss1m"),
            arrayOf("-XX:MaxMetaspaceSize=256m", "-XX:MaxMetaspaceSize=256m"),
            arrayOf("-XX:MaxPermSize=256m", "-XX:MaxPermSize=256m"),
            arrayOf("-XX:ReservedCodeCacheSize=128m", "-XX:ReservedCodeCacheSize=128m"),
            arrayOf("-XX:+UseCompressedOops", "-XX:+UseCompressedOops"),
            // Non-memory flags: should be stripped
            arrayOf("-verbose:gc", ""),
            arrayOf("-Djava.awt.headless=true", ""),
            arrayOf("-XX:+PrintGCDetails", ""),
            arrayOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", ""),
            // Mixed: memory flags are kept, non-memory flags are dropped
            arrayOf("-Xmx2g -Djava.awt.headless=true", "-Xmx2g"),
            arrayOf("-verbose:gc -Xms512m -XX:+PrintGCDetails -Xmx4g", "-Xms512m -Xmx4g"),
            arrayOf("-Xmx4g -Xms1g -XX:MaxMetaspaceSize=512m -XX:+UseCompressedOops -verbose:gc", "-Xmx4g -Xms1g -XX:MaxMetaspaceSize=512m -XX:+UseCompressedOops"),
            // Empty input
            arrayOf("", ""),
        )
    }
}