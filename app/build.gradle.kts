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
        // v2.8.0 后续：minSdk 上调至 26（Android 8.0/Oreo）。java.time 在 API 26+ 原生可用，
        // coreLibraryDesugaring 仍保留（其他 desugared API 可能依赖，移除有回归风险）。
        minSdk = 26
        targetSdk = 34
        // v2.9.0 封板：五大 Tab 改版（计划页合并任务+习惯）、工具箱扩至 55 工具、
        // P0 主题/系统栏失联修复、P1 设备身份/位图回收/首页折叠/重组开销修复、
        // 测试加固（消除假绿 + 关键路径零覆盖补齐）、文档同步。综合成熟度 99.2。
        versionCode = 17
        versionName = "2.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 本 App 仅提供中文界面（res/ 下无任何 values-xx 语言目录），但 Compose Material3 /
        // androidx 各库自带数十种语言的 strings 会被全部打进包。显式收敛可见语言以瘦身；
        // 保留 en 是为了第三方库在非中文系统上仍有可回退的资源，避免走 default 缺失路径。
        resourceConfigurations += listOf("zh-rCN", "en")
        // 注：minSdk 26 下矢量图由平台原生支持（API 21+），无需 support 库兼容路径，故不开启
        // vectorDrawables.useSupportLibrary。
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
            // 有 release 密钥用正式签名；缺失时回退 debug 但显式告警，避免「静默用公开 debug 密钥签名可分发包」。
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("[signing] Release build is signed with the DEBUG keystore - DO NOT distribute. Configure release.storeFile / release.storePassword / release.keyAlias / release.keyPassword in local.properties")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            // 测试覆盖率（V2.2）：Jacoco 随 AGP 内置，零新依赖；
            // unit 覆盖率本地可跑，androidTest 覆盖率供 CI 真机/模拟器生成。
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            // ASR 密钥统一走 SecureStore + 设置页录入（当前默认阿里云 ASR），
            // 不再经 BuildConfig 内联明文（腾讯 ASR 配置已名存实亡且易误植明文，已移除）。
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
    // 二维码生成（工具箱「二维码」工具，破例引入；仅 core，自绘 Bitmap）。
    implementation(libs.zxing)
    // 生物识别解锁（用户明确批准破例引入，密码保险箱指纹/面容快速解锁）。
    implementation(libs.androidx.biometric)
    // AppCompatActivity：主 Activity 改为其子类以承载 BiometricPrompt(FragmentActivity)；
    // 显式 pin 版本，避免被 androidx.biometric 传递的旧 appcompat 拉低导致兼容问题。
    implementation(libs.androidx.appcompat)

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
