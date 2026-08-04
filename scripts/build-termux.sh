#!/bin/bash
# Builds the debug APK on a Termux/proot-distro aarch64 device with no root,
# no system JDK, and only a partial Android SDK. Adapted from
# balloon-pop-game/scripts/build-termux.sh for this repo's single-module
# (":app") layout — no "android:" task prefix, build.gradle lives at
# app/build.gradle, and the APK lands under app/build/outputs/apk/debug.
#
# Idempotent: safe to re-run in a fresh session. Each step checks what's
# already in place (in stable, non-scratchpad paths) before redoing it.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="$HOME/tools"
SDK_DIR="${ANDROID_SDK_ROOT:-/root/coding/android-sdk}"
SYSROOT_DIR="$TOOLS_DIR/amd64root/sysroot"
QEMU="$(command -v qemu-x86_64 || echo /data/data/com.termux/files/usr/bin/qemu-x86_64)"

log() { echo "==> $*"; }

# --- 1. JDK 21 -------------------------------------------------------------
JDK_DIR="$(find "$TOOLS_DIR" -maxdepth 1 -iname 'jdk-21*' 2>/dev/null | head -1 || true)"
if [ -z "$JDK_DIR" ]; then
  log "No JDK found under $TOOLS_DIR — downloading Temurin 21 (linux/aarch64)"
  mkdir -p "$TOOLS_DIR"
  arch="$(uname -m)"
  case "$arch" in
    aarch64) jdk_arch=aarch64 ;;
    x86_64) jdk_arch=x64 ;;
    *) echo "Unsupported arch: $arch" >&2; exit 1 ;;
  esac
  curl -sL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${jdk_arch}/jdk/hotspot/normal/eclipse" \
    -o "$TOOLS_DIR/jdk21.tar.gz"
  tar xzf "$TOOLS_DIR/jdk21.tar.gz" -C "$TOOLS_DIR"
  rm "$TOOLS_DIR/jdk21.tar.gz"
  JDK_DIR="$(find "$TOOLS_DIR" -maxdepth 1 -iname 'jdk-21*' | head -1)"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
log "JAVA_HOME=$JAVA_HOME"
java -version

# --- 2. Android SDK packages ------------------------------------------------
export ANDROID_HOME="$SDK_DIR"
if [ ! -d "$SDK_DIR/cmdline-tools/latest" ]; then
  echo "No Android cmdline-tools at $SDK_DIR/cmdline-tools/latest — bootstrap the SDK first (see CLAUDE.md)." >&2
  exit 1
fi

compile_sdk="$(grep -oE 'compileSdk\s*=\s*[0-9]+' "$REPO_DIR/app/build.gradle" | grep -oE '[0-9]+')"
build_tools="${compile_sdk}.0.0"

# Android's platform package naming changed at API 37: older levels are
# "android-<N>" but 37+ are "android-<N>.0". Prefer whichever is already
# installed; if neither is, try the plain name first and fall back to the
# dotted variant below (we can't know in advance which scheme a future SDK
# level will use).
platform_dir="$SDK_DIR/platforms/android-${compile_sdk}"
platform_pkg="platforms;android-${compile_sdk}"
if [ -d "${platform_dir}.0" ]; then
  platform_dir="${platform_dir}.0"
  platform_pkg="${platform_pkg}.0"
fi

if [ ! -d "$platform_dir" ] || \
   [ ! -d "$SDK_DIR/build-tools/${build_tools}" ] || \
   [ ! -d "$SDK_DIR/platform-tools" ]; then
  log "Installing missing SDK packages (platform ${platform_pkg}, build-tools ${build_tools})"
  yes | sh "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
  if ! sh "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
      "platform-tools" "${platform_pkg}" "build-tools;${build_tools}"; then
    platform_pkg="platforms;android-${compile_sdk}.0"
    sh "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
      "platform-tools" "${platform_pkg}" "build-tools;${build_tools}"
  fi
fi

