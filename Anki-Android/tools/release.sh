#!/bin/bash
# Perform a release of AnkiDroid
# Nicolas Raoul 2011/10/24
# Timothy Rae 2014/11/10
# Mike Hardy 2020/05/15
# Usage from AnkiDroid's root directory:
# tools/release.sh # For an alpha or beta release
# tools/release.sh public # For a public (non alpha/beta) release


# Basic expectations
# - tools needed: sed, gawk, gh, git
# - authority needed: ability to commit/tag/push directly to main branch in AnkiDroid, ability to create releases
# - ankidroiddocs checked out in a sibling directory (that is, '../ankidroiddocs' should exist with 'upstream' remote set correctly)

# Suffix configuration
SUFFIX=""
PUBLIC=$1

# Make sure we can find our binaries
export PATH="$HOME/bin:$PATH"

# Check basic expectations
echo "Checking basic utility installation status..."
for UTILITY in sed gawk gh asciidoctor; do
  if ! command -v "$UTILITY" >/dev/null 2>&1; then echo "$UTILITY" missing; exit 1; fi
done
if [ "$PUBLIC" = "public" ] && ! [ -f ../ankidroiddocs/changelog.asc ]; then
  echo "Could not find ../ankidroiddocs/changelog.asc?"
  exit 1
fi

# Captured once so every APK in this release reports the same BUILD_TIME
# (consumed by AnkiDroid/build.gradle via -PbuildTime).
BUILD_TIME_MS=$(date +%s000)
export BUILD_TIME_MS

# Define the location of the manifest file
SRC_DIR="./AnkiDroid"
GRADLEFILE="$SRC_DIR/build.gradle"
CHANGELOG="$SRC_DIR/src/main/assets/changelog.html"

if ! VERSION=$(grep 'versionName=' $GRADLEFILE | sed -e 's/.*="//' | sed -e 's/".*//')
then
  echo "Unable to get current version. Is sed installed?"
  exit 1
fi

if [ "$PUBLIC" = "public" ]; then
  echo "About to perform a public release. Please first:"
  echo "- Edit the version in $GRADLEFILE manually but do not commit it."
  echo "- Author and merge a PR to ankidroiddocs/changelog.asc with details for the current version"
  echo "Press Enter to continue."
  read -r

  # Render the new changelog
  echo "Rendering changelog..."
  if ! asciidoctor -a webfonts! ../ankidroiddocs/changelog.asc -o "$CHANGELOG"
  then
    echo "Failed to render changelog?"
    exit 1
  fi
  if ! grep "Version $VERSION " "$CHANGELOG"
  then
    echo "Could not find entry for version $VERSION in rendered $CHANGELOG ?"
    exit 1
  fi

else
  echo "Performing testing release."
fi

if [ "$PUBLIC" != "public" ]; then
  # Increment version name
  # Ex: 2.1beta7 to 2.1beta8
  PREVIOUS_VERSION=$VERSION
  if [ -n "$SUFFIX" ]; then
    PREVIOUS_VERSION="${PREVIOUS_VERSION//$SUFFIX/}"
  fi
  if ! GUESSED_VERSION=$(echo "$PREVIOUS_VERSION" | gawk -f tools/lib/increase-version.awk)
  then
    echo "Unable to guess next version. Is gawk installed?";
    exit 1;
  fi
  VERSION=${1:-$GUESSED_VERSION$SUFFIX}

  # Increment version code
  # It is an integer in AndroidManifest that nobody actually sees.
  # Ex: 72 to 73
  PREVIOUS_CODE=$(grep 'versionCode=' $GRADLEFILE | sed -e 's/.*=//')
  GUESSED_CODE=$((PREVIOUS_CODE + 1))

  # Edit AndroidManifest.xml to bump version string
  echo "Bumping version from $PREVIOUS_VERSION$SUFFIX to $VERSION (and code from $PREVIOUS_CODE to $GUESSED_CODE)"
  sed -i -e s/"$PREVIOUS_VERSION""$SUFFIX"/"$VERSION"/g $GRADLEFILE
  sed -i -e s/versionCode="$PREVIOUS_CODE"/versionCode="$GUESSED_CODE"/g $GRADLEFILE
fi

# If any changes go in during the release process, pushing fails, so push immediately.
# Worst case this burns a version number despite a failure later, and we have a version/tag
# that never launched. That's better than having to manually patch up build.gradle.kts and push a tag
# for a release that did launch, but the push failed
echo "Committing changelog and version bump via git"
git add $GRADLEFILE $CHANGELOG
git commit -m "Bumped version to $VERSION"
git tag v"$VERSION"

. tools/check-keystore.sh

# Build signed APK using Gradle and publish to Play.
# Do this before building universal of the play flavor so the universal is not uploaded to Play Store
# Configuration for pushing to Play specified in build.gradle.kts 'play' task
echo "Running 'publishPlayReleaseApk' gradle target"
./gradlew --stop
if ! ./gradlew publishPlayReleaseApk -PbuildTime="$BUILD_TIME_MS" --no-configuration-cache
then
  # APK contains problems, abort release
  exit 1
fi

# If the Play Store accepted the builds, the version bump should be pushed / made concrete
git push
git push --tags

# Build the full set of release APKs for all flavors, with universals
UCFLAVORS='Full Amazon Play'
for UCFLAVOR in $UCFLAVORS; do
  ./gradlew --stop
  echo Running assemble"$UCFLAVOR"Release target with universal APK flag
  if ! ./gradlew assemble"$UCFLAVOR"Release -Duniversal-apk=true -PbuildTime="$BUILD_TIME_MS" --no-configuration-cache
  then
    echo "unable to build release APKs for flavor $UCFLAVOR"
    exit 1
  fi
