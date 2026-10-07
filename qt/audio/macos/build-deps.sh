#!/usr/bin/env bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Builds mpv, lame and all their runtime libraries from source into
# out/audio-deps/<arch>/prefix, with MACOSX_DEPLOYMENT_TARGET=12.0.
#
# Homebrew provides build tools only. Each library writes a stamp file after it
# is installed, and is skipped on the next run if its stamp exists.

# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# meson_build NAME [meson options...] -> configure, build and install $SRC/NAME
meson_build() {
    local name=$1
    shift
    rm -rf "${BUILD:?}/${name:?}"
    meson setup "$BUILD/$name" "$SRC/$name" "${MESON_ARGS[@]}" "$@"
    meson compile -C "$BUILD/$name"
    meson install -C "$BUILD/$name"
}

# cmake_build NAME [cmake options...] -> configure, build and install $SRC/NAME
cmake_build() {
    local name=$1
    shift
    rm -rf "${BUILD:?}/${name:?}"
    cmake -S "$SRC/$name" -B "$BUILD/$name" "${CMAKE_ARGS[@]}" "$@"
    cmake --build "$BUILD/$name" --parallel "$JOBS"
    cmake --install "$BUILD/$name"
}

# autotools_build NAME [configure options...] -> configure (in tree), build, install
autotools_build() {
    local name=$1
    shift
    (
        cd "$SRC/$name"
        ./configure "${AUTOTOOLS_ARGS[@]}" "$@"
        make -j"$JOBS"
        make install
    )
}

# step NAME VERSION FUNCTION -> run FUNCTION unless NAME-VERSION is already built
step() {
    local name=$1 version=$2 fn=$3
    if is_built "$name" "$version"; then
        log "$name $version: already built"
        return
    fi
    log "$name $version: building"
    "$fn"
    mark_built "$name" "$version"
}

# The SDK's zlib has no pkg-config file, but libpng.pc requires one.
write_sdk_zlib_pc() {
    local version
    version="$(sed -n 's/^#define ZLIB_VERSION "\(.*\)"/\1/p' "$SDKROOT/usr/include/zlib.h")"
    [ -n "$version" ] || die "cannot read ZLIB_VERSION from the SDK"
    mkdir -p "$PREFIX/lib/pkgconfig"
    cat >"$PREFIX/lib/pkgconfig/zlib.pc" <<EOF
Name: zlib
Description: zlib from the macOS SDK
Version: $version
Libs: -lz
Cflags:
EOF
}

# Layer 1

build_libpng() {
    fetch_tarball libpng "$LIBPNG_URL" "$LIBPNG_SHA256"
    autotools_build libpng --enable-shared --disable-static --disable-tests --disable-tools
}

build_freetype() {
    fetch_tarball freetype "$FREETYPE_URL" "$FREETYPE_SHA256"
    meson_build freetype \
        -Dharfbuzz=disabled -Dpng=enabled -Dbzip2=enabled -Dzlib=system \
        -Dbrotli=disabled -Dtests=disabled
}

build_fribidi() {
    fetch_tarball fribidi "$FRIBIDI_URL" "$FRIBIDI_SHA256"
    meson_build fribidi -Ddocs=false -Dtests=false -Dbin=false
}

build_libunibreak() {
    fetch_tarball libunibreak "$LIBUNIBREAK_URL" "$LIBUNIBREAK_SHA256"
    autotools_build libunibreak --enable-shared --disable-static
}

build_dav1d() {
    fetch_tarball dav1d "$DAV1D_URL" "$DAV1D_SHA256"
    meson_build dav1d -Denable_tools=false -Denable_tests=false -Denable_examples=false
}

build_lcms2() {
    fetch_tarball lcms2 "$LCMS2_URL" "$LCMS2_SHA256"
    # jpeg/tiff are only used by the lcms2 utilities, which we do not build.
    meson_build lcms2 -Djpeg=disabled -Dtiff=disabled -Dtests=disabled -Dutils=false
}

build_zimg() {
    fetch_tarball zimg "$ZIMG_URL" "$ZIMG_SHA256"
    (cd "$SRC/zimg" && ./autogen.sh)
    autotools_build zimg --enable-shared --disable-static
}

build_vulkan_headers() {
    fetch_tarball vulkan-headers "$VULKAN_HEADERS_URL" "$VULKAN_HEADERS_SHA256"
    cmake_build vulkan-headers -DVULKAN_HEADERS_ENABLE_MODULE=OFF -DVULKAN_HEADERS_ENABLE_TESTS=OFF
}

