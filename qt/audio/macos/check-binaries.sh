#!/usr/bin/env bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Usage: check-binaries.sh DIR...
#
# Checks every Mach-O file under each DIR (out/extracted/mpv and
# out/extracted/lame, or anki_audio/ from an unpacked wheel). A DIR is one
# bundle: @executable_path/libs/ and @rpath/ must resolve inside it.
#
# 1. minos (LC_BUILD_VERSION or LC_VERSION_MIN_MACOSX) <= 12.0
# 2. the only architecture is $EXPECTED_ARCH (default: uname -m)
# 3. every linked library is in the bundle (@executable_path/libs/,
#    @loader_path/, or @rpath/ resolving inside DIR) or in /usr/lib/ or
#    /System/Library/; LC_RPATHs are unique and do not point outside the
#    bundle
# 4. no libraries that should never be bundled (Homebrew leaks)
#
# Prints a line per file and every problem found, then exits non-zero if
# there were any problems.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 DIR..." >&2
    exit 2
fi

MAX_MINOS=12.0
EXPECTED_ARCH="${EXPECTED_ARCH:-$(uname -m)}"
FORBIDDEN_LIBS="libMoltenVK libssl libcrypto libarchive libbluray libfontconfig libglib"

problems=0

problem() {
    echo "    FAIL: $*"
    problems=$((problems + 1))
}

# version_gt A B -> true if version A > version B
version_gt() {
    awk -v a="$1" -v b="$2" 'BEGIN {
        split(a, x, "."); split(b, y, ".")
        for (i = 1; i <= 3; i++) {
            if (x[i] + 0 > y[i] + 0) exit 0
            if (x[i] + 0 < y[i] + 0) exit 1
        }
        exit 1
    }'
}

# minos_of FILE -> deployment target(s) from the load commands
minos_of() {
    otool -l "$1" | awk '
        /cmd LC_BUILD_VERSION$/ { b = 1; next }
        /cmd LC_VERSION_MIN_MACOSX$/ { v = 1; next }
        /cmd / { b = 0; v = 0 }
        b && $1 == "minos" { print $2; b = 0 }
        v && $1 == "version" { print $2; v = 0 }
    ' | sort -u
}

# deps_of FILE -> linked libraries (not the file's own install name)
deps_of() {
    otool -l "$1" | awk '
        /cmd LC_(LOAD_DYLIB|LOAD_WEAK_DYLIB|REEXPORT_DYLIB|LAZY_LOAD_DYLIB|LOAD_UPWARD_DYLIB)$/ { d = 1; next }
        /cmd / { d = 0 }
        d && $1 == "name" { print $2; d = 0 }
    '
}

# rpaths_of FILE -> LC_RPATH entries
rpaths_of() {
    otool -l "$1" | awk '
        /cmd LC_RPATH$/ { r = 1; next }
        /cmd / { r = 0 }
        r && $1 == "path" { print $2; r = 0 }
    '
}

is_macho() {
    file -b "$1" | grep -q '^Mach-O'
}

# The executables (mpv, lame) sit next to libs/; @executable_path is their dir.
exe_dir_of() {
    local dir
    dir="$(dirname "$1")"
    if [ "$(basename "$dir")" = libs ]; then
        dirname "$dir"
    else
        echo "$dir"
    fi
}

