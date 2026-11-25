#!/usr/bin/env bash
# Сборка TG-Purge.apk БЕЗ Gradle: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner.
# Причина: Google поставляет build-tools только под x86-64, поэтому это гонится на
# GitHub Actions (ubuntu-latest), а не на телефоне (aarch64/musl).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
echo "SDK=$SDK"
[ -d "$SDK" ] || { echo "нет Android SDK в $SDK"; ls /usr/local/lib/android 2>/dev/null; exit 1; }

BTDIR="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
echo "build-tools: $BTDIR"

PLAT="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)"
AJ="$PLAT/android.jar"
echo "platform: $PLAT"
[ -f "$AJ" ] || { echo "нет android.jar в $PLAT"; exit 1; }

AAPT2="$BTDIR/aapt2"
"$AAPT2" version

rm -rf build gen dexout
mkdir -p build gen dexout

echo "=== 1) aapt2 link (бинарный манифест, res/ нет — UI строится кодом) ==="
"$AAPT2" link \
  -o build/base.apk \
  -I "$AJ" \
  --manifest app/AndroidManifest.xml \
  --java gen \
  --min-sdk-version 24 \
  --target-sdk-version 35 \
  --auto-add-overlay
ls -la build/base.apk

echo "=== 2) javac против android.jar ==="
mkdir -p build/classes
find app/src -name '*.java' > build/sources.txt
find gen -name 'R.java' >> build/sources.txt 2>/dev/null || true
wc -l < build/sources.txt
javac -nowarn -Xlint:-options \
  -d build/classes \
  -classpath "$AJ" \
  --release 11 \
  @build/sources.txt
find build/classes -name '*.class' | sort

echo "=== 3) d8 -> classes.dex ==="
"$BTDIR/d8" --release --lib "$AJ" --output dexout \
  $(find build/classes -name '*.class')
ls -la dexout/classes.dex

echo "=== 4) dex внутрь apk ==="
cp build/base.apk build/unsigned.apk
(cd dexout && zip -q ../build/unsigned.apk classes.dex)
unzip -l build/unsigned.apk

echo "=== 5) zipalign 4 ==="
"$BTDIR/zipalign" -f 4 build/unsigned.apk build/aligned.apk

echo "=== 6) подпись (release-ключ генерится при отсутствии) ==="
if [ ! -f build/purge.ks ]; then
  keytool -genkeypair -v -keystore build/purge.ks -alias purge \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass purge123 -keypass purge123 \
    -dname "CN=TGPurge, OU=local, O=local, L=nowhere, ST=NA, C=RU" 2>&1 | tail -3
fi
"$BTDIR/apksigner" sign \
  --ks build/purge.ks --ks-key-alias purge \
  --ks-pass pass:purge123 --key-pass pass:purge123 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out build/TG-Purge.apk build/aligned.apk

echo "=== 7) verify ==="
"$BTDIR/apksigner" verify --verbose --print-certs build/TG-Purge.apk | head -25

cp build/TG-Purge.apk "$ROOT/TG-Purge.apk"
ls -la "$ROOT/TG-Purge.apk"
sha256sum "$ROOT/TG-Purge.apk"
echo "BUILD_OK"
