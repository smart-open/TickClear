# TickClear ProGuard 规则
# 保留 SQLCipher（加密数据库核心）
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }

# 保留 Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# 保留 Hilt / Dagger 生成类
-keep class **_HiltModules.* { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# 保留 kotlinx.serialization 数据类（@Serializable）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

# 保留 XML / JSON 序列化模型字段
-keepclassmembers class com.tickclear.app.domain.** {
    <fields>;
}

# 保留 androidx.window（自适应）
-keep class androidx.window.** { *; }

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*

# 保留 Parcelable / Parcelize
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 不混淆 BuildConfig（调试期密钥注入）
-keep class com.tickclear.app.BuildConfig { *; }
