# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# Shared environment and helpers for the macOS anki-audio build.
# Source this file; do not run it.

set -euo pipefail

MACOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$MACOS_DIR/../../.." && pwd)"

# shellcheck source=versions.env
source "$MACOS_DIR/versions.env"

ARCH="$(uname -m)" # arm64 or x86_64
DEPLOYMENT_TARGET=12.0
export MACOSX_DEPLOYMENT_TARGET=$DEPLOYMENT_TARGET
SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
export SDKROOT

DEPS_ROOT="$REPO/out/audio-deps"
DOWNLOADS="$DEPS_ROOT/downloads"
PREFIX="$DEPS_ROOT/$ARCH/prefix"
SRC="$DEPS_ROOT/$ARCH/src"
BUILD="$DEPS_ROOT/$ARCH/build"
STAMPS="$PREFIX/.stamps"
JOBS="$(sysctl -n hw.ncpu)"

# Homebrew prefix on this arch. Only used for the Vulkan loader's ICD search
# paths, to match the Homebrew loader that 0.2.3 shipped. Never used to find
# headers or libraries.
if [ "$ARCH" = arm64 ]; then
    BREW_PREFIX_FOR_ARCH=/opt/homebrew
else
    BREW_PREFIX_FOR_ARCH=/usr/local
fi

mkdir -p "$DOWNLOADS" "$PREFIX/lib" "$PREFIX/include" "$SRC" "$BUILD" "$STAMPS"

export CFLAGS="-arch $ARCH -mmacosx-version-min=$DEPLOYMENT_TARGET -isysroot $SDKROOT -O2 -I$PREFIX/include"
export CXXFLAGS="$CFLAGS"
export OBJCFLAGS="$CFLAGS"
export OBJCXXFLAGS="$CFLAGS"
export LDFLAGS="-arch $ARCH -mmacosx-version-min=$DEPLOYMENT_TARGET -isysroot $SDKROOT -L$PREFIX/lib -Wl,-headerpad_max_install_names"
export CC=clang
export CXX=clang++

# Find only our own libraries. PKG_CONFIG_LIBDIR replaces the default search path.
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig"
unset PKG_CONFIG_PATH
export PATH="$PREFIX/bin:$PATH"

CMAKE_ARGS=(
    -G Ninja
    -DCMAKE_BUILD_TYPE=Release
    -DCMAKE_INSTALL_PREFIX="$PREFIX"
    -DCMAKE_INSTALL_LIBDIR=lib
    -DCMAKE_PREFIX_PATH="$PREFIX"
    -DCMAKE_OSX_DEPLOYMENT_TARGET=$DEPLOYMENT_TARGET
    -DCMAKE_OSX_ARCHITECTURES="$ARCH"
    -DCMAKE_OSX_SYSROOT="$SDKROOT"
    "-DCMAKE_IGNORE_PREFIX_PATH=/opt/homebrew;/usr/local"
    -DCMAKE_FIND_FRAMEWORK=LAST
    -DCMAKE_INSTALL_NAME_DIR="$PREFIX/lib"
)

MESON_ARGS=(
    --prefix="$PREFIX"
    --libdir=lib
    --buildtype=release
    -Ddefault_library=shared
    # Never fall back to a bundled subproject in place of our own library.
    --wrap-mode=nofallback
)

AUTOTOOLS_ARGS=(
    --prefix="$PREFIX"
    --disable-dependency-tracking
)

log() {
    echo "==> $*" >&2
}

die() {
    echo "error: $*" >&2
    exit 1
}

# download NAME URL SHA256 -> prints path of the verified file
download() {
    local name=$1 url=$2 sha=$3
    local file
    file="$DOWNLOADS/$name-$(basename "$url")"
    if [ -f "$file" ] && [ "$(shasum -a 256 "$file" | cut -d' ' -f1)" = "$sha" ]; then
        echo "$file"
        return
    fi
    log "Downloading $url"
    curl -fsSL --retry 5 --retry-all-errors --retry-delay 5 --connect-timeout 30 \
        -o "$file.part" "$url" || die "download failed: $url"
    local actual
    actual="$(shasum -a 256 "$file.part" | cut -d' ' -f1)"
    if [ "$actual" != "$sha" ]; then
        # Show what was served instead (for example an HTML error page).
        echo "downloaded $(wc -c <"$file.part" | tr -d ' ') bytes: $(file -b "$file.part")" >&2
        rm -f "$file.part"
        die "sha256 mismatch for $url: expected $sha, got $actual"
    fi
    mv "$file.part" "$file"
    echo "$file"
}

# fetch_tarball NAME URL SHA256 -> fresh source tree at $SRC/NAME
fetch_tarball() {
    local name=$1 url=$2 sha=$3
    local file
    file="$(download "$name" "$url" "$sha")" || exit 1
    rm -rf "${SRC:?}/${name:?}"
    mkdir -p "$SRC/$name"
    tar -xf "$file" -C "$SRC/$name" --strip-components=1
}

# fetch_git NAME URL COMMIT -> fresh checkout (with submodules) at $SRC/NAME
fetch_git() {
    local name=$1 url=$2 commit=$3
    local dir="$SRC/$name"
    rm -rf "${SRC:?}/${name:?}"
    log "Cloning $url at $commit"
    git init -q "$dir"
    git -C "$dir" remote add origin "$url"
    git -C "$dir" fetch -q --depth 1 origin "$commit"
    git -C "$dir" -c advice.detachedHead=false checkout -q FETCH_HEAD
    [ "$(git -C "$dir" rev-parse HEAD)" = "$commit" ] || die "$name: checkout is not at $commit"
    git -C "$dir" submodule update -q --init --recursive --depth 1
}

# apply_patches NAME -> apply patches/NAME/*.patch to $SRC/NAME
apply_patches() {
    local name=$1 patch
    for patch in "$MACOS_DIR/patches/$name"/*.patch; do
        [ -e "$patch" ] || continue
        log "Applying $(basename "$patch")"
        patch -d "$SRC/$name" -p1 --forward --batch <"$patch"
    done
}

is_built() {
    [ -f "$STAMPS/$1-$2" ]
}

mark_built() {
    touch "$STAMPS/$1-$2"
}