if [ ! -f "$REPO_DIR/local.properties" ] || ! grep -q "^sdk.dir=$SDK_DIR$" "$REPO_DIR/local.properties"; then
  log "Writing local.properties -> sdk.dir=$SDK_DIR"
  echo "sdk.dir=$SDK_DIR" > "$REPO_DIR/local.properties"
fi

# --- 3. x86_64 sysroot for qemu-user aapt2 ---------------------------------
if [ ! -x "$SYSROOT_DIR/lib/x86_64-linux-gnu/libc.so.6" ] && \
   [ ! -x "$SYSROOT_DIR/usr/lib/x86_64-linux-gnu/libc.so.6" ]; then
  log "Building x86_64 glibc sysroot at $SYSROOT_DIR"
  mkdir -p "$TOOLS_DIR/amd64root"
  cd "$TOOLS_DIR/amd64root"
  libc6_ver="$(dpkg-query -W -f='${Version}' libc6)"
  libgcc_ver="$(dpkg-query -W -f='${Version}' libgcc-s1)"
  [ -f "libc6_${libc6_ver}_amd64.deb" ] || \
    curl -sL "http://deb.debian.org/debian/pool/main/g/glibc/libc6_${libc6_ver}_amd64.deb" -o "libc6_${libc6_ver}_amd64.deb"
  [ -f "libgcc-s1_${libgcc_ver}_amd64.deb" ] || \
    curl -sL "http://deb.debian.org/debian/pool/main/g/gcc-14/libgcc-s1_${libgcc_ver}_amd64.deb" -o "libgcc-s1_${libgcc_ver}_amd64.deb"
  mkdir -p sysroot
  dpkg-deb -x "libc6_${libc6_ver}_amd64.deb" sysroot
  dpkg-deb -x "libgcc-s1_${libgcc_ver}_amd64.deb" sysroot
  ln -sfn usr/lib64 sysroot/lib64
  ln -sfn usr/lib sysroot/lib
  cd "$REPO_DIR"
fi

# --- 4. Wrap any cached aapt2 with qemu-x86_64 ------------------------------
wrap_aapt2() {
  local real="$1"
  local dir
  dir="$(dirname "$real")"
  if [ ! -f "$dir/aapt2.real" ]; then
    mv "$real" "$dir/aapt2.real"
  fi
  cat > "$dir/aapt2" <<EOF
#!/bin/sh
exec "$QEMU" -L "$SYSROOT_DIR" "$dir/aapt2.real" "\$@"
EOF
  chmod +x "$dir/aapt2"
}

while IFS= read -r aapt2_bin; do
  if file "$aapt2_bin" | grep -q "shell script"; then
    log "aapt2 already wrapped: $aapt2_bin (re-pointing at stable sysroot)"
  else
    log "Wrapping aapt2: $aapt2_bin"
  fi
  wrap_aapt2 "$aapt2_bin"
done < <(find "$HOME/.gradle/caches" -path '*/transformed/aapt2-*-linux/aapt2' 2>/dev/null)

# --- 5. Build ---------------------------------------------------------------
cd "$REPO_DIR"
log "Running gradlew assembleDebug"
if ! sh ./gradlew assembleDebug --console=plain; then
  log "First attempt failed — checking for a newly-populated aapt2 cache entry to wrap and retrying once"
  found_new=false
  while IFS= read -r aapt2_bin; do
    if ! file "$aapt2_bin" | grep -q "shell script"; then
      wrap_aapt2 "$aapt2_bin"
      found_new=true
    fi
  done < <(find "$HOME/.gradle/caches" -path '*/transformed/aapt2-*-linux/aapt2' 2>/dev/null)
  if [ "$found_new" = true ]; then
    sh ./gradlew assembleDebug --console=plain
  else
    exit 1
  fi
fi

apk="$(find "$REPO_DIR/app/build/outputs/apk/debug" -name '*.apk' | head -1)"
log "BUILD SUCCESSFUL: $apk"
