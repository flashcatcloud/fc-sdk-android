/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.internal.time.TimeProvider
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.MapperTypeWrapper
import cloud.flashcat.android.sessionreplay.SessionReplayInternalCallback
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.internal.LifecycleCallback
import cloud.flashcat.android.sessionreplay.internal.SessionReplayLifecycleCallback
import cloud.flashcat.android.sessionreplay.internal.TouchPrivacyManager
import cloud.flashcat.android.sessionreplay.internal.async.RecordedDataQueueHandler
import cloud.flashcat.android.sessionreplay.internal.processor.MutationResolver
import cloud.flashcat.android.sessionreplay.internal.processor.RecordedDataProcessor
import cloud.flashcat.android.sessionreplay.internal.processor.ResourceQueueImpl
import cloud.flashcat.android.sessionreplay.internal.processor.RumContextDataHandler
import cloud.flashcat.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import cloud.flashcat.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import cloud.flashcat.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import cloud.flashcat.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.BitmapPool
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.ImageTypeResolver
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.ResourceResolver
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache
import cloud.flashcat.android.sessionreplay.internal.recorder.resources.WebPImageCompression
import cloud.flashcat.android.sessionreplay.internal.resources.ResourceDataStoreManager
import cloud.flashcat.android.sessionreplay.internal.storage.RecordWriter
import cloud.flashcat.android.sessionreplay.internal.storage.ResourcesWriter
import cloud.flashcat.android.sessionreplay.internal.utils.DrawableUtils
import cloud.flashcat.android.sessionreplay.internal.utils.PathUtils
import cloud.flashcat.android.sessionreplay.internal.utils.RumContextProvider
import cloud.flashcat.android.sessionreplay.recorder.OptionSelectorDetector
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DefaultColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DefaultViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.DefaultViewIdentifierResolver
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.concurrent.ConcurrentLinkedQueue

internal class SessionReplayRecorder : OnWindowRefreshedCallback, Recorder {

    private val appContext: Application
    private val textAndInputPrivacy: TextAndInputPrivacy
    private val imagePrivacy: ImagePrivacy
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
    private val windowInspector: WindowInspector
    private val windowCallbackInterceptor: WindowCallbackInterceptor
    private val sessionReplayLifecycleCallback: LifecycleCallback
    private val recordedDataQueueHandler: RecordedDataQueueHandler
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor
    private val internalLogger: InternalLogger
    private val uiHandler: Handler
    private var shouldRecord = false

