#!/usr/bin/env bash
# 本地打包桌面端：构建 Web + Tauri .app，并用本机稳定证书签名。
#
# 证书/钥匙串默认与 sign-dev.sh 相同，可用 XIAOMAN_SIGNING_* 覆盖。
# 若存在 ~/.tauri/xiaoman-updater.key，会自动用作 TAURI_SIGNING_PRIVATE_KEY。
#
# 用法：
#   desktop/scripts/build-signed.sh [tauri build 的额外参数]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KC="${XIAOMAN_SIGNING_KEYCHAIN:-$HOME/Library/Keychains/xiaoman-signing.keychain-db}"
PASS_FILE="${XIAOMAN_SIGNING_PASS_FILE:-$HOME/Library/Keychains/xiaoman-signing.pass}"
IDENTITY="${XIAOMAN_SIGNING_IDENTITY:-Xiaoman Dev Signing}"

if [ -f "$PASS_FILE" ] && [ -f "$KC" ]; then
  security unlock-keychain -p "$(cat "$PASS_FILE")" "$KC" >/dev/null
  if ! security list-keychains -d user | grep -q "xiaoman-signing"; then
    EXISTING="$(security list-keychains -d user | tr -d '"' | tr '\n' ' ')"
    # shellcheck disable=SC2086
    security list-keychains -s $EXISTING "$KC"
  fi
fi

if [ -z "${TAURI_SIGNING_PRIVATE_KEY:-}" ] && [ -f "$HOME/.tauri/xiaoman-updater.key" ]; then
  export TAURI_SIGNING_PRIVATE_KEY
  TAURI_SIGNING_PRIVATE_KEY="$(cat "$HOME/.tauri/xiaoman-updater.key")"
fi

cd "$ROOT/web"
npm run build

cd "$ROOT/desktop"
CONFIG="$(printf '{"bundle":{"macOS":{"signingIdentity":"%s"}}}' "$IDENTITY")"
npx @tauri-apps/cli@2.12.6 build --config "$CONFIG" "$@"
