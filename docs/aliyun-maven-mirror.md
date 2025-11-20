# 使用阿里云 Maven 镜像

对于中国大陆用户，如果无法访问 Maven Central，可以配置使用阿里云 Maven 镜像来下载 FlashCat SDK。

## 配置方法

### 方法一：在项目的 build.gradle 或 settings.gradle 中配置

```groovy
// settings.gradle.kts 或 build.gradle.kts
repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/google")
    }
    google()
    mavenCentral()
}
```

### 方法二：全局配置（推荐）

在用户目录下创建或编辑 `~/.gradle/init.gradle` 文件：

```groovy
allprojects {
    repositories {
        maven {
            url 'https://maven.aliyun.com/repository/public'
        }
        maven {
            url 'https://maven.aliyun.com/repository/google'
        }
    }
}
```

## 添加 FlashCat SDK 依赖

配置好镜像后，在您的应用模块的 `build.gradle` 文件中添加依赖：

```groovy
dependencies {
    // 核心库
    implementation "com.flashcat:fc-sdk-android-core:1.0.0"

    // RUM (Real User Monitoring)
    implementation "com.flashcat:fc-sdk-android-rum:1.0.0"

    // 日志收集
    implementation "com.flashcat:fc-sdk-android-logs:1.0.0"

    // 链路追踪
    implementation "com.flashcat:fc-sdk-android-trace:1.0.0"
}
```

## 注意事项

1. FlashCat SDK 将同时发布到 Maven Central 和阿里云镜像
2. 建议中国大陆用户优先使用阿里云镜像以获得更快的下载速度
3. 如果遇到同步延迟，可以稍后重试或临时使用 Maven Central

## 常见问题

### Q: 如何验证镜像配置是否生效？

A: 在构建时查看 Gradle 输出日志，可以看到依赖下载的源地址。

### Q: 阿里云镜像的同步频率是多少？

A: 阿里云镜像通常会在新版本发布后的 1-2 小时内同步。

### Q: 可以混用多个仓库吗？

A: 可以，Gradle 会按照配置顺序依次尝试各个仓库。