done

# PREFIX is used to order the apks in the file list. Most users will use `arm64-v8a`.
# variant ABIs are a source of error and confusion, so should be lower in the list
PREFIX=""
# Copy full ABI APKs to cwd
ABIS='arm64-v8a x86 x86_64 armeabi-v7a'
for ABI in $ABIS; do
  if [ "$ABI" = "arm64-v8a" ]; then
    PREFIX=""
  else
    PREFIX="variant-abi-"
  fi
  cp AnkiDroid/build/outputs/apk/full/release/AnkiDroid-full-"$ABI"-release.apk "$PREFIX"AnkiDroid-"$VERSION"-"$ABI".apk
done

# Copy universal APKs for all flavors to cwd
FLAVORS='full amazon play'
for FLAVOR in $FLAVORS; do
  if [ "$FLAVOR" = "full" ]; then
    PREFIX=""
  else
    PREFIX="dev-"
  fi
  cp AnkiDroid/build/outputs/apk/"$FLAVOR"/release/AnkiDroid-"$FLAVOR"-universal-release.apk "$PREFIX"AnkiDroid-"$VERSION"-"$FLAVOR"-universal.apk
done

# Pack up our proguard mapping file for debugging in case needed
PREFIX="z-"
tar -zcf proguard-mappings.tar.gz AnkiDroid/build/outputs/mapping
cp proguard-mappings.tar.gz "$PREFIX"proguard-mappings.tar.gz

# Create a full universal build that disables minify, to help diagnose proguard issues
./gradlew --stop
echo Running assembleFullRelease target with universal APK flag and MINIFY_ENABLED=false
if ! MINIFY_ENABLED=false ./gradlew assembleFullRelease -Duniversal-apk=true -PbuildTime="$BUILD_TIME_MS" --no-configuration-cache
then
  echo "unable to build full unminified APKs"
  exit 1
fi
# Copy our unminified full universal release out
cp AnkiDroid/build/outputs/apk/full/release/AnkiDroid-full-universal-release.apk "$PREFIX"AnkiDroid-"$VERSION"-full-universal-nominify.apk

# Push to Github Releases.
GITHUB_TOKEN=$(cat ~/src/my-github-personal-access-token)
export GITHUB_TOKEN
export GITHUB_USER="ankidroid"
export GITHUB_REPO="Anki-Android"

if [ "$PUBLIC" = "public" ]; then
  RELEASE_TYPE="--latest"
else
  RELEASE_TYPE="--prerelease"
fi

# Get the directory of the current script
SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
# Read the content of the markdown file using the absolute path
RELEASE_NOTES="$SCRIPT_DIR/release-description.md"
echo "Creating new Github release"
gh release create v"$VERSION" --title "AnkiDroid $VERSION" -F "$RELEASE_NOTES" $RELEASE_TYPE --draft

echo "Sleeping 30s to make sure the release exists, see issue 11746"
sleep 30

# Upload full ABI APKs to GitHub release
for ABI in $ABIS; do
  if [ "$ABI" = "arm64-v8a" ]; then
    PREFIX=""
  else
    PREFIX="variant-abi-"
  fi
  echo "Adding full APK for $ABI to GitHub release"
  gh release upload v"$VERSION" "$PREFIX"AnkiDroid-"$VERSION"-"$ABI".apk
done

# Upload universal APKs for all flavors to GitHub release
for FLAVOR in $FLAVORS; do
  if [ "$FLAVOR" = "full" ]; then
    PREFIX=""
  else
    PREFIX="dev-"
  fi
  echo "Adding full APK for $FLAVOR to GitHub release"
  gh release upload v"$VERSION" "$PREFIX"AnkiDroid-"$VERSION"-"$FLAVOR"-universal.apk
done

# Set to z- for un-minified full universal APK and proguard to ensure it is at the end of the list
PREFIX="z-"
echo "Adding un-minified full universal APK to GitHub release"
gh release upload v"$VERSION" "$PREFIX"AnkiDroid-"$VERSION"-full-universal-nominify.apk
echo "Adding proguard mappings file to Github release"
gh release upload v"$VERSION" "$PREFIX"proguard-mappings.tar.gz

# Not publishing to amazon pending: https://github.com/ankidroid/Anki-Android/issues/14161
#if [ "$PUBLIC" = "public" ]; then
#  ./gradlew --stop
#  echo "Running 'publishToAmazonAppStore' gradle target"
#  if ! ./gradlew publishToAmazonAppStore
#  then
#    echo "Unable to publish to amazon app store"
#    exit 1
#  fi
#  echo "Remember to add release notes and submit on Amazon: https://developer.amazon.com/apps-and-games/console/app/list"
#fi

# Now that Git is clean and the main release is done, run the parallel release script and upload them
echo "Running parallel package build"
./gradlew --stop
./tools/parallel-package-release.sh "$VERSION"
if [ "$PUBLIC" = "public" ]; then
  BUILDNAMES='A B C D E' # For public builds we will post all parallels
else
  BUILDNAMES='A B' # For alpha releases just post a couple parallel builds
fi
for BUILD in $BUILDNAMES; do
  echo "Adding parallel build $BUILD to Github release"
  gh release upload v"$VERSION" AnkiDroid-"$VERSION".parallel."$BUILD".apk
done

# For publishing the draft release and making it immutable;
echo "Publishing release (making it immutable)"
if ! gh release edit v"$VERSION" --draft=false;
then
  echo "Failed to publish release"
  exit 1
fi
echo "Release published successfully"
