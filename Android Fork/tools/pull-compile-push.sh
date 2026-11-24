#!/bin/sh

# Small utility to quickly merge changes from others:
# 1) Pull from given branch
# 2) Check whether it compiles or not
# 3) If it compiles, push to server
#
# Usage (from the main AnkiDroid directory):
#   tools/pull-compile-push.sh flerda
#   tools/pull-compile-push.sh flerda issue677
#
# It assumes that your local name for flerda's branch is called bflerda,
# you might have to adapt it to your own conventions.
#
# Developer selection
DEFAULT_DEVELOPER="nobnago"
DEVELOPER=${1:-$DEFAULT_DEVELOPER}
# Branch selection
DEFAULT_BRANCH="main"
BRANCH=${2:-$DEFAULT_BRANCH}

# Change to the AnkiDroid project directory
cd "$(dirname "$0")/.."

# Pull
git pull https://github.com/$DEVELOPER/Anki-Android.git $BRANCH
if [ "$?" -ne "0" ]; then zenity --error --text 'pull failed'; exit 1; fi 

# Compile
ant clean debug
if [ "$?" -ne "0" ]; then zenity --error --text 'compile failed'; exit 1; fi 

# If compile worked, push
git push
if [ "$?" -ne "0" ]; then zenity --error --text 'push failed'; exit 1; fi 
