#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/android/build"

[ -f "$PLATFORM" ] || { echo "missing $PLATFORM (sdkmanager platforms;android-34)"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"

[ -f "$ROOT/data/brands.json" ] && cp "$ROOT/data/brands.json" "$ROOT/android/assets/brands/brands.json"

echo "== aapt2 compile/link"
"$BT/aapt2" compile --dir "$ROOT/android/res" -o "$OUT/res.zip"
"$BT/aapt2" link \
  -o "$OUT/apk/unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$ROOT/android/AndroidManifest.xml" \
  -A "$ROOT/android/assets" \
  --java "$OUT/gen" \
  "$OUT/res.zip"

echo "== javac"
javac --release 11 -encoding UTF-8 -classpath "$PLATFORM" -d "$OUT/classes" \
  $(find "$ROOT/android/src" "$OUT/gen" -name '*.java')

echo "== d8"
"$BT/d8" --lib "$PLATFORM" --min-api 24 --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

(cd "$OUT/dex" && zip -q -u "$OUT/apk/unsigned.apk" classes.dex)

echo "== zipalign"
"$BT/zipalign" -f 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

echo "== sign"
KS="$ROOT/android/replica.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass xiaoman123 -keypass xiaoman123 \
    -alias replica -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Xiaoman Replica, OU=Local, O=Local, L=Local, ST=Local, C=CN"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:xiaoman123 --key-pass pass:xiaoman123 \
  --out "$ROOT/android/xiaoman.apk" "$OUT/apk/aligned.apk"
"$BT/apksigner" verify --print-certs "$ROOT/android/xiaoman.apk" | head -4

echo "APK: $ROOT/android/xiaoman.apk"
