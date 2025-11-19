# Flashcat RUM SDK for Android

> An Android SDK for real user monitoring, logging, and tracing with the Flashcat platform.

[![Maven Central](https://img.shields.io/maven-central/v/cloud.flashcat.rum/flashcat-rum-android)](https://search.maven.org/artifact/cloud.flashcat.rum/flashcat-rum-android)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## 📢 About This Project

This SDK is a **fork** of the [Datadog Android SDK](https://github.com/DataDog/fc-sdk-android) (Apache 2.0 License), adapted and maintained by **Flashcat (Beijing) Technology Co., Ltd.** for use with the Flashcat RUM platform.

We are deeply grateful to Datadog, Inc. for their excellent open-source work. This fork exists to:
- Integrate with Flashcat's RUM backend
- Add Flashcat-specific features and optimizations
- Provide localized support for Chinese developers

**Disclaimer**: Flashcat has no official affiliation with Datadog, Inc. Datadog is a registered trademark of Datadog, Inc.

---

## 🚀 Getting Started

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("cloud.flashcat.rum:flashcat-rum-android:1.0.0")
}
```

### Quick Start

```kotlin
// In your Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = FlashcatConfig.Builder(
            clientToken = "YOUR_CLIENT_TOKEN",
            env = "prod",
            variant = "release"
        )
            .useSite(FlashcatSite.PRODUCTION)  // Default
            .build()
        
        FlashcatRum.initialize(
            context = this,
            configuration = config,
            trackingConsent = TrackingConsent.GRANTED
        )
    }
}
```

### Environments

- **Production**: `FlashcatSite.PRODUCTION` - Data sent to `https://browser.flashcat.cloud`
- **Custom**: Use `.useCustomEndpoint("https://your-server.com")` for local development or internal testing

---

## 📚 Documentation

- [Getting Started Guide](https://flashcat.cloud/docs/sdk/android/getting-started)
- [API Reference](https://flashcat.cloud/docs/sdk/android/api)
- [Migration from Datadog](MIGRATION.md)
- [Original Datadog Documentation](https://docs.datadoghq.com/real_user_monitoring/android/)

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## 📄 License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details.

**Attribution**: This project includes software developed by Datadog, Inc. See [NOTICE](NOTICE) for full attribution.

---

_继续原 README 内容..._


## 📦 Integrations

### Log Integrations

#### Timber

If your existing codebase is using Timber, you can forward all those logs to Flashcat automatically by using the [dedicated library](integrations/fc-sdk-android-timber/README.md).

### RUM Integrations

#### Coil

If you use Coil to load images in your application, see the [dedicated library](integrations/fc-sdk-android-coil/README.md).

#### Fresco

If you use Fresco to load images in your application, see the [dedicated library](integrations/fc-sdk-android-fresco/README.md).

#### Glide

If you use Glide to load images in your application, see the [dedicated library](integrations/fc-sdk-android-glide/README.md).

#### Jetpack Compose

If you use Jetpack Compose in your application, see the [dedicated library](integrations/fc-sdk-android-compose/README.md).

#### SQLDelight

If you use SQLDelight in your application, see the [dedicated library](integrations/fc-sdk-android-sqldelight/README.md).

#### RxJava

If you use RxJava in your application, see the [dedicated library](integrations/fc-sdk-android-rx/README.md).

#### Kotlin Coroutines

See the dedicated library with [extensions for RUM](integrations/fc-sdk-android-rum-coroutines/README.md) and [extensions for Trace](integrations/fc-sdk-android-trace-coroutines/README.md).

### Network Client Integrations

#### Retrofit

Use Retrofit with the `OkHttpClient` instrumented with the Flashcat SDK for RUM and APM information:

```kotlin
val retrofitClient = Retrofit.Builder()
    .client(okHttpClient)
    // …
    .build()
```

#### Picasso

Use Picasso with the instrumented `OkHttpClient`:

```kotlin
val picasso = Picasso.Builder(context)
    .downloader(OkHttp3Downloader(okHttpClient))
    // …
    .build()
Picasso.setSingletonInstance(picasso)
```

#### Apollo (GraphQL)

Use Apollo with the instrumented `OkHttpClient`:

```kotlin
val apolloClient = ApolloClient.builder()
    .okHttpClient(okHttpClient)
    .serverUrl(<APOLLO_SERVER_URL>)
    .build()
```

---

## 🔧 Troubleshooting

If you encounter any issues when using the Flashcat SDK for Android, please:

1. Check the [troubleshooting guide](docs/advanced_troubleshooting.md)
2. Review [existing issues](https://github.com/flashcat-cloud/fc-sdk-android/issues)
3. Contact [Flashcat Support](https://flashcat.cloud/support)

---

## 📜 Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history and updates.

---

**Maintained with ❤️ by [Flashcat Team](https://flashcat.cloud)**

