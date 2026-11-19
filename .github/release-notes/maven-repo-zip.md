FlashCat Android SDK ${VERSION} 的 Maven 仓库包。解压后就是一个本地 Maven 仓库，不需要从 Maven Central 下载 SDK。

包内模块（版本均为 ${VERSION}，文件带 GPG 签名 `.asc`）：

${MODULES}

## 接入步骤

### 1. 解压

下载 `fc-sdk-android-${VERSION}-maven-repo.zip`，解压到项目根目录（和 `settings.gradle(.kts)` 同级），目录名用 `fc-maven-repo`：

```
your-project/
├── fc-maven-repo/
│   └── cloud/flashcat/...
├── settings.gradle.kts
└── app/
```

### 2. 添加仓库

按项目现在声明仓库的位置二选一，**只加一处**。如果 `settings.gradle(.kts)` 里有 `dependencyResolutionManagement { repositories { … } }`，用写法 A；如果仓库写在根目录 `build.gradle(.kts)` 的 `allprojects { repositories { … } }` 里，用写法 B。

`google()`、`mavenCentral()` 保留不动，SDK 依赖的 OkHttp、Kotlin 等第三方库仍从那里下载。

**写法 A：`settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url = uri("fc-maven-repo") } }
            filter { includeGroup("cloud.flashcat") }
        }
    }
}
```

`settings.gradle`（Groovy）里把 `url = uri("fc-maven-repo")` 换成 `url "fc-maven-repo"`，其余相同。

**写法 B：根目录 `build.gradle`（Groovy）**

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url "${rootDir}/fc-maven-repo" } }
            filter { includeGroup "cloud.flashcat" }
        }
    }
}
```

`allprojects` 里的路径必须带 `${rootDir}`，否则每个子模块会在自己的目录下找 `fc-maven-repo`。`build.gradle.kts` 写法同 A 中的代码块，`url` 写成 `uri("$rootDir/fc-maven-repo")`。

### 3. 修改依赖版本

依赖坐标不变，版本改成 ${VERSION}：

```kotlin
implementation("cloud.flashcat:dd-sdk-android-core:${VERSION}")
implementation("cloud.flashcat:dd-sdk-android-rum:${VERSION}")
```

## 注意：本包以外的 cloud.flashcat 模块

上面的 `exclusiveContent` 会让 `cloud.flashcat` 下的**所有**模块只从 `fc-maven-repo` 查找。如果项目还依赖本包里没有的模块（例如已停止发布的 `dd-sdk-android-timber:0.7.4`），构建会报找不到依赖。

这种情况下，把 `exclusiveContent { … }` 换成下面的普通仓库，并**放在 `mavenCentral()` 前面**：

```kotlin
maven {
    url = uri("fc-maven-repo")
    content { includeGroup("cloud.flashcat") }
}
mavenCentral()
```

这样包里有的模块从 `fc-maven-repo` 取，没有的继续从 Maven Central 取。不同版本的 SDK 模块混用可能有兼容问题，建议尽快去掉已停止发布的模块。
