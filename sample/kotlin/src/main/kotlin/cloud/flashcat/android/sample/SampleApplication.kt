/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.sample

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.compose.enableComposeActionTracking
import cloud.flashcat.android.core.configuration.BackPressureMitigation
import cloud.flashcat.android.core.configuration.BackPressureStrategy
import cloud.flashcat.android.core.configuration.BatchSize
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.core.configuration.UploadFrequency
import cloud.flashcat.android.flags.Flags
import cloud.flashcat.android.flags.FlagsConfiguration
import cloud.flashcat.android.log.Logger
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.log.LogsConfiguration
import cloud.flashcat.android.ndk.NdkCrashReports
import cloud.flashcat.android.okhttp.DatadogEventListener
import cloud.flashcat.android.okhttp.DatadogInterceptor
import cloud.flashcat.android.okhttp.trace.TracingInterceptor
import cloud.flashcat.android.rum.ExperimentalRumApi
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.RumErrorSource
import cloud.flashcat.android.rum.configuration.SlowFramesConfiguration
import cloud.flashcat.android.rum.tracking.NavigationViewTrackingStrategy
import cloud.flashcat.android.sample.account.AccountFragment
import cloud.flashcat.android.sample.data.db.LocalDataSource
import cloud.flashcat.android.sample.data.remote.RemoteDataSource
import cloud.flashcat.android.sample.picture.CoilImageLoader
import cloud.flashcat.android.sample.picture.FrescoImageLoader
import cloud.flashcat.android.sample.picture.PicassoImageLoader
import cloud.flashcat.android.sample.user.UserFragment
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.SessionReplay
import cloud.flashcat.android.sessionreplay.SessionReplayConfiguration
import cloud.flashcat.android.sessionreplay.SessionReplayPrivacy
import cloud.flashcat.android.sessionreplay.SystemRequirementsConfiguration
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.TouchPrivacy
import cloud.flashcat.android.sessionreplay.compose.ComposeExtensionSupport
import cloud.flashcat.android.sessionreplay.material.MaterialExtensionSupport
import cloud.flashcat.android.timber.DatadogTree
import cloud.flashcat.android.trace.DatadogTracing
import cloud.flashcat.android.trace.GlobalDatadogTracer
import cloud.flashcat.android.trace.Trace
import cloud.flashcat.android.trace.TraceConfiguration
import cloud.flashcat.android.trace.opentelemetry.DatadogOpenTelemetry
import cloud.flashcat.android.vendor.sample.LocalServer
import com.facebook.stetho.Stetho
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.opentelemetry.api.GlobalOpenTelemetry
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.security.SecureRandom

/**
 * The main [Application] for the sample project.
 */
@Suppress("MagicNumber", "TooManyFunctions")
class SampleApplication : Application() {

    private val tracedHosts = listOf(
        "datadoghq.com",
        "127.0.0.1"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            DatadogInterceptor.Builder(tracedHosts)
                .build()
        )
        .addNetworkInterceptor(
            TracingInterceptor.Builder(tracedHosts)
                .build()
        )
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()

    private val retrofitClient = Retrofit.Builder()
        .baseUrl("https://api.datadoghq.com/api/v2/")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .addCallAdapterFactory(RxJava3CallAdapterFactory.createSynchronous())
        .client(okHttpClient)
        .build()

    private val retrofitBaseDataSource = retrofitClient.create(RemoteDataSource::class.java)

    private val localServer = LocalServer()

    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
        initializeDatadog()

        initializeTimber()

        initializeImageLoaders()

