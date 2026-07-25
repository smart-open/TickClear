import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinKapt)
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
        versionCode = 10
        versionName = "2.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
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
        }
    }

    lint {
        // 收紧质量门禁：错误级问题阻断构建（P8 已修复 4 处 MissingPermission）。
        abortOnError = true
        checkReleaseBuilds = true
        // 仅告警不阻断（历史遗留 i18n/未用资源等，非正确性问题），保留可见性但不卡构建。
        warningsAsErrors = false
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
    implementation(libs.androidx.window)

    // 本地数据库：Room + SQLCipher 加密。SQLCipher 自带 SQLite，排除以避免与 androidx.sqlite 冲突。
    implementation(libs.androidx.room.runtime) {
        exclude(group = "androidx.sqlite", module = "sqlite-framework")
    }
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.accompanist.permissions)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    coreLibraryDesugaring(libs.coreDesugar)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // test-only：android.jar 的 org.json 为 stub（方法抛异常），纯 JVM 单测需真实实现；不进 release 体积
    testImplementation("org.json:json:20231013")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.tooling)
    kspAndroidTest(libs.androidx.room.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}
