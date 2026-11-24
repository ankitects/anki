#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

RESULTS = Path("baselineprofile/build/outputs/androidTest-results/connected")
SMOKE_TEST = [
    "./gradlew",
    ":baselineprofile:connectedBenchmarkBenchmarkAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.ichi2.anki.baselineprofile.ReleaseSmokeTest",
    "-Pandroid.testInstrumentationRunnerArguments.releaseSmokeTest=true",
    "-x",
    "lintVitalPlayRelease",
    "--daemon",
    "--stacktrace",
]
UNFINISHED_TEST_ERROR = b"Test failed with status -1"


def adb_connection(serial):
    devices = subprocess.run(["adb", "devices", "-l"], capture_output=True, text=True).stdout
    return [line.split() for line in devices.splitlines() if line.split()[:1] == [serial]]


def read_varint(data, position):
    value = shift = 0
    while data[position] & 0x80:
        value |= (data[position] & 0x7F) << shift
        position += 1
        shift += 7
    return value | data[position] << shift, position + 1


def length_delimited_fields(data):
    position = 0
    while position < len(data):
        key, position = read_varint(data, position)
        if key & 7 == 0:
            position = read_varint(data, position)[1]
        elif key & 7 == 2:
            length, position = read_varint(data, position)
            yield key >> 3, data[position : position + length]
            position += length
        else:
            position += 8 if key & 7 == 1 else 4


def unfinished_tests():
    tests = set()
    for report in RESULTS.rglob("test-result.pb"):
        for field, value in length_delimited_fields(report.read_bytes()):
            result = dict(length_delimited_fields(value)) if field == 3 else {}
            if dict(length_delimited_fields(result.get(3, b""))).get(1) == UNFINISHED_TEST_ERROR:
                test = dict(length_delimited_fields(result.get(1, b"")))
                package, cls, method = (test.get(field, b"").decode() for field in (2, 1, 3))
                tests.add((f"{package}.{cls}" if package else cls, method))
    return tests


def has_test_result():
    for report in RESULTS.rglob("TEST-*.xml"):
        for test in ET.parse(report).getroot().iter("testcase"):
            failures = [result for result in test if result.tag in ("failure", "error")]
            if not failures or any(result.attrib or "".join(result.itertext()).strip() for result in failures):
                return True
            if (test.get("classname"), test.get("name")) not in unfinished_tests():
                return True
    return False


def run_smoke_test():
    shutil.rmtree(RESULTS, ignore_errors=True)
    return subprocess.run(SMOKE_TEST).returncode


def main():
    serial = os.environ["ANDROID_SERIAL"]
    connection = adb_connection(serial)
    status = run_smoke_test()
    if not has_test_result() and adb_connection(serial) != connection:
        print(f"ADB reconnected to {serial} before the smoke test reported a result; running it again.", file=sys.stderr)
        subprocess.run(["adb", "-s", serial, "wait-for-device"], timeout=60, check=True)
        status = run_smoke_test()
    if status == 0 and not has_test_result():
        print("The smoke test passed without reporting a result.", file=sys.stderr)
        return 1
    return status


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.SubprocessError) as error:
        sys.exit(str(error))