        localServer.init(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        GlobalRumMonitor.get().addError(
            "Low Memory warning",
            RumErrorSource.SOURCE,
            null
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        GlobalRumMonitor.get().addError(
            "Low Memory warning",
            RumErrorSource.SOURCE,
            null,
            mapOf("trim.level" to level)
        )
    }

    private fun initializeImageLoaders() {
        CoilImageLoader.initialize(this, okHttpClient)
        PicassoImageLoader.initialize(this, okHttpClient)
        FrescoImageLoader.initialize(this, okHttpClient)
    }

    private fun initializeDatadog() {
        val preferences = Preferences.defaultPreferences(this)
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            preferences.getTrackingConsent()
        )

        initializeSessionReplay()
        initializeLogs()
        initializeTraces()

        NdkCrashReports.enable()

        initializeUserInfo(preferences)
        initializeAccountInfo(preferences)

        Rum.enable(createRumConfiguration())

        initializeFlags()

        GlobalRumMonitor.get().debug = true
    }

    private fun initializeUserInfo(preferences: Preferences.DefaultPreferences) {
        Datadog.setUserInfo(
            id = preferences.getUserId() ?: "unknown",
            name = preferences.getUserName(),
            email = preferences.getUserEmail(),
            extraInfo = mapOf(
                UserFragment.GENDER_KEY to preferences.getUserGender(),
                UserFragment.AGE_KEY to preferences.getUserAge()
            )
        )
    }

    private fun initializeAccountInfo(preferences: Preferences.DefaultPreferences) {
        preferences.getAccountId()?.let { id ->
            Datadog.setAccountInfo(
                id = id,
                name = preferences.getAccountName(),
                extraInfo = mapOf(
                    AccountFragment.ROLE_KEY to preferences.getAccountRole(),
                    AccountFragment.AGE_KEY to preferences.getUserAge()
                )
            )
        }
    }

