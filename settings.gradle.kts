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
        // Opus 编解码库：martoreto/opuscodec（JitPack 单模块 AAR，JNI 包完整 libopus，
        // 各 ABI 的 .so 随包发布），用于替代本机不可用的 MediaCodec Opus 编码器。
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TickClear"
include(":app")