build_lame() {
    fetch_tarball lame "$LAME_URL" "$LAME_SHA256"
    # Fix undefined symbol error _lame_init_old (same fix as Homebrew).
    # https://sourceforge.net/p/lame/mailman/message/36081038/
    sed -i '' '/^lame_init_old$/d' "$SRC/lame/include/libmp3lame.sym"
    # Static, so the lame binary depends only on system libraries. Note that
    # configure enables nasm if --enable-nasm or --disable-nasm is passed.
    if [ "$ARCH" = x86_64 ]; then
        autotools_build lame --disable-shared --enable-static --disable-debug --enable-nasm
    else
        autotools_build lame --disable-shared --enable-static --disable-debug
    fi
}

# Layer 2

build_harfbuzz() {
    fetch_tarball harfbuzz "$HARFBUZZ_URL" "$HARFBUZZ_SHA256"
    meson_build harfbuzz \
        -Dfreetype=enabled -Dcoretext=enabled \
        -Dglib=disabled -Dgobject=disabled -Dcairo=disabled -Dicu=disabled \
        -Dgraphite2=disabled -Dchafa=disabled -Dpng=disabled -Dzlib=disabled \
        -Draster=disabled -Dvector=disabled -Dgpu=disabled -Dsubset=disabled \
        -Dtests=disabled -Ddocs=disabled -Dintrospection=disabled \
        -Dutilities=disabled -Dbenchmark=disabled
}

build_vulkan_loader() {
    fetch_tarball vulkan-loader "$VULKAN_LOADER_URL" "$VULKAN_LOADER_SHA256"
    # ICD search paths match the Homebrew loader that 0.2.3 shipped, so a user
    # with Homebrew molten-vk gets the same Vulkan output. With an absolute
    # sysconfdir, the loader searches <brew>/etc and /etc; the fallbacks add
    # <brew>/etc/xdg, /etc/xdg, <brew>/share, /usr/local/share and /usr/share.
    # Nothing is installed into sysconfdir.
    cmake_build vulkan-loader \
        -DBUILD_SHARED_LIBS=ON \
        -DBUILD_TESTS=OFF \
        -DVULKAN_HEADERS_INSTALL_DIR="$PREFIX" \
        -DCMAKE_INSTALL_SYSCONFDIR="$BREW_PREFIX_FOR_ARCH/etc" \
        -DFALLBACK_CONFIG_DIRS="$BREW_PREFIX_FOR_ARCH/etc/xdg:/etc/xdg" \
        -DFALLBACK_DATA_DIRS="$BREW_PREFIX_FOR_ARCH/share:/usr/local/share:/usr/share"
}

build_shaderc() {
    fetch_tarball shaderc "$SHADERC_URL" "$SHADERC_SHA256"
    # Fetch only the dependencies needed for libshaderc_shared, at the commits
    # pinned in shaderc's DEPS file. The others are for tests.
    (
        cd "$SRC/shaderc"
        python3 - <<'EOF'
import re
contents = re.sub(r"Var\((.*?)\)", r"vars[\1]", open("DEPS").read())
deps_file = {}
exec(contents, deps_file)
wanted = ("glslang", "spirv-headers", "spirv-tools")
deps = {k: v for k, v in deps_file["deps"].items() if k.split("/")[-1] in wanted}
assert len(deps) == len(wanted), deps
open("DEPS.build", "w").write("deps = %r\n" % deps)
EOF
        GIT_SYNC_DEPS_PATH="$SRC/shaderc/DEPS.build" python3 utils/git-sync-deps
    )
    # shaderc builds its own static deps and links them into
    # libshaderc_shared, so leave BUILD_SHARED_LIBS unset.
    cmake_build shaderc \
        -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON \
        -DSHADERC_SKIP_COPYRIGHT_CHECK=ON -DSPIRV_SKIP_EXECUTABLES=ON \
        -DSPIRV_SKIP_TESTS=ON -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF
}

# Layer 3

build_libass() {
    fetch_tarball libass "$LIBASS_URL" "$LIBASS_SHA256"
    meson_build libass \
        -Dcoretext=enabled -Dfontconfig=disabled -Ddirectwrite=disabled \
        -Drequire-system-font-provider=false -Dlibunibreak=enabled \
        -Dtest=disabled -Dcompare=disabled -Dprofile=disabled -Dfuzz=disabled \
        -Dcheckasm=disabled
}

