#!/usr/bin/env bash
# ============================================================================
# build_release.sh — TickClear 发布打包脚本（一键生成签名密钥 + 构建 Release APK）
#
# 功能：
#   1. keystore 不存在时，用 keytool 生成 RSA 2048 公私钥证书（有效期 10000 天，
#      随机 20 位字母数字强密码，写入 keystore/keystore.properties，仅本机保留）；
#   2. 把签名配置写入 local.properties（app/build.gradle.kts 的 release signingConfig
#      读取 release.* 键，有密钥即用正式签名）；
#   3. 执行 assembleRelease，产出正式签名 APK 并校验签名证书。
#
# 用法：
#   bash scripts/build_release.sh               # 复用已有 keystore / 首次自动生成
#   bash scripts/build_release.sh --force-keystore  # 强制重新生成 keystore（旧文件先备份为 .old.<时间戳>）
#
# 安全红线：
#   - keystore/ 目录已被 .gitignore 排除，严禁提交（丢失即失去该身份发布权）；
#   - keystore 与 keystore.properties 请立即离线备份（U 盘 / 密码管理器），切勿仅存本机。
# ============================================================================
set -euo pipefail

# 统一先进入工程根目录：keytool / gradle 均以相对路径工作，
# 避免 msys 把 /d/... 绝对路径参数原样传给 Windows 程序（Java 会解析成 \d\... 而失败）。
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
# msys → Windows 盘符路径（本机无 cygpath）：/d/ai_work/... → D:/ai_work/...（写 local.properties 用）
ROOT_WIN="$(echo "$ROOT" | sed 's|^/\([a-zA-Z]\)/|\1:/|')"
KEYSTORE_DIR="$ROOT/keystore"
KEYSTORE="keystore/release.jks"                # 相对路径（cwd 已切到 ROOT），keytool 可直接写
KEYSTORE_ABS="$KEYSTORE_DIR/release.jks"
KEYSTORE_PROPS="$KEYSTORE_DIR/keystore.properties"
LOCAL_PROPS="$ROOT/local.properties"
ALIAS="tickclear"
VALIDITY_DAYS=10000

# 本机构建环境（沙箱 gradlew 不可用，走 wrapper jar + 指定 JDK）
# 注意：环境 JAVA_HOME 可能是反斜杠形式（D:\...），Git Bash exec 会失败，统一归一为正斜杠。
JAVA_HOME="${JAVA_HOME:-D:/software/jvms_v2.1.6/store/jdk-21.0.6}"
JAVA_HOME="${JAVA_HOME//\\//}"
ANDROID_HOME="${ANDROID_HOME:-C:/Android}"
ANDROID_HOME="${ANDROID_HOME//\\//}"
KEYTOOL="$JAVA_HOME/bin/keytool"
JAVA="$JAVA_HOME/bin/java"

# 20 位随机字母数字密码（/dev/urandom，Git Bash 可用）
rand_pass() { head -c 48 /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 20; }

echo "==> [1/3] 检查 / 生成发布密钥 (keystore)"
if [ ! -f "$KEYSTORE" ] || [ "${1:-}" = "--force-keystore" ]; then
    [ -d "$KEYSTORE_DIR" ] || mkdir -p "$KEYSTORE_DIR"
    # keytool -genkeypair 对已存在文件会先尝试加载（旧密码 → 失败），不会覆盖；
    # 因此 --force 时把旧文件 mv 走当备份（mv 非删除，不触发安全删除拦截），再生成全新密钥。
    if [ -f "$KEYSTORE" ]; then
        mv "$KEYSTORE" "$KEYSTORE.old.$(date +%Y%m%d%H%M%S)"
        echo "    旧 keystore 已备份（mv 移动）"
    fi
    # PKCS12（JDK9+ 默认密钥库格式）不支持 storepass ≠ keypass，keytool 会忽略 -keypass，
    # 因此 store/key 必须用同一密码，否则 Gradle 解密 key 时 "Given final block not properly padded"。
    STORE_PASS="$(rand_pass)"
    KEY_PASS="$STORE_PASS"
    "$KEYTOOL" -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity "$VALIDITY_DAYS" \
        -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
        -dname "CN=TickClear, OU=Mobile, O=TickClear, L=Shenzhen, ST=Guangdong, C=CN" || {
        echo "FAILED: keytool 生成失败（检查 JAVA_HOME=$JAVA_HOME）"
        exit 1
    }
    printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=%s\n' "$STORE_PASS" "$KEY_PASS" "$ALIAS" > "$KEYSTORE_PROPS"
    chmod 600 "$KEYSTORE_PROPS" 2>/dev/null || true
    echo "    已生成 $KEYSTORE（RSA 2048 / ${VALIDITY_DAYS} 天）"
    echo "    密码已存 keystore/keystore.properties —— 请立即离线备份！"
else
    echo "    复用现有 keystore: $KEYSTORE"
fi

# 读取密钥参数（生成场景刚写入；复用场景从 properties 恢复）
if [ -f "$KEYSTORE_PROPS" ]; then
    STORE_PASS="$(grep '^storePassword=' "$KEYSTORE_PROPS" | head -1 | cut -d= -f2)"
    KEY_PASS="$(grep '^keyPassword=' "$KEYSTORE_PROPS" | head -1 | cut -d= -f2)"
    ALIAS="$(grep '^keyAlias=' "$KEYSTORE_PROPS" | head -1 | cut -d= -f2)"
else
    echo "FAILED: 缺少 $KEYSTORE_PROPS（keystore 存在但密码文件丢失，无法签名）"
    exit 1
fi

echo "==> [2/3] 写入 local.properties 签名配置（保留原有非 release.* 键）"
if [ -f "$LOCAL_PROPS" ]; then
    grep -v '^release\.' "$LOCAL_PROPS" > "$LOCAL_PROPS.tmp" || true
else
    : > "$LOCAL_PROPS.tmp"
fi
cat >> "$LOCAL_PROPS.tmp" <<EOF
release.storeFile=$ROOT_WIN/keystore/release.jks
release.storePassword=$STORE_PASS
release.keyAlias=$ALIAS
release.keyPassword=$KEY_PASS
EOF
mv "$LOCAL_PROPS.tmp" "$LOCAL_PROPS"
echo "    local.properties 已更新"

echo "==> [3/3] 构建 Release APK"
export JAVA_HOME ANDROID_HOME
cd "$ROOT"
"$JAVA" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon assembleRelease

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
APK_REL="app/build/outputs/apk/release/app-release.apk"   # 相对路径（cwd=ROOT），避免 msys 转换坑
echo ""
echo "=========================================="
echo " 构建完成:"
echo "   $APK"
ls -la "$APK_REL"
echo ""
echo "--- 签名校验 (apksigner, v2/v3) ---"
# minSdk 26 → AGP 默认禁用 v1(JAR) 签名，keytool -printcert 读不到，必须用 apksigner 验 v2/v3
APKSIGNER_JAR="$(ls "$ANDROID_HOME"/build-tools/*/lib/apksigner.jar 2>/dev/null | sort | tail -1)"
if [ -n "$APKSIGNER_JAR" ]; then
    "$JAVA" -jar "$APKSIGNER_JAR" verify --print-certs "$APK_REL"
else
    echo "(未找到 apksigner.jar，APK 已生成，请手动校验)"
fi
echo "=========================================="
