#!/bin/sh
# Usage:
# - Go to http://crowdin.net/project/ankidroid/activity_stream
# - Click on "More" a few times, up to the date of the last release
# - Save generated HTML as /tmp/activity-stream.html
# - Run this script

sed -e "s/suggested/suggested\n/g" /tmp/activity-stream.html | grep suggested | grep profile | sed -e "s/.*profile\///g" | sed -e "s/<\/a>.*//g" | sed -e "s/\".*//g" | sort -u | grep -v AnkiDroid | grep -v REMOVED_USER | sed -e "s/^/- /g"
