#!/usr/bin/env bash
set -euo pipefail
ROOT=/scratch/work/ug
SDK=/scratch/work/tc/android-sdk
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-35/android.jar
KOTLINC=/scratch/work/tc/kotlinc/bin/kotlinc
export JAVA_HOME=/scratch/work/tc/jdk
export PATH=$JAVA_HOME/bin:$PATH
OUT=$ROOT/build
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== resources"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/res.zip"
# aapt2 needs the package attribute; AGP 8 forbids it in source, so inject it into a build copy.
sed 's|<manifest |<manifest package="com.ultimateguard" |' \
  "$ROOT/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" \
  --manifest "$OUT/AndroidManifest.xml" \
  -R "$OUT/res.zip" -A "$ROOT/app/src/main/assets" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 35 \
  --version-code 1 --version-name 1.0.0 --auto-add-overlay

echo "== R.java"
javac -nowarn -source 17 -target 17 -cp "$PLATFORM" -d "$OUT/classes" "$OUT/gen/com/ultimateguard/R.java"
jar cf "$OUT/r.jar" -C "$OUT/classes" .

echo "== kotlin"
"$KOTLINC" -nowarn -jvm-target 17 -classpath "$PLATFORM:$OUT/r.jar" -d "$OUT/app.jar" \
  $(find "$ROOT/app/src/main/java" -name '*.kt')

echo "== dex"
java -Xmx700m -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" \
  --output "$OUT/dex" "$OUT/app.jar" "$OUT/r.jar" /scratch/work/tc/kotlinc/lib/kotlin-stdlib.jar

echo "== package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
if [ ! -f "$OUT/ug.keystore" ]; then
  keytool -J-Xmx256m -genkeypair -keystore "$OUT/ug.keystore" -alias ug -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass ultimateguard -keypass ultimateguard \
    -dname "CN=Ultimate Guard, O=Ultimate Guard, C=IN" >/dev/null 2>&1
fi
java -Xmx500m -jar "$BT/lib/apksigner.jar" sign --ks "$OUT/ug.keystore" --ks-pass pass:ultimateguard \
  --key-pass pass:ultimateguard --out "$OUT/UltimateGuard.apk" "$OUT/aligned.apk"
java -Xmx500m -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT/UltimateGuard.apk" | head -3
ls -lh "$OUT/UltimateGuard.apk"
