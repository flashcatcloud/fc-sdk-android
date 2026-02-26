FC SDK (RUM) 三方库冲突解决方案（最终版）

问题背景

宿主 App 接入 FC SDK 时，SDK 内部三方库可能与宿主已有依赖冲突，导致编译失败或运行时崩溃。需同时解决：

1. OkHttp/Gson 等常见库的版本冲突
2. KronosNTP/JCTools/re2j 等冷门库的 Duplicate class
3. **AndroidX 高版本对宿主 compileSdk / Kotlin 版本的要求过高**

\--------------------------------------------------------------------------------

依赖版本约束总表

| 依赖                  | 当前版本      | compileSdk 要求 | 冲突风险 | 公开 API 暴露 | 策略                            |
| --------------------- | ------------- | --------------- | -------- | ------------- | ------------------------------- |
| OkHttp                | 4.12.0        | —               | 🔴 极高   | ✅ 深度暴露    | compileOnly + 降到 4.9.0        |
| Gson                  | 2.10.1        | —               | 🔴 极高   | ✅ 深度暴露    | compileOnly + 降到 2.8.9        |
| **AndroidX Core**     | **1.17.0**    | **36**          | 🔴 极高   | 部分          | **降到 1.12.0** (compileSdk 34) |
| AndroidX Navigation   | 2.7.7         | 34              | 🟡 中     | ✅ 暴露        | compileOnly + 降到 2.5.3        |
| AndroidX WorkManager  | 2.8.1         | 33              | 🟡 中     | ✅ 暴露        | compileOnly + 降到 2.7.1        |
| AndroidX Metrics      | 1.0.0-beta03  | 34              | 🟡 中     | ❌ 内部        | 降到 1.0.0-alpha04              |
| AndroidX RecyclerView | 1.3.2         | 31              | 🟢 低     | ❌ 内部        | 保持/可降到 1.2.1               |
| AndroidX Fragment     | 1.2.4         | —               | 🟢 低     | ❌ 内部        | 保持                            |
| AndroidX Collection   | 1.4.5         | 28              | 🟢 低     | ❌ 内部        | 保持                            |
| AndroidX Annotation   | 1.9.1         | —               | 🟢 低     | ❌ 内部        | 保持                            |
| KronosNTP             | 0.0.1-alpha11 | —               | 🟡 中     | ❌ 内部        | **Shadow relocate**             |
| JCTools               | 3.3.0         | —               | 🟡 中     | ❌ 内部        | **Shadow relocate**             |
| re2j                  | 1.7           | —               | 🟡 中     | ❌ 内部        | **Shadow relocate**             |
| OpenTelemetry API     | 1.40.0        | —               | 🟡 中     | ✅ 暴露        | compileOnly                     |

IMPORTANT

**最大问题**：`androidx.core:core:1.17.0` 要求 compileSdk 36、AGP 8.9.1+，绝大多数宿主项目达不到。必须降版本。

\--------------------------------------------------------------------------------

修改方案

一、OkHttp / Gson：`compileOnly` + 降版本

SDK 编译使用低版本，由宿主提供具体版本。

版本变更

```
# gradle/libs.versions.toml

-okHttp = "4.12.0"

+okHttp = "4.9.0"

-gson = "2.10.1"

+gson = "2.8.9"
```

依赖声明变更

需修改以下模块的

build.gradle.kts，将 OkHttp/Gson 从 `implementation` 改为 `compileOnly`：

| 模块                     | OkHttp             | Gson               |
| ------------------------ | ------------------ | ------------------ |
| dd-sdk-android-core      | impl → compileOnly | impl → compileOnly |
| dd-sdk-android-rum       | impl → compileOnly | impl → compileOnly |
| dd-sdk-android-trace     | —                  | impl → compileOnly |
| dd-sdk-android-webview   | —                  | impl → compileOnly |
| dd-sdk-android-ndk       | impl → compileOnly | —                  |
| dd-sdk-android-profiling | impl → compileOnly | impl → compileOnly |
| dd-sdk-android-okhttp    | impl → compileOnly | —                  |

WARNING

**降到 OkHttp 4.9.0 需验证**：SDK 内部使用的 Kotlin 扩展 API（`toMediaTypeOrNull`、`toRequestBody` 等）在 4.9.0 中存在，但部分新 API 可能不兼容，需要编译验证。

**降到 Gson 2.8.9 需验证**：确认 SDK 内部没有使用 2.9+ 新增的 API。

\--------------------------------------------------------------------------------

二、AndroidX 依赖：降版本 + 部分改 `compileOnly`

目标：兼容 compileSdk 33 的宿主项目

```
# gradle/libs.versions.toml

-androidXCore = "1.17.0"

+androidXCore = "1.12.0"          # compileSdk 34, 向下兼容 33



-androidXNavigation = "2.7.7"

+androidXNavigation = "2.5.3"     # compileSdk 33



-androidXWorkManager = "2.8.1"

+androidXWorkManager = "2.7.1"    # compileSdk 31



-androidXMetrics = "1.0.0-beta03"

+androidXMetrics = "1.0.0-alpha04" # compileSdk 33



-androidXRecyclerView = "1.3.2"

+androidXRecyclerView = "1.2.1"    # compileSdk 31



-androidXCollection = "1.4.5"

+androidXCollection = "1.2.0"      # compileSdk 28
```

Navigation / WorkManager → `compileOnly`