# inside_bundle PATH -> true if PATH exists and is inside DIR
inside_bundle() {
    [ -e "$1" ] || return 1
    local real
    real="$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
    case "$real" in
    "$DIR"/*) return 0 ;;
    *) return 1 ;;
    esac
}

# expand PATH LOADER_DIR EXE_DIR -> PATH with @loader_path/@executable_path replaced
expand() {
    local path=$1 loader_dir=$2 exe_dir=$3
    path="${path/#@loader_path/$loader_dir}"
    path="${path/#@executable_path/$exe_dir}"
    echo "$path"
}

check_file() {
    local f=$1 rel=${1#"$DIR"/}
    local exe_dir loader_dir minos archs deps ndeps=0
    exe_dir="$(exe_dir_of "$f")"
    loader_dir="$(dirname "$f")"
    minos="$(minos_of "$f" | tr '\n' ' ' | sed 's/ $//')"
    archs="$(lipo -archs "$f" 2>/dev/null || echo unknown)"
    deps="$(deps_of "$f")"
    [ -n "$deps" ] && ndeps=$(echo "$deps" | wc -l | tr -d ' ')
    echo "$rel: minos=${minos:-none} arch=$archs deps=$ndeps"

    # 1. minos
    if [ -z "$minos" ]; then
        problem "$rel: no LC_BUILD_VERSION or LC_VERSION_MIN_MACOSX"
    fi
    local m
    for m in $minos; do
        if version_gt "$m" "$MAX_MINOS"; then
            problem "$rel: minos $m > $MAX_MINOS"
        fi
    done

    # 2. arch
    if [ "$archs" != "$EXPECTED_ARCH" ]; then
        problem "$rel: arch is '$archs', expected '$EXPECTED_ARCH'"
    fi

    # 3. rpaths and linkage
    local rpaths rp
    rpaths="$(rpaths_of "$f")"
    # dyld refuses to load binaries built with a recent SDK that have these.
    for rp in $(echo "$rpaths" | sort | uniq -d); do
        problem "$rel: duplicate rpath $rp"
    done
    for rp in $rpaths; do
        case "$rp" in
        @loader_path* | @executable_path*)
            local expanded
            expanded="$(expand "$rp" "$loader_dir" "$exe_dir")"
            if ! inside_bundle "$expanded"; then
                problem "$rel: rpath $rp is outside the bundle"
            fi
            ;;
        /usr/lib/*) ;;
        *) problem "$rel: rpath $rp is outside the bundle" ;;
        esac
    done

    local dep
    for dep in $deps; do
        case "$dep" in
        /usr/lib/* | /System/Library/*) ;;
        @executable_path/libs/*)
            if ! inside_bundle "$(expand "$dep" "$loader_dir" "$exe_dir")"; then
                problem "$rel: $dep is missing from libs/"
            fi
            ;;
        @loader_path/*)
            if ! inside_bundle "$(expand "$dep" "$loader_dir" "$exe_dir")"; then
                problem "$rel: $dep does not resolve inside the bundle"
            fi
            ;;
        @rpath/*)
            # Search this file's rpaths, then those of the executables.
            local name=${dep#@rpath/} found=0 candidates exe
            candidates="$rpaths"
            for exe in "$exe_dir"/*; do
                [ -f "$exe" ] && is_macho "$exe" && candidates="$candidates $(rpaths_of "$exe")"
            done
            for rp in $candidates; do
                if inside_bundle "$(expand "$rp" "$loader_dir" "$exe_dir")/$name"; then
                    found=1
                    break
                fi
            done
            [ $found = 1 ] || problem "$rel: $dep does not resolve inside the bundle"
            ;;
        *) problem "$rel: links to $dep" ;;
        esac
    done
}

total=0
for arg in "$@"; do
    [ -d "$arg" ] || {
        problem "$arg is not a directory"
        continue
    }
    DIR="$(cd "$arg" && pwd -P)"
    echo "Checking $DIR"

    nfiles=0
    for f in $(find "$DIR" -type f | sort); do
        is_macho "$f" || continue
        nfiles=$((nfiles + 1))
        check_file "$f"
    done
    [ $nfiles -gt 0 ] || problem "no Mach-O files found under $DIR"
    total=$((total + nfiles))

    # 4. unexpected libraries
    for libs_dir in $(find "$DIR" -type d -name libs); do
        echo "Libraries in ${libs_dir#"$DIR"/}:"
        ls "$libs_dir" | sed 's/^/    /'
        for lib in "$libs_dir"/*; do
            name="$(basename "$lib")"
            for forbidden in $FORBIDDEN_LIBS; do
                case "$name" in
                "$forbidden".* | "$forbidden"-*) problem "unexpected library ${lib#"$DIR"/}" ;;
                esac
            done
        done
    done
done

if [ $problems -gt 0 ]; then
    echo "check-binaries: $problems problem(s)" >&2
    exit 1
fi
echo "check-binaries: $total Mach-O files OK (minos <= $MAX_MINOS, arch $EXPECTED_ARCH)"
