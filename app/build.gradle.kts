import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tickclear.app"
    compileSdk = 34

    // 发布签名：从 local.properties 读取 release.* ；缺失时回退 debug，保证本地仍可产出 release 包（不内联任何密钥）。
    val releaseProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
    val hasReleaseKey = listOf("release.storeFile", "release.storePassword", "release.keyAlias", "release.keyPassword")
        .all { releaseProps[it] != null }

    defaultConfig {
        applicationId = "com.tickclear.app"
        minSdk = 24
        targetSdk = 34
        // v2.8.0 封板：V2.8X 全部增量（小组件/习惯/标签/皮肤/语音历史/常驻唤醒/
        // 通知三态可靠性/助手闪退根因/轻提示 3 秒）随本版本一并发布。
        versionCode = 16
        versionName = "2.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // V2.8X++ 语音长期方案：改用 theeasiestway/android-opus-codec（本地 libs/opus.aar，封装官方 libopus 1.3.1），
        // 其预编译 .so 覆盖 armeabi-v7a / arm64-v8a / x86 / x86_64 全部 ABI（含 64 位），
        // 彻底解决原 martoreto/opuscodec 仅含 32 位 libsenz.so、arm64 设备 dlopen 失败导致语音不可用的根因。
        // 不再需要「剔除 arm64-v8a 目录 / 32 位回退」妥协方案，arm64 设备原生 64 位运行。
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseProps["release.storeFile"].toString())
                storePassword = releaseProps["release.storePassword"].toString()
                keyAlias = releaseProps["release.keyAlias"].toString()
                keyPassword = releaseProps["release.keyPassword"].toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有 release 密钥用正式签名，否则回退 debug（仅影响本地构建，不入库）。
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            // 测试覆盖率（V2.2）：Jacoco 随 AGP 内置，零新依赖；
            // unit 覆盖率本地可跑，androidTest 覆盖率供 CI 真机/模拟器生成。
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            // 仅 debug 注入本地 ASR 密钥（local.properties 含明文凭据，已 gitignore，禁止入库/禁止提交到 release）。
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) load(f.inputStream())
            }
            val secretId = localProps["tencent.asr.secretId"]?.toString().orEmpty()
            val secretKey = localProps["tencent.asr.secretKey"]?.toString().orEmpty()
            val region = localProps["tencent.asr.region"]?.toString().orEmpty().ifEmpty { "ap-guangzhou" }
            buildConfigField("String", "TENCENT_ASR_SECRET_ID", "\"$secretId\"")
            buildConfigField("String", "TENCENT_ASR_SECRET_KEY", "\"$secretKey\"")
            buildConfigField("String", "TENCENT_ASR_REGION", "\"$region\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 本机未安装 NDK，AGP 的 stripDebugDebugSymbols 无 strip 工具可用，
        // 会对第三方原生库报 "Unable to strip"。此处显式保留这些库的调试符号，
        // 让 strip 任务无可剥离对象，从而消除告警（零新依赖、无需 NDK）。
        // 若后续安装 NDK 并声明 ndkVersion，可改为真正去符号以进一步缩小包体。
        jniLibs {
            keepDebugSymbols += "**/libsqlcipher.so"
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
            // Opus 编解码库（theeasiestway/android-opus-codec 的 libopus.so / libeasyopus.so / libopusenc.so）；
            // 保留调试符号以避免 strip 告警（本机 NDK 可用时亦可正常 strip，此处保守保留）。
            keepDebugSymbols += "**/libopus.so"
            keepDebugSymbols += "**/libeasyopus.so"
            keepDebugSymbols += "**/libopusenc.so"
            // 注：不再剔除 arm64-v8a 目录——该库自带 arm64-v8a 的 libopus.so，arm64 设备原生 64 位运行。
        }
    }

    lint {
        // 收紧质量门禁：错误级问题阻断构建（P8 已修复 4 处 MissingPermission）。
        abortOnError = true
        checkReleaseBuilds = true
        // 仅告警不阻断（历史遗留 i18n/未用资源等，非正确性问题），保留可见性但不卡构建。
        warningsAsErrors = false
        // 锁定 targetSdk 34，暂不升 35 以免行为变更；OldTargetApi 告警非正确性问题，用 disable += 抑制该检查。
        // 注意：AGP 8.x Kotlin DSL 中 disable/enable/warning/error/fatal 是 MutableSet<String> 属性，须用 `disable += "id"`，
        // 不能用 Groovy 遗留的方法式 `disable("id")`（会 Unresolved reference 编译失败）。
        disable += "OldTargetApi"
    }

    testOptions {
        unitTests.all {
            // 消除单测时的 JVM 警告：
            //   "OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes
            //    because bootstrap classpath has been appended"
            // 根因：mockk-inline 的 byte-buddy agent 会向 bootstrap classpath 追加字节码，
            // 与 JDK 默认开启的 CDS（Class Data Sharing）冲突，JVM 降级共享并打印警告。
            // 关闭测试 JVM 的 CDS 即可（仅影响单测进程启动的毫秒级共享收益，无功能影响）。
            it.jvmArgs("-Xshare:off")
        }
    }
}

// Room 迁移可审计性（V2.1）：将各版本 schema 导出到 app/schemas 并提交仓库，
// 后续破坏性迁移可据此校验/生成 Migration，避免盲目 fallbackToDestructiveMigration。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // 注：不引入 androidx.window —— 自适应断点由 ui/navigation/WindowSize.kt 的 AppSizeClass
    // 基于 LocalConfiguration.screenWidthDp 自建，零依赖即可满足手机/平板两档需求。

    // 本地数据库：Room + SQLCipher 加密。SQLCipher 自带 SQLite，排除以避免与 androidx.sqlite 冲突。
    implementation(libs.androidx.room.runtime) {
        exclude(group = "androidx.sqlite", module = "sqlite-framework")
    }
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // 网络层一律 OkHttp 裸调 + org.json 手工解析（ASR / LLM / 小智 WebSocket 均如此）。
    // 曾声明的 retrofit + retrofit-kotlinx-serialization-converter 在 app/src 中零引用，
    // 属死依赖，已移除以收敛包体并对齐「零新依赖」纪律。
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.accompanist.permissions)

    // Opus 编解码（theeasiestway/android-opus-codec，本地 libs/opus.aar，封装官方 libopus 1.3.1）。
    // 覆盖 armeabi-v7a / arm64-v8a / x86 / x86_64 全 ABI 的 libopus.so，arm64 设备原生 64 位可用，
    // 彻底规避原 martoreto/opuscodec 仅 32 位 libsenz.so 导致 arm64 dlopen 失败、语音不可用的根因。
    implementation(files("libs/opus.aar"))

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.coreDesugar)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // test-only：android.jar 的 org.json 为 stub（方法抛异常），纯 JVM 单测需真实实现；不进 release 体积
    testImplementation(libs.orgJson)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.tooling)
    kspAndroidTest(libs.androidx.room.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}
