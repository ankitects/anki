#!/bin/bash
# Diff Roborazzi screenshot tests between a provided baseline commit and HEAD.
#
# Usage:
#   tools/compare-screenshot-test.sh <baseline-commit> [test-class-fqn] [-Ptheme=dark] [-Pdevice=tablet]
#   tools/compare-screenshot-test.sh HEAD                          # diff working tree vs last commit
#
# With no test filter, runs every test tagged ScreenshotTestCategory.
#
# Implementation:
#   Baseline output is produced in a worktree: ../Anki-Android-screenshot-baseline

set -euo pipefail

BASELINE=""
TEST_FILTER=""
GRADLE_EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        -P*)
            GRADLE_EXTRA_ARGS+=("$1")
            shift
            ;;
        *)
            if [ -z "$BASELINE" ]; then
                BASELINE="$1"
            elif [ -z "$TEST_FILTER" ]; then
                TEST_FILTER="$1"
            else
                echo "error: unexpected argument '$1'" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Ensure the commit hash is provided as the first argument
if [ -z "$BASELINE" ]; then
    echo "usage: $(basename "$0") <baseline-commit> [test-class-fqn] [-P<arg>...]" >&2
    echo "       pass 'HEAD' to diff only your uncommitted changes" >&2
    exit 1
fi

# Validate the baseline commit (arg 1)
if ! git rev-parse --verify --quiet "${BASELINE}^{commit}" >/dev/null; then
    echo "error: baseline '$BASELINE' is not a valid git commit" >&2
    exit 1
fi

BASELINE_SHA="$(git rev-parse --verify "$BASELINE")"
HEAD_SHA="$(git rev-parse HEAD)"

# Fail if using HEAD with a clean working tree
if [ "$BASELINE_SHA" = "$HEAD_SHA" ] \
   && git diff --quiet HEAD && git diff --cached --quiet; then
    echo "error: nothing to compare. No unstaged changes." >&2
    exit 1
fi


# /Users/davidallison/StudioProjects/Anki-Android
REPO_ROOT="$(git rev-parse --show-toplevel)"
# /Users/davidallison/StudioProjects/Anki-Android-screenshot-baseline
WORKTREE_DIR="${REPO_ROOT}/../Anki-Android-screenshot-baseline"
OUT_DIR="${REPO_ROOT}/AnkiDroid/build/outputs/roborazzi"
WORKTREE_OUT="${WORKTREE_DIR}/AnkiDroid/build/outputs/roborazzi"

# baseline  4d7daca42e  test(card-browser): screenshot test
# HEAD      18377ab3d1  feat(card-browser): enable edge to edge (+ uncommitted changes)
echo "Comparing:"
echo "baseline  $(git log -1 --format='%h  %s' "$BASELINE")"
head_line="$(git log -1 --format='%h  %s' HEAD)"
if ! git diff --quiet HEAD || ! git diff --cached --quiet; then
    head_line="${head_line} (+ uncommitted changes)"
fi
echo "HEAD      ${head_line}"
# If the user picked a non-HEAD baseline, remind them HEAD is also valid
# and is the quickest way to check just their uncommitted edits.
if [ "$BASELINE_SHA" != "$HEAD_SHA" ]; then
    echo "Tip: pass 'HEAD' to diff only your uncommitted changes."
fi
echo

# Display a tip to use arg 2
GRADLE_TESTS_ARG=()
if [ -n "$TEST_FILTER" ]; then
    GRADLE_TESTS_ARG=(--tests "$TEST_FILTER")
else
    echo "Tip: use the 2nd arg to limit test runs. Syntax: \"com.ichi2.anki.**Test\""
    echo
fi

## Function definitions

# ANSI red, only when stdout is a TTY (no escape codes in pipes / CI logs).
if [ -t 1 ]; then
    RED=$'\033[31m'
    NC=$'\033[0m'
else
    RED='' NC=''
fi

# Silence IPackageStatsObserver.java uses or overrides a deprecated API. Maintain progress output.
gradle_quiet() {
    "$@" 2> >(grep -v -E '^Note:' >&2)
}

# Open a directory in the OS file manager
open_dir() {
    case "$(uname -s)" in
        Darwin)               open "$1" ;;
        Linux)                xdg-open "$1" >/dev/null 2>&1 ;;
        MINGW*|MSYS*|CYGWIN*) start "" "$1" ;;
        *) echo "warning: don't know how to open '$1' on $(uname -s)" >&2 ;;
    esac
}

