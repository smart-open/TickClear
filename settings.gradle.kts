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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 注：Opus 编解码走 app/libs/opus.aar（theeasiestway，全 ABI 含 arm64）本地文件依赖，
        // 无需远端仓库。原 JitPack 仓库是 martoreto/opuscodec 时期的残留，该方案因仅打包
        // 32 位 libsenz.so 导致 arm64 设备 dlopen 失败已废弃，仓库一并移除（少一次远端解析探测）。
    }
}

rootProject.name = "TickClear"
include(":app")
