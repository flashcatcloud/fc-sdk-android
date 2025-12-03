/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.security

import android.util.Log
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.log.Logger
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.log.LogsConfiguration
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumActionType
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.RumMonitor
import cloud.flashcat.android.rum.RumResourceKind
import cloud.flashcat.android.rum.RumResourceMethod
import cloud.flashcat.android.security.Encryption
import cloud.flashcat.android.sessionreplay.SessionReplay
import cloud.flashcat.android.sessionreplay.SessionReplayConfiguration
import cloud.flashcat.android.trace.DatadogTracing
import cloud.flashcat.android.trace.GlobalDatadogTracer
import cloud.flashcat.android.trace.Trace
import cloud.flashcat.android.trace.TraceConfiguration
import cloud.flashcat.android.trace.api.tracer.DatadogTracer
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.experimental.inv

@MediumTest
internal class EncryptionTest {

    @get:Rule
    val forge = ForgeRule()

    @Before
    fun setUp() = deleteAllSdkFiles()

    @After
    fun tearDown() = deleteAllSdkFiles()

    @Test
    fun must_encrypt_all_data() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val configuration = createSdkConfiguration()

        Flashcat.setVerbosity(Log.VERBOSE)
        val sdkCore =
            Flashcat.initialize(targetContext, configuration, TrackingConsent.PENDING)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            {
                val rumConfig =
                    RumConfiguration.Builder(applicationId = forge.anAlphaNumericalString()).build()
                Rum.enable(rumConfig, sdkCore)
            },
            { Logs.enable(LogsConfiguration.Builder().build(), sdkCore) },
            { Trace.enable(TraceConfiguration.Builder().build(), sdkCore) },
            {
                val sessionReplayConfiguration = SessionReplayConfiguration
                    .Builder(100f)
                    .build()
                SessionReplay.enable(sessionReplayConfiguration, sdkCore)
            }
        )
        featureActivations.shuffled(Random(forge.seed)).forEach { it() }

        val tracer = DatadogTracing.newTracerBuilder(sdkCore).setBundleWithRumEnabled(true).build()
        GlobalDatadogTracer.registerIfAbsent(tracer)

        val logger = Logger.Builder(sdkCore)
            .setBundleWithRumEnabled(true)
            .setBundleWithTraceEnabled(true)
            .build()

        sendEventsForAllFeatures(GlobalRumMonitor.get(sdkCore), logger, tracer)

        flushAndShutdownExecutors()
        stopSdk()

        // this part is flaky, because we make an assumption on the internal folder/file structure
        val isPendingDirectory = { file: File ->
            file.name.contains("pending")
        }
        val isNdkPendingDirectory = { file: File ->
            file.name == "ndk_crash_reports_intermediary_v2"
        }

        val dataDirectories =
            targetContext.cacheDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("datadog") }
                ?.flatMap { it.listFiles()?.asIterable() ?: emptyList() }
                ?.filter {
                    if (it.isDirectory) {
                        (isPendingDirectory(it) && !it.name.contains("crash")) ||
                            isNdkPendingDirectory(it)
                    } else {
                        false
                    }
                }!!

        assertThat(dataDirectories).isNotEmpty()

        dataDirectories.forEach { directory ->

            val files = directory.listFiles()!!

            assertThat(files)
                .overridingErrorMessage("Expecting ${directory.path} to contain files")
                .isNotEmpty

            files.forEach { file ->
                val content = file.readText()

                assertThat(content)
                    .overridingErrorMessage("Expecting ${file.path} to not contain plain text")
                    .doesNotContain("source")
                assertThat(content)
                    .overridingErrorMessage("Expecting ${file.path} to contain encryption marker")
                    .contains(ENCRYPTION_MARKER)
            }
        }
    }

    // region private

    private fun createSdkConfiguration(): Configuration {
        val encryption = object : Encryption {
            override fun encrypt(data: ByteArray): ByteArray {
                return ENCRYPTION_MARKER.toByteArray() + data.map { it.inv() }
            }

            override fun decrypt(data: ByteArray): ByteArray {
                throw UnsupportedOperationException("Shouldn't be called in this test")
            }
        }

        return Configuration
            .Builder(
                clientToken = forge.anAlphaNumericalString(),
                env = forge.anAlphaNumericalString()
            )
            .setEncryption(encryption)
            .build()
    }

    private fun sendEventsForAllFeatures(rumMonitor: RumMonitor, logger: Logger, tracer: DatadogTracer) {
        val viewName = "rumView-${forge.aString()}"
        rumMonitor.startView(viewName, viewName)

        logger.w("Started a view")
        val resourceName = "rumResource-${forge.aString()}"

        val span = tracer.buildSpan(resourceName)
            .start()

        rumMonitor.startResource(
            resourceName,
            RumResourceMethod.GET,
            "https://${forge.anAlphaNumericalString()}.com"
        )

        rumMonitor.addAction(
            RumActionType.CUSTOM,
            "rumAction-${forge.aString()}"
        )
        logger.w("Action added")

        rumMonitor.stopResource(
            resourceName,
            forge.anInt(100, 600),
            forge.aLong(min = 0L),
            forge.aValueFrom(RumResourceKind::class.java)
        )

        span.finish()

        rumMonitor.stopView(viewName)
    }

    private fun deleteAllSdkFiles() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.cacheDir.listFiles()?.forEach {
            it.deleteRecursively()
        }
    }

    private fun stopSdk() {
        Flashcat.stopInstance()
        GlobalDatadogTracer.clear()
    }

    private fun flushAndShutdownExecutors() {
        invokeDatadogMethod("flushAndShutdownExecutors")
    }

    private fun invokeDatadogMethod(method: String, vararg arguments: Any?) {
        val instance = Flashcat.javaClass.getDeclaredField("INSTANCE")
        instance.isAccessible = true
        val callMethod = Flashcat.javaClass.declaredMethods.first { it.name.startsWith(method) }
        callMethod.isAccessible = true
        callMethod.invoke(instance.get(null), *arguments)
    }

    // endregion

    companion object {
        const val ENCRYPTION_MARKER = "~ENCRYPTION-MARKER~"
    }
}