    @Suppress("LongParameterList")
    constructor(
        appContext: Application,
        resourcesWriter: ResourcesWriter,
        rumContextProvider: RumContextProvider,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        touchPrivacyManager: TouchPrivacyManager,
        recordWriter: RecordWriter,
        timeProvider: TimeProvider,
        mappers: List<MapperTypeWrapper<*>> = emptyList(),
        customOptionSelectorDetectors: List<OptionSelectorDetector> = emptyList(),
        customDrawableMappers: List<DrawableToColorMapper>,
        windowInspector: WindowInspector = WindowInspector,
        sdkCore: FeatureSdkCore,
        resourceDataStoreManager: ResourceDataStoreManager,
        dynamicOptimizationEnabled: Boolean,
        internalCallback: SessionReplayInternalCallback
    ) {
        val internalLogger = sdkCore.internalLogger
        val rumContextDataHandler = RumContextDataHandler(
            rumContextProvider,
            timeProvider,
            internalLogger
        )

        val processor = RecordedDataProcessor(
            resourceDataStoreManager,
            resourcesWriter,
            recordWriter,
            MutationResolver(internalLogger)
        )

        this.appContext = appContext
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = RecordedDataQueueHandler(
            processor = processor,
            rumContextDataHandler = rumContextDataHandler,
            internalLogger = internalLogger,
            executorService = sdkCore.createSingleThreadExecutorService(
                "sr-event-processing"
            ),
            recordedDataQueue = ConcurrentLinkedQueue()
        )

        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper =
            DrawableToColorMapper.getDefault(customDrawableMappers)

        val defaultVWM = ViewWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val bitmapCachesManager = BitmapCachesManager(
            bitmapPool = BitmapPool(),
            resourcesLRUCache = ResourcesLRUCache(),
            logger = internalLogger
        )

        val resourceResolver = ResourceResolver(
            recordedDataQueueHandler = recordedDataQueueHandler,
            pathUtils = PathUtils(internalLogger, bitmapCachesManager),
            bitmapCachesManager = bitmapCachesManager,
            drawableUtils = DrawableUtils(
                internalLogger,
                bitmapCachesManager,
                sdkCore.createSingleThreadExecutorService("drawables")
            ),
            logger = internalLogger,
            md5HashGenerator = MD5HashGenerator(internalLogger),
            webPImageCompression = WebPImageCompression(internalLogger)
        )

        this.viewOnDrawInterceptor = ViewOnDrawInterceptor(
            internalLogger = internalLogger,
            onDrawListenerProducer = DefaultOnDrawListenerProducer(
                snapshotProducer = SnapshotProducer(
                    DefaultImageWireframeHelper(
                        logger = internalLogger,
                        resourceResolver = resourceResolver,
                        viewIdentifierResolver = viewIdentifierResolver,
                        viewUtilsInternal = ViewUtilsInternal(),
                        imageTypeResolver = ImageTypeResolver()
                    ),
                    TreeViewTraversal(
                        mappers = mappers,
                        defaultViewMapper = defaultVWM,
                        decorViewMapper = DecorViewMapper(defaultVWM, viewIdentifierResolver),
                        hiddenViewMapper = HiddenViewMapper(
                            viewBoundsResolver = viewBoundsResolver,
                            viewIdentifierResolver = viewIdentifierResolver
                        ),
                        viewUtilsInternal = ViewUtilsInternal(),
                        internalLogger = internalLogger
                    ),
                    ComposedOptionSelectorDetector(
                        customOptionSelectorDetectors + DefaultOptionSelectorDetector()
                    ),
                    touchPrivacyManager,
                    internalLogger = internalLogger
                ),
                recordedDataQueueHandler = recordedDataQueueHandler,
                sdkCore = sdkCore,
                dynamicOptimizationEnabled = dynamicOptimizationEnabled
            ),
            touchPrivacyManager = touchPrivacyManager
        )
        this.windowCallbackInterceptor = WindowCallbackInterceptor(
            recordedDataQueueHandler,
            viewOnDrawInterceptor,
            timeProvider,
            rumContextProvider,
            internalLogger,
            imagePrivacy,
            textAndInputPrivacy,
            touchPrivacyManager
        )
        this.sessionReplayLifecycleCallback = SessionReplayLifecycleCallback(this)

        // Register fragment lifecycle callbacks for clients initialized after the Application.onCreate phase
        internalCallback.getCurrentActivity()?.let {
            sessionReplayLifecycleCallback.setCurrentWindow(it)
            sessionReplayLifecycleCallback.registerFragmentLifecycleCallbacks(it)
        }

        // Expose this object so it can be used to dynamically add resources
        internalCallback.setResourceQueue(ResourceQueueImpl(this.recordedDataQueueHandler))

        this.uiHandler = Handler(Looper.getMainLooper())
        this.internalLogger = internalLogger
    }

    @VisibleForTesting
    @Suppress("LongParameterList")
    constructor(
        appContext: Application,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        windowInspector: WindowInspector = WindowInspector,
        windowCallbackInterceptor: WindowCallbackInterceptor,
        sessionReplayLifecycleCallback: LifecycleCallback,
        viewOnDrawInterceptor: ViewOnDrawInterceptor,
        recordedDataQueueHandler: RecordedDataQueueHandler,
        uiHandler: Handler,
        internalLogger: InternalLogger
    ) {
        this.appContext = appContext
        this.textAndInputPrivacy = textAndInputPrivacy
        this.imagePrivacy = imagePrivacy
        this.customOptionSelectorDetectors = customOptionSelectorDetectors
        this.windowInspector = windowInspector
        this.recordedDataQueueHandler = recordedDataQueueHandler
        this.viewOnDrawInterceptor = viewOnDrawInterceptor
        this.windowCallbackInterceptor = windowCallbackInterceptor
        this.sessionReplayLifecycleCallback = sessionReplayLifecycleCallback
        this.uiHandler = uiHandler
        this.internalLogger = internalLogger
    }

    override fun stopProcessingRecords() {
        recordedDataQueueHandler.clearAndStopProcessingQueue()
    }

    override fun registerCallbacks() {
        appContext.registerActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    override fun unregisterCallbacks() {
        appContext.unregisterActivityLifecycleCallbacks(sessionReplayLifecycleCallback)
    }

    override fun resumeRecorders() {
        uiHandler.post {
            shouldRecord = true
            val windows = sessionReplayLifecycleCallback.getCurrentWindows()
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }

    override fun stopRecorders() {
        uiHandler.post {
            viewOnDrawInterceptor.stopIntercepting()
            windowCallbackInterceptor.stopIntercepting()
            shouldRecord = false
        }
    }

    @MainThread
    override fun onWindowsAdded(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.intercept(windows, appContext)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) {
        if (shouldRecord) {
            val decorViews = windowInspector.getGlobalWindowViews(internalLogger)
            windowCallbackInterceptor.stopIntercepting(windows)
            viewOnDrawInterceptor.intercept(decorViews, textAndInputPrivacy, imagePrivacy)
        }
    }
}
