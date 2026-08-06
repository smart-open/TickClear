# TickClear ProGuard 规则
# 保留 SQLCipher（加密数据库核心；实际 artifact 为 net.zetetic，net.sqlcipher 为旧包名死规则已删）
-keep class net.zetetic.** { *; }

# 保留 AndroidX Security（Tink 支撑 EncryptedSharedPreferences / VaultStore）：
# Tink 经 protobuf 反射 + Class.forName 注册 KeyManager，R8 full-mode 下易被裁导致
# GeneralSecurityException: No key manager found → 加密库打不开、用户数据「消失」。防御性保留。
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# 保留 Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# 保留 Hilt / Dagger 生成类（Hilt/Dagger 自带 consumer R8 规则已覆盖，下面仅兜底显式类）
-keep class **_HiltModules.* { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.** { *; }

# 通用保留属性（合并声明，避免重复条目）；保留 SourceFile/LineNumberTable 使 release 崩溃栈可读
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

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

# 不混淆 BuildConfig（调试期配置注入）
-keep class com.tickclear.app.BuildConfig { *; }
