pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// JDK 17 已通过 apt 安装并设为 JAVA_HOME，无需 foojay 工具链自动下载
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UnlockLearn"

// 纯 JVM 模块（可独立测试，无 Android 依赖）
include(":core:scheduler")
include(":core:quizbank")

// Android 模块
include(":app")