# Create or reuse the screenshot-baseline worktree (../Anki-Android-screenshot-baseline)
worktree_head=""
if [ -e "$WORKTREE_DIR/.git" ]; then
    worktree_head="$(git -C "$WORKTREE_DIR" rev-parse HEAD 2>/dev/null || true)"
fi
if [ "$worktree_head" = "$BASELINE_SHA" ]; then
    echo "==> Reusing existing worktree at $BASELINE"
else
    git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
    rm -rf "$WORKTREE_DIR" # handle a hard kill.
    git worktree add "$WORKTREE_DIR" "$BASELINE" >/dev/null
fi

# Keep in sync with .worktreeinclude.
# Fixes 'SDK location not found'
if [ -f "${REPO_ROOT}/local.properties" ]; then
    cp "${REPO_ROOT}/local.properties" "${WORKTREE_DIR}/local.properties"
fi

echo "==> Recording baseline at $BASELINE"
gradle_quiet "${WORKTREE_DIR}/gradlew" -p "$WORKTREE_DIR" \
    :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot -q \
    -x installGitHook \
    ${GRADLE_TESTS_ARG[@]+"${GRADLE_TESTS_ARG[@]}"} \
    ${GRADLE_EXTRA_ARGS[@]+"${GRADLE_EXTRA_ARGS[@]}"}

# Clear stale Roborazzi outputs
gradle_quiet "${REPO_ROOT}/gradlew" -p "$REPO_ROOT" \
    :AnkiDroid:clearRoborazziPlayDebug -q

# Copy baselines to outputs/roborazzi/<TestClass>/**
echo "==> Copying baselines to ${OUT_DIR#${REPO_ROOT}/}"
staged=0
shopt -s nullglob
for class_dir in "${WORKTREE_OUT}"/*/; do
    [ -d "$class_dir" ] || continue
    class_name="$(basename "$class_dir")"
    pngs=("${class_dir}"*.png)
    if [ ${#pngs[@]} -eq 0 ]; then
        echo "warning: skipping empty class dir ${class_name}" >&2
        continue
    fi
    mkdir -p "${OUT_DIR}/${class_name}"
    cp "${pngs[@]}" "${OUT_DIR}/${class_name}/"
    staged=1
done
shopt -u nullglob

if [ $staged -eq 0 ]; then
    echo "error: baseline run produced no screenshots. Does the test filter '${TEST_FILTER:-(none)}' match any @Category(ScreenshotTestCategory) tests?" >&2
    exit 1
fi

echo "==> Comparing baseline to HEAD"
# *_actual.png / *_compare.png are written if there are differences
gradle_quiet "${REPO_ROOT}/gradlew" -p "$REPO_ROOT" \
    :AnkiDroid:compareRoborazziPlayDebug -Pscreenshot -q \
    ${GRADLE_TESTS_ARG[@]+"${GRADLE_TESTS_ARG[@]}"} \
    ${GRADLE_EXTRA_ARGS[@]+"${GRADLE_EXTRA_ARGS[@]}"}


# Recurse — Roborazzi writes *_compare.png inside directories
diffs=()
while IFS= read -r line; do
    diffs+=("$line")
done < <(find "$OUT_DIR" -type f -name '*_compare.png')

if [ ${#diffs[@]} -eq 0 ]; then
    label="${TEST_FILTER:-all screenshot tests}"
    echo "No visual difference between $BASELINE and HEAD for ${label}."
    exit 0
fi

# Screenshots were not equivalent. Explain and link the issues

#   1 visual diff(s):
#     diff: file:///Users/.../AnkiDroid/build/outputs/roborazzi/30_notes_compare.png
echo "${RED}${#diffs[@]} visual diff(s)${NC} in file://${OUT_DIR}"
for compare in "${diffs[@]}"; do
    echo "  ${RED}diff:${NC} file://${compare}"
done

# Offer to open AnkiDroid/build/outputs/roborazzi if running in a TTY
if [ -t 0 ]; then
    read -n 1 -r -p "Press [R] to reveal files: "
    echo
    if [[ "$REPLY" =~ ^[Rr]$ ]]; then
        open_dir "$OUT_DIR"
    fi
fi

exit 1