build_ffmpeg() {
    fetch_tarball ffmpeg "$FFMPEG_URL" "$FFMPEG_SHA256"
    local arch_flag=$ARCH
    [ "$ARCH" = arm64 ] && arch_flag=aarch64
    rm -rf "${BUILD:?}/ffmpeg"
    mkdir -p "$BUILD/ffmpeg"
    (
        cd "$BUILD/ffmpeg"
        # --disable-autodetect keeps anything outside our prefix and the SDK
        # out. The Apple frameworks that 0.2.3's FFmpeg used are enabled
        # explicitly instead. --enable-gpl keeps the GPL-only filters that
        # 0.2.3 had; mpv itself is GPL. With --disable-autodetect, configure
        # only looks for iconv in libc, so -liconv is passed explicitly.
        # The transpose_vt filter needs macOS 13 and is left out at our 12.0
        # target; mpv never inserts it.
        "$SRC/ffmpeg/configure" \
            --prefix="$PREFIX" \
            --arch="$arch_flag" --cc=clang --cxx=clang++ \
            --extra-cflags="$CFLAGS" --extra-ldflags="$LDFLAGS" \
            --extra-libs=-liconv \
            --enable-shared --disable-static \
            --disable-programs --disable-doc --disable-debug \
            --disable-autodetect \
            --enable-gpl \
            --enable-pthreads \
            --enable-videotoolbox --enable-audiotoolbox \
            --enable-avfoundation --enable-appkit --enable-coreimage --enable-metal \
            --enable-zlib --enable-bzlib --enable-iconv \
            --enable-libdav1d
        make -j"$JOBS"
        make install
    )
}

build_libplacebo() {
    fetch_git libplacebo "$LIBPLACEBO_GIT" "$LIBPLACEBO_COMMIT"
    meson_build libplacebo \
        -Dvulkan=enabled -Dshaderc=enabled -Dglslang=disabled \
        -Dlcms=enabled -Dopengl=enabled -Dd3d11=disabled \
        -Ddemos=false -Dtests=false -Dlibdovi=disabled -Dxxhash=disabled \
        -Dunwind=disabled \
        -Dvulkan-registry="$PREFIX/share/vulkan/registry/vk.xml"
}

# Layer 4

build_mpv() {
    fetch_tarball mpv "$MPV_URL" "$MPV_SHA256"
    apply_patches mpv
    # audiounit is mpv's iOS (RemoteIO) output; macOS uses coreaudio.
    # mpv's own shaderc option is Windows-only (d3d11); on macOS shaderc is
    # used through libplacebo. The sdl2-* options are disabled explicitly
    # because meson would otherwise find Homebrew's SDL2 through sdl2-config.
    meson_build mpv \
        -Dbuild-date=false \
        -Dlibmpv=false -Dcplayer=true \
        -Dmanpage-build=disabled -Dhtml-build=disabled -Dpdf-build=disabled \
        -Djavascript=disabled -Dlua=disabled -Dvapoursynth=disabled \
        -Dlibarchive=disabled -Dlibbluray=disabled -Duchardet=disabled \
        -Drubberband=disabled -Djpeg=disabled \
        -Dcdda=disabled -Ddvdnav=disabled -Dsdl2-audio=disabled \
        -Dsdl2-video=disabled -Dsdl2-gamepad=disabled -Dopenal=disabled \
        -Djack=disabled -Dsixel=disabled -Dcaca=disabled -Dspirv-cross=disabled \
        -Dlcms2=enabled -Dzimg=enabled -Dlibavdevice=enabled \
        -Dvulkan=enabled -Dshaderc=disabled \
        -Dcocoa=enabled -Dcoreaudio=enabled -Davfoundation=enabled \
        -Daudiounit=disabled \
        -Dgl-cocoa=enabled -Dmacos-cocoa-cb=enabled -Dswift-build=enabled \
        -Dvideotoolbox-gl=enabled -Dvideotoolbox-pl=enabled \
        -Dmacos-media-player=enabled -Dmacos-touchbar=enabled \
        -Dswift-flags="-target $ARCH-apple-macos$DEPLOYMENT_TARGET"
    local swift_minos
    swift_minos="$(otool -l "$BUILD/mpv/osdep/mac/swift.o" | awk '$1 == "minos" { print $2 }' | sort -u | tr '\n' ' ' | sed 's/ $//')"
    [ "$swift_minos" = "$DEPLOYMENT_TARGET" ] ||
        die "mpv's Swift code has minos $swift_minos, expected $DEPLOYMENT_TARGET"
}

write_sdk_zlib_pc

step libpng "$LIBPNG_VERSION" build_libpng
step freetype "$FREETYPE_VERSION" build_freetype
step fribidi "$FRIBIDI_VERSION" build_fribidi
step libunibreak "$LIBUNIBREAK_VERSION" build_libunibreak
step dav1d "$DAV1D_VERSION" build_dav1d
step lcms2 "$LCMS2_VERSION" build_lcms2
step zimg "$ZIMG_VERSION" build_zimg
step vulkan-headers "$VULKAN_VERSION" build_vulkan_headers
step lame "$LAME_VERSION" build_lame

step harfbuzz "$HARFBUZZ_VERSION" build_harfbuzz
step vulkan-loader "$VULKAN_VERSION" build_vulkan_loader
step shaderc "$SHADERC_VERSION" build_shaderc

step libass "$LIBASS_VERSION" build_libass
step ffmpeg "$FFMPEG_VERSION" build_ffmpeg
step libplacebo "$LIBPLACEBO_VERSION" build_libplacebo

step mpv "$MPV_VERSION" build_mpv

log "All dependencies built in $PREFIX"
