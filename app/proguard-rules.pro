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

# 通用保留属性（合并声明，避免重复条目）
-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization：本项目不使用 @Serializable 反射序列化，
# 全部走 Json.parseToJsonElement / buildJsonObject 手工构造，
# 故不再整包 keep（该库自带 consumer R8 规则已足够），仅抑制告警。
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# 保留 XML / JSON 序列化模型字段
-keepclassmembers class com.tickclear.app.domain.** {
    <fields>;
}

# 保留 Opus 编解码库（theeasiestway/android-opus-codec，本地 opus.aar，JNI 包 libopus，
# native 方法 + .so 勿被 R8 误删/优化破坏，否则 release 包语音不可用）
-keep class com.theeasiestway.opus.** { *; }
-dontwarn com.theeasiestway.opus.**

# OkHttp（网络层唯一客户端；retrofit 已作为死依赖移除，相应 dontwarn 一并删除）
-dontwarn okhttp3.**

# 保留 Parcelable / Parcelize
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 不混淆 BuildConfig（调试期密钥注入）
-keep class com.tickclear.app.BuildConfig { *; }
