#!/usr/bin/env bash
# 给开发版二进制/应用做稳定签名，避免 macOS 钥匙串每次重新询问。
#
# 前提：本机已导入代码签名证书（默认 "Xiaoman Dev Signing"）与专用钥匙串。
# 可用环境变量覆盖：
#   XIAOMAN_SIGNING_IDENTITY / XIAOMAN_SIGNING_KEYCHAIN / XIAOMAN_SIGNING_PASS_FILE
#
# 用法：
#   desktop/scripts/sign-dev.sh                 # 签 target/debug/xiaoman-desktop
#   desktop/scripts/sign-dev.sh path/to/app.app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KC="${XIAOMAN_SIGNING_KEYCHAIN:-$HOME/Library/Keychains/xiaoman-signing.keychain-db}"
PASS_FILE="${XIAOMAN_SIGNING_PASS_FILE:-$HOME/Library/Keychains/xiaoman-signing.pass}"
IDENTITY="${XIAOMAN_SIGNING_IDENTITY:-Xiaoman Dev Signing}"
TARGET="${1:-$ROOT/src-tauri/target/debug/xiaoman-desktop}"

if [ ! -e "$TARGET" ]; then
  echo "找不到目标：$TARGET（先 cargo build）" >&2
  exit 1
fi

if [ -f "$PASS_FILE" ] && [ -f "$KC" ]; then
  security unlock-keychain -p "$(cat "$PASS_FILE")" "$KC" >/dev/null
  if ! security list-keychains -d user | grep -q "xiaoman-signing"; then
    EXISTING="$(security list-keychains -d user | tr -d '"' | tr '\n' ' ')"
    # shellcheck disable=SC2086
    security list-keychains -s $EXISTING "$KC"
  fi
fi

if [[ "$TARGET" == *.app ]]; then
  codesign --force --sign "$IDENTITY" "$TARGET"
else
  codesign --force --sign "$IDENTITY" --identifier com.xiaoman.desktop "$TARGET"
fi

codesign -dv --verbose=2 "$TARGET" 2>&1 | grep -E 'Identifier|Authority' || true