    private fun initializeTraces() {
        val tracesConfig = TraceConfiguration.Builder().apply {
            if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
                useCustomEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
            }
        }.build()
        Trace.enable(tracesConfig)

        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder()
                .withPartialFlushMinSpans(1)
                .build()
        )

        GlobalOpenTelemetry.set(
            DatadogOpenTelemetry(BuildConfig.APPLICATION_ID)
        )
    }

    private fun initializeFlags() {
        val flagsConfig = FlagsConfiguration.Builder().build()
        Flags.enable(flagsConfig)
    }

    private fun initializeLogs() {
        val logsConfig = LogsConfiguration.Builder().apply {
            if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
                useCustomEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
            }
        }.build()
        Logs.enable(logsConfig)
    }

    private fun initializeSessionReplay() {
        val shouldUseFgm = SecureRandom().nextInt(100) < USE_FGM_PCT
        val systemRequirementsConfiguration = SystemRequirementsConfiguration.Builder()
            .setMinRAMSizeMb(1024)
            .setMinCPUCoreNumber(1)
            .build()

        val sessionReplayConfig = SessionReplayConfiguration.Builder(SAMPLE_IN_ALL_SESSIONS)
            .apply {
                if (BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL)
                }

                if (shouldUseFgm) {
                    useFgmConfiguration(this)
                } else {
                    useLegacyConfiguration(this)
                }
            }
            .addExtensionSupport(MaterialExtensionSupport())
            .addExtensionSupport(ComposeExtensionSupport())
            .setSystemRequirements(systemRequirementsConfiguration)
            .build()
        SessionReplay.enable(sessionReplayConfig)
    }

    private fun useFgmConfiguration(builder: SessionReplayConfiguration.Builder) {
        val shouldMaskAll = SecureRandom().nextInt(100) < MASK_SESSION_PCT // 25%

        val imagePrivacy = if (shouldMaskAll) {
            ImagePrivacy.MASK_ALL
        } else {
            ImagePrivacy.MASK_NONE
        }

        val textAndInputPrivacy = if (shouldMaskAll) {
            TextAndInputPrivacy.MASK_ALL
        } else {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        }

        val touchPrivacy = TouchPrivacy.SHOW

        GlobalRumMonitor.get().addAttribute("imagePrivacy", imagePrivacy)
        GlobalRumMonitor.get().addAttribute("textAndInputPrivacy", textAndInputPrivacy)
        GlobalRumMonitor.get().addAttribute("touchPrivacy", touchPrivacy)

        builder.setImagePrivacy(imagePrivacy)
        builder.setTouchPrivacy(touchPrivacy)
        builder.setTextAndInputPrivacy(textAndInputPrivacy)
    }

    @Suppress("Deprecation")
    private fun useLegacyConfiguration(builder: SessionReplayConfiguration.Builder) {
        if (SecureRandom().nextInt(100) <= SESSION_REPLAY_PRIVACY_SAMPLING) {
            builder.setPrivacy(SessionReplayPrivacy.ALLOW)
        } else {
            builder.setPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
        }
    }

    @OptIn(ExperimentalRumApi::class)
    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .apply {
                if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
                }
            }
            .useViewTrackingStrategy(
                NavigationViewTrackingStrategy(
                    R.id.nav_host_fragment,
                    true,
                    SampleNavigationPredicate()
                )
            )
            .setTelemetrySampleRate(100f)
            .trackUserInteractions()
            .trackLongTasks(250L)
            .trackNonFatalAnrs(true)
            .setSlowFramesConfiguration(SlowFramesConfiguration.DEFAULT)
            .setViewEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setActionEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setResourceEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setErrorEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setLongTaskEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setVitalOperationStepEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .trackBackgroundEvents(true)
            .trackAnonymousUser(true)
            .enableComposeActionTracking()
            .collectAccessibility(true)
            .build()
    }

    @SuppressLint("LogNotTimber")
    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR
        )
            .setFirstPartyHosts(tracedHosts)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)

        try {
            configBuilder.useSite(FlashcatSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        configBuilder.setBackpressureStrategy(
            BackPressureStrategy(
                32,
                { Log.w("BackPressure", "THRESHOLD REACHED!") },
                { Log.e("BackPressure", "ITEM DROPPED $it!") },
                BackPressureMitigation.IGNORE_NEWEST
            )
        )

        return configBuilder.build()
    }

    @Suppress("TooGenericExceptionCaught", "CheckInternal")
    private fun initializeTimber() {
        val logger = Logger.Builder()
            .setName("timber")
            .setNetworkInfoEnabled(true)
            .build()

        val device = JsonObject()
        val abis = JsonArray()
        try {
            device.addProperty("api", Build.VERSION.SDK_INT)
            device.addProperty("brand", Build.BRAND)
            device.addProperty("manufacturer", Build.MANUFACTURER)
            device.addProperty("model", Build.MODEL)
            for (abi in Build.SUPPORTED_ABIS) {
                abis.add(abi)
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error setting device and abi properties")
        }
        logger.addAttribute("device", device)
        logger.addAttribute("supported_abis", abis)

        logger.addTag("flavor", BuildConfig.FLAVOR)
        logger.addTag("build_type", BuildConfig.BUILD_TYPE)

        Timber.plant(DatadogTree(logger))
    }

    companion object {
        private const val USE_FGM_PCT = 10
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val MASK_SESSION_PCT = 25
        private const val SESSION_REPLAY_PRIVACY_SAMPLING = 75

        init {
            System.loadLibrary("datadog-native-sample-lib")
        }

        internal const val ATTR_IS_MAPPED = "is_mapped"

        internal fun getViewModelFactory(context: Context): ViewModelProvider.Factory {
            return ViewModelFactory(
                getOkHttpClient(context),
                getRemoteDataSource(context),
                LocalDataSource(context),
                getLocalServer(context)
            )
        }

        internal fun getOkHttpClient(context: Context): OkHttpClient {
            val application = context.applicationContext as SampleApplication
            return application.okHttpClient
        }

        private fun getRemoteDataSource(context: Context): RemoteDataSource {
            val application = context.applicationContext as SampleApplication
            return application.retrofitBaseDataSource
        }

        private fun getLocalServer(context: Context): LocalServer {
            val application = context.applicationContext as SampleApplication
            return application.localServer
        }
    }
}