这两个库在 SDK 公开 API 中有暴露（`NavigationViewTrackingStrategy` 继承了 `NavController.OnDestinationChangedListener`，`UploadWorker` 继承了 `Worker`），但宿主不一定使用 Navigation 组件：

```
# features/dd-sdk-android-rum/build.gradle.kts

-    implementation(libs.bundles.androidXNavigation)

+    compileOnly(libs.bundles.androidXNavigation)



# dd-sdk-android-core/build.gradle.kts

-    implementation(libs.androidXWorkManager)

+    compileOnly(libs.androidXWorkManager)
```

NOTE

Navigation 改为 `compileOnly` 后，不使用 `NavigationViewTrackingStrategy` 的宿主无需引入 Navigation 库。WorkManager 改为 `compileOnly` 后，需在文档中说明宿主必须引入 `androidx.work:work-runtime`。

SDK 自身 compileSdk 不变

SDK 自身的 `compileSdk = 36` 保持不变（SDK 编译环境由 SDK 开发者控制）。降低的是**依赖库版本**，使得宿主在 `compileSdk 33` 或 `34` 时也能正常编译集成。

\--------------------------------------------------------------------------------

三、KronosNTP / JCTools / re2j → Shadow Relocate

这三个库在 SDK 内部使用，不暴露公开 API，适合做 relocate。

重定位映射

```
com.lyft.kronos.*    → com.datadog.vendor.kronos.*

org.jctools.*        → com.datadog.vendor.jctools.*

com.google.re2j.*    → com.datadog.vendor.re2j.*
```

实现方式

使用 [Shadow Gradle Plugin](https://www.google.com/url?sa=E&q=https%3A%2F%2Fgithub.com%2FGradleUp%2Fshadow) 或手动 jar 重打包：

1. 在根项目添加 Shadow 插件
2. 为 `dd-sdk-android-core` 和 `features:dd-sdk-android-trace` 配置 relocate 规则
3. 发布时自动将 relocate 后的类打包进 AAR

CAUTION

Shadow Plugin 对 Android AAR 的支持有限，推荐方式是：先将这些库 Shadow 为独立的 jar/aar，再作为本地依赖引入。或者直接将源码 fork 后改包名放入 SDK 项目内部。

\--------------------------------------------------------------------------------

四、OpenTelemetry API → `compileOnly`

仅在 `trace-otel` 模块中通过 `api()` 暴露，改为 `compileOnly`：

```
# features/dd-sdk-android-trace-otel/build.gradle.kts

-    api(libs.openTelemetryApi)

+    compileOnly(libs.openTelemetryApi)
```

\--------------------------------------------------------------------------------

修改汇总

版本变更 (libs.versions.toml)

| 依赖                 | 当前         | 目标          | 原因             |
| -------------------- | ------------ | ------------- | ---------------- |
| okHttp               | 4.12.0       | 4.9.0         | 降低版本限制     |
| gson                 | 2.10.1       | 2.8.9         | 降低版本限制     |
| androidXCore         | 1.17.0       | 1.12.0        | compileSdk 36→34 |
| androidXNavigation   | 2.7.7        | 2.5.3         | compileSdk 34→33 |
| androidXWorkManager  | 2.8.1        | 2.7.1         | compileSdk 33→31 |
| androidXMetrics      | 1.0.0-beta03 | 1.0.0-alpha04 | compileSdk 34→33 |
| androidXRecyclerView | 1.3.2        | 1.2.1         | compileSdk 31    |
| androidXCollection   | 1.4.5        | 1.2.0         | compileSdk 28    |

依赖声明变更

| 依赖          | 变更                | 涉及模块                             |
| ------------- | ------------------- | ------------------------------------ |
| OkHttp        | impl → compileOnly  | core, rum, ndk, profiling, okhttp    |
| Gson          | impl → compileOnly  | core, rum, trace, webview, profiling |
| Navigation    | impl → compileOnly  | rum                                  |
| WorkManager   | impl → compileOnly  | core                                 |
| OpenTelemetry | api → compileOnly   | trace-otel                           |
| KronosNTP     | impl → **relocate** | core                                 |
| JCTools       | impl → **relocate** | trace (via traceCore bundle)         |
| re2j          | impl → **relocate** | trace (via traceCore bundle)         |

\--------------------------------------------------------------------------------

实施优先级

| 阶段   | 改动                                                    | 工作量 |
| ------ | ------------------------------------------------------- | ------ |
| **P0** | libs.versions.toml 降版本 + 编译验证                    | 1 天   |
| **P0** | OkHttp/Gson 改 compileOnly + 运行时检查                 | 1 天   |
| **P1** | AndroidX 降版本 + Navigation/WorkManager 改 compileOnly | 2 天   |
| **P1** | OpenTelemetry 改 compileOnly                            | 0.5 天 |
| **P2** | KronosNTP / JCTools / re2j Shadow relocate              | 3-5 天 |

验证计划

1. 降版本后执行 `./gradlew assembleLibrariesRelease` 确认 SDK 编译通过
2. 执行 `./gradlew unitTestRelease` 确认单元测试通过
3. 在 `sample/vendor-lib` 中添加宿主冲突依赖（OkHttp 4.9/4.11/5.0-alpha, Gson 2.8.6/2.10）模拟编译
4. 宿主集成测试：使用 compileSdk 33 的宿主项目接入修改后的 SDK

