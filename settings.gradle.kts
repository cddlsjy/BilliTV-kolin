pluginManagement {
    repositories {
        // 腾讯云公共仓库 (替代阿里云 public 和 gradle-plugin)
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 显式指定 Google 仓库的腾讯镜像 (如果公共库不包含最新的 Google 依赖)
        // 注意：腾讯云有时不单独提供 google 镜像，若构建失败，请保留下面的 google()
        // 或者尝试使用阿里云的 google 镜像作为备选，因为腾讯对 google 的镜像支持不如阿里完善
        maven { url = uri("https://maven.aliyun.com/repository/google") }

        // 如果必须纯腾讯，且遇到 Google 依赖问题，可能需要直连或寻找其他源
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 腾讯云公共仓库
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }

        // 同样，对于 Google 依赖，腾讯云镜像可能不如阿里云全。
        // 建议策略：优先腾讯公共库，其次保留 google() 和 mavenCentral() 自动回落
        // 或者如果确定腾讯库里有，可以注释掉原生的，只留 maven 地址
        google()
        mavenCentral()
    }
}

rootProject.name = "BiliTV"
include(":app")
