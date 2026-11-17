#!/usr/bin/env bash
# Linux 版 native_ripgrep 构建脚本（上游只提供 PowerShell 版）
#
# 与 build_native_ripgrep.ps1 等价：用 NDK 工具链交叉编译 Rust crate，
# 产物落到 app/src/main/jniLibs/<abi>/liboperit_ripgrep.so
#
# 用法: bash tools/native_ripgrep/build_native_ripgrep.sh [target] [api]
#   默认 target=aarch64-linux-android api=23
set -eu

TARGET="${1:-aarch64-linux-android}"
API="${2:-23}"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CRATE_DIR="$REPO_ROOT/tools/native_ripgrep"

# --- SDK / NDK ---
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f "$REPO_ROOT/local.properties" ]; then
  SDK_DIR=$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" | head -1)
fi
[ -n "$SDK_DIR" ] || { echo "Android SDK 路径未知（设 ANDROID_HOME 或写 local.properties）"; exit 1; }

# NDK 从 SDK 目录里挑最高版本：在 aarch64 主机上 uname 得到的是 linux-aarch64，
# 但 NDK 只带 linux-x86_64 预编译，所以要显式找 toolchain 目录，不能拼 uname。
if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
  NDK_ROOT="$ANDROID_NDK_HOME"
elif [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
  NDK_ROOT="$ANDROID_NDK_ROOT"
elif [ -d "$SDK_DIR/ndk" ]; then
  NDK_ROOT=$(ls -d "$SDK_DIR"/ndk/* 2>/dev/null | sort -V | tail -1 || true)
fi
[ -n "${NDK_ROOT:-}" ] && [ -d "$NDK_ROOT" ] || { echo "NDK 未找到"; exit 1; }

PREBUILT="$NDK_ROOT/toolchains/llvm/prebuilt"
LINKER_NAME="aarch64-linux-android$API-clang"

case "$TARGET" in
  aarch64-linux-android)      LINKER_NAME="aarch64-linux-android$API-clang";   ABI=arm64-v8a ;;
  armv7-linux-androideabi)    LINKER_NAME="armv7a-linux-androideabi$API-clang"; ABI=armeabi-v7a ;;
  x86_64-linux-android)      LINKER_NAME="x86_64-linux-android$API-clang";     ABI=x86_64 ;;
  i686-linux-android)         LINKER_NAME="i686-linux-android$API-clang";       ABI=x86 ;;
  *) echo "不支持的 target: $TARGET"; exit 1 ;;
esac

# NDK 的 llvm/prebuilt 下是主机架构目录，直接在里面找；找不到就全盘找一次
CANDIDATE=""
for d in "$PREBUILT"/*/bin; do
  if [ -f "$d/$LINKER_NAME" ]; then CANDIDATE="$d/$LINKER_NAME"; break; fi
done
[ -n "$CANDIDATE" ] || CANDIDATE=$(find "$PREBUILT" -name "$LINKER_NAME" 2>/dev/null | head -1 || true)
[ -n "$CANDIDATE" ] || { echo "找不到链接器: $LINKER_NAME (under $PREBUILT)"; exit 1; }
chmod +x "$CANDIDATE" 2>/dev/null || true

# NDK 的预编译二进制全是 x86_64，在 arm64 主机上执行会报
# "cannot execute: required file not found"。检测到这种情况就退回到
# 主机原生 clang + --target + NDK sysroot 的等价包装脚本。
LINKER_CMD="$CANDIDATE"
if ! "$CANDIDATE" --version >/dev/null 2>&1; then
  echo "NDK 链接器不可执行（主机架构不匹配），改用主机原生 clang 包装"
  HOST_CLANG=$(command -v clang || true)
  [ -n "$HOST_CLANG" ] || { echo "主机也没有 clang，先装 clang lld"; exit 1; }
  NDK_PREBUILT="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
  CLANG_RES_DIR=$(ls -d "$NDK_PREBUILT"/lib/clang/* 2>/dev/null | head -1)
  WRAPPER="/tmp/operit-android-linker-$TARGET.sh"
  cat > "$WRAPPER" <<WRAP
#!/bin/bash
exec "$HOST_CLANG" --target=$TARGET$API --sysroot="$NDK_PREBUILT/sysroot" \\
  -resource-dir "$CLANG_RES_DIR" \\
  -L"$CLANG_RES_DIR/lib/linux/aarch64" \\
  -fuse-ld=lld "\$@"
WRAP
  chmod +x "$WRAPPER"
  LINKER_CMD="$WRAPPER"
  CANDIDATE="$WRAPPER"
fi

echo "SDK      = $SDK_DIR"
echo "NDK      = $NDK_ROOT"
echo "prebuilt = $PREBUILT"
echo "target   = $TARGET"
echo "abi      = $ABI"
echo "linker   = $LINKER_CMD"

command -v cargo >/dev/null || { echo "cargo 未安装"; exit 1; }

ENV_NAME="CARGO_TARGET_$(echo "$TARGET" | tr '[:lower:]' '[:upper:]' | tr '-' '_')_LINKER"
export "$ENV_NAME=$CANDIDATE"

cargo build --manifest-path "$CRATE_DIR/Cargo.toml" --release --target "$TARGET"

SRC="$CRATE_DIR/target/$TARGET/release/liboperit_ripgrep.so"
[ -f "$SRC" ] || { echo "产物缺失: $SRC"; exit 1; }

DST_DIR="$REPO_ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$DST_DIR"
cp -f "$SRC" "$DST_DIR/liboperit_ripgrep.so"
echo "已生成 $DST_DIR/liboperit_ripgrep.so"