#!/bin/bash

set -e -o pipefail

# Placed in the public domain by David Allison <davidallisongithub@gmail.com>

# Converts an AnkiDroid issue/pull request ID to a well-formatted Discord link
#
# Input:
# ./format-for-discord 16804
#
# Output:
# [#16804: refactor/docs: `libs.versions.toml` changelogs/renames](<https://github.com/ankidroid/Anki-Android/issues/16804>)
#
# The output appears on Discord as the following, with no link preview
# #16804: refactor/docs: `libs.versions.toml` changelogs/renames

# SECURITY: This script assumes that Issue/PR titles are non-malicious.
# A malicious actor is able to 'break out' of the link formatting and insert custom text
# into a Discord message

OWNER=ankidroid
REPO=Anki-Android

# validate: arg 1 is a number >= 1
[[ $1 =~ [[:digit:]]+ && $1 -ge 1 ]] || { echo "arg 1 must be a positive integer. Received '$1'"; exit 1; }

# https://docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#get-an-issue
# The GitHub API treats every pull request as an issue, so we only need the ID
ISSUE_NUMBER=$1
ISSUE_TITLE=$(curl -L --silent  \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$OWNER/$REPO/issues/$ISSUE_NUMBER" | jq -r '.title')

# A number which is too high results in 'null', we may also want to check the API response
# at a later date (HTTP 429)
[[ "$ISSUE_TITLE" != null ]] || { echo "failed to read issue $ISSUE_NUMBER.\
 See: https://github.com/$OWNER/$REPO/issues/$ISSUE_NUMBER"; exit 1; }

# Discord syntax is standard markdown: [title](url)
# Wrapping a URL in <> means that it doesn't display a link preview

# Looks like: "#16804: refactor/docs: `libs.versions.toml` changelogs/renames"
# Links to: https://github.com/ankidroid/Anki-Android/issues/16804
# Redirects to: https://github.com/ankidroid/Anki-Android/pull/16804
echo "[#$ISSUE_NUMBER: $ISSUE_TITLE](<https://github.com/$OWNER/$REPO/issues/$ISSUE_NUMBER>)"