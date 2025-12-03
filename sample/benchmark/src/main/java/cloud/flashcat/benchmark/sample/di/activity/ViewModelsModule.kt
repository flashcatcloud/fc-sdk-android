/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.di.activity

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cloud.flashcat.android.log.Logger
import cloud.flashcat.android.rum.RumMonitor
import cloud.flashcat.benchmark.sample.di.common.CoroutineDispatcherQualifier
import cloud.flashcat.benchmark.sample.di.common.CoroutineDispatcherType
import cloud.flashcat.benchmark.sample.ui.logscustom.LogsScreenViewModel
import cloud.flashcat.benchmark.sample.ui.rummanual.RumManualScenarioViewModel
import cloud.flashcat.benchmark.sample.ui.trace.TraceScenarioViewModel
import dagger.Module
import dagger.Provides
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Qualifier
import kotlin.reflect.KClass

@Qualifier
internal annotation class ViewModelQualifier(val viewModelType: KClass<*>)

@Module
internal interface ViewModelsModule {
    companion object {
        @Provides
        @ViewModelQualifier(LogsScreenViewModel::class)
        fun provideLogsScreenViewModelFactory(
            logger: Logger,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogsScreenViewModel(
                    logger = logger,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }

        @Provides
        @ViewModelQualifier(TraceScenarioViewModel::class)
        fun provideTraceScenarioViewModelFactory(
            tracer: Tracer,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TraceScenarioViewModel(
                    tracer = tracer,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }

        @Provides
        @ViewModelQualifier(RumManualScenarioViewModel::class)
        fun provideRumManualScenarioViewModelFactory(
            rumMonitor: RumMonitor,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RumManualScenarioViewModel(
                    rumMonitor = rumMonitor,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }
    }
}
