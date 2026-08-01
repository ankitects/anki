#!/bin/bash
#
# Upload codacy coverage reports.
#
# Partial uploads for APIs, with an option to finalize.
#

set -e

export JAR=~/codacy-coverage-reporter-assembly.jar

curl -Ls -o $JAR https://github.com/codacy/codacy-coverage-reporter/releases/download/6.0.6/codacy-coverage-reporter-6.0.6-assembly.jar

if [[ ( "$API" != "NONE" ) ]]; then
    java -jar $JAR report \
        -l Java \
        -r AnkiDroid/build/reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml \
        --partial
fi

if [[ ( "$UNIT_TEST" == "TRUE" ) ]]; then
    java -jar $JAR report \
        -l Java \
        -r AnkiDroid/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml \
        --partial
fi

if [[ ( "$FINALIZE_COVERAGE" == "TRUE" ) ]]; then
    java -jar $JAR final
fi

