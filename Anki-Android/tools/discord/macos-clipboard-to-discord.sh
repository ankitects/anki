#!/bin/bash

set -e -o pipefail

# Placed in the public domain by David Allison <davidallisongithub@gmail.com>

# If the clipboard contains a link to an AnkiDroid Issue/PR, replace it with a well-formatted
# link for use in Discord.
#
# Input:
# ./macos-clipboard-to-discord.sh
# with 'https://github.com/ankidroid/Anki-Android/pull/16804' on the clipboard
#
# Output:
# copied '[#16804: refactor/docs: `libs.versions.toml` changelogs/renames](<https://github.com/ankidroid/Anki-Android/issues/16804>)' to clipboard
#
# The above link is displayed in Discord as the following, with no link preview:
# #16804: refactor/docs: `libs.versions.toml` changelogs/renames
#
# See:
# ./format-for-discord


OWNER=ankidroid
REPO=Anki-Android

ISSUE_REGEX=https://github.com/$OWNER/$REPO/issues/\(\[\[:digit:\]\]+\)
PR_REGEX=https://github.com/$OWNER/$REPO/pull/\(\[\[:digit:\]\]+\)

CLIPBOARD=$(pbpaste)

if ! [[ "$CLIPBOARD" =~ $ISSUE_REGEX ]] && ! [[ "$CLIPBOARD" =~ $PR_REGEX ]]; then
  echo "No issue/PR link found on clipboard"
  exit 1
fi

ISSUE_NUMBER="${BASH_REMATCH[1]}"
# format-for-discord accepts either a PR number or an issue number
OUTPUT_FOR_DISCORD=$(./format-for-discord.sh "$ISSUE_NUMBER")

echo "$OUTPUT_FOR_DISCORD" | pbcopy
echo "copied '$OUTPUT_FOR_DISCORD' to clipboard"