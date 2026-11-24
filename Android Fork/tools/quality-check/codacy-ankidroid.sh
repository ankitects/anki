#!/bin/bash

# This script can help you see how you stand vs Codacy review BEFORE you issue a Pull Request
#
# You need to install Docker first
# You need to install 'codacy-analysis-cli' second
#
# You should run this against main frequently to generate a base for comparison
# Then pre-checkin you run this against your branch and diff your branch vs main

SOURCE_BASE=/home/$USER/work/AnkiDroid
SOURCE=$SOURCE_BASE/Anki-Android
TIMESTAMP=`date +"%Y%m%d-%H%M%S"`
TOKEN=<SECRET TOKEN, YOU NEED TO REQUEST THIS TOKEN FROM TIM OR MIKE>

if [ "$1" == "" ]; then
  OUTPUT=$SOURCE_BASE/reports/codacy/codacy-report-$TIMESTAMP.txt
  echo "no suffix specified - output to $OUTPUT"
else
  OUTPUT=$SOURCE_BASE/reports/codacy/codacy-report-$1.txt
  echo "suffix specifed - output to $OUTPUT"
fi

codacy-analysis-cli analyse --directory $SOURCE --project-token $TOKEN -p 6 -f text -o $SOURCE/build/reports/codacy-report.txt

mkdir -p $SOURCE_BASE/reports/codacy/
cat $SOURCE/build/reports/codacy-report.txt | sort > $OUTPUT
