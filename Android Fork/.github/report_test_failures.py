#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Report failures, skipped tests, and overview summary from JUnit XML using the standard library."""

import argparse
import html
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


TABLE_HEADER = (
    "## Test Results\n\n"
    "| Suite | Result | Duration | Tests | Passed | Failed | Skipped |\n"
    "| ----- | ------ | -------- | ----- | ------ | ------ | ------- |\n"
)


def format_duration(seconds):
    minutes = int(seconds // 60)
    rem_seconds = seconds % 60
    return f"{minutes:02d}m {rem_seconds:06.3f}s"


def read_ignored_tests(log_file):
    ignored = set()
    failed = set()
    if log_file is None or not log_file.is_file():
        return ignored
    with log_file.open(encoding="utf-8", errors="replace") as log:
        for line in log:
            match = re.search(r"\bTestRunner: (ignored|failed): (.+)\(([^()]+)\)\s*$", line)
            if match:
                status, method, classname = match.groups()
                (ignored if status == "ignored" else failed).add(f"{classname}.{method}")
    return ignored - failed


def get_report_duration(root):
    suites = root.findall("testsuite")
    if suites:
        total = 0.0
        for s in suites:
            try:
                total += float(s.get("time", 0) or 0)
            except (ValueError, TypeError):
                pass
        if total > 0.0:
            return total
    try:
        root_time = float(root.get("time", 0) or 0)
        if root_time > 0.0:
            return root_time
    except (ValueError, TypeError):
        pass
    total = 0.0
    for tc in root.iter("testcase"):
        try:
            total += float(tc.get("time", 0) or 0)
        except (ValueError, TypeError):
            pass
    return total


def main(results_dir, log_file=None, pattern="TEST-*.xml", title="Test failures", suite=None):
    failures = []
    skipped = []
    ignored = read_ignored_tests(log_file)
    total_duration = 0.0
    passed_count = 0
    failed_count = 0
    skipped_count = 0

    for report in sorted(results_dir.rglob(pattern)):
        try:
            root = ET.parse(report).getroot()
        except (ET.ParseError, OSError) as error:
            if os.environ.get("GITHUB_ACTIONS") == "true":
                print(f"::warning::Could not read {report}: {error}")
            else:
                print(f"Warning: Could not read {report}: {error}", file=sys.stderr)
            continue

        total_duration += get_report_duration(root)

        for test in root.iter("testcase"):
            name = f"{test.get('classname', '')}.{test.get('name', '')}"
            has_failure = False
            has_skipped = False

            for result in test:
                if result.tag not in ("failure", "error", "skipped"):
                    continue
                details = "".join(result.itertext()).strip()
                message = result.get("message", "")
                # Android can report assumption violations as failures or errors.
                # Only match the top-level exception, not mentions in a real failure.
                exception_type = result.get("type") or (details or message).partition("\n")[0].partition(":")[0]
                # The Android test engine also writes @Ignore as an empty failure.
                # Require the runner's ignore event before treating an empty failure as skipped.
                is_ignored = name in ignored and not (details or message or result.get("type"))
                if message and message not in details:
                    details = f"{message}\n{details}".strip()
                if result.tag == "skipped" or is_ignored or exception_type in (
                    "org.junit.AssumptionViolatedException",
                    "org.junit.internal.AssumptionViolatedException",
                ):
                    has_skipped = True
                    skipped.append(f"{name}\n{details or result.get('type', 'Test skipped')}")
                else:
                    has_failure = True
                    failures.append(f"{name}\n{details or result.get('type', 'Test failed')}")

            if has_failure:
                failed_count += 1
            elif has_skipped:
                skipped_count += 1
            else:
                passed_count += 1

    total_tests = passed_count + failed_count + skipped_count
    is_successful = failed_count == 0 and total_tests > 0
    result_text = "SUCCESS" if is_successful else "FAILURE"
    elapsed = format_duration(total_duration)

    if suite:
        print(
            f"{suite}: {result_text} ({elapsed}) - "
            f"{total_tests} tests: {passed_count} passed, {failed_count} failed, {skipped_count} skipped"
        )

    for failure in failures:
        if os.environ.get("GITHUB_ACTIONS") == "true":
            # GitHub renders the escaped message as a multiline error in the log.
            message = failure.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error::{message}")
        else:
            print(failure, end="\n\n")

    if skipped:
        skipped_title = f"Skipped tests ({len(skipped)})"
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::group::{skipped_title}")
        else:
            print(skipped_title, end="\n\n")
        for skip in skipped:
            print(skip, end="\n\n")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print("::endgroup::")

    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a+", encoding="utf-8") as summary:
            summary.seek(0)
            existing_content = summary.read()
            if suite:
                if "## Test Results" not in existing_content:
                    summary.write(TABLE_HEADER)
                summary.write(f"| {suite} | **{result_text}** | {elapsed} | {total_tests} | {passed_count} | {failed_count} | {skipped_count} |\n\n")

            if failures:
                summary.write(f"### {title}\n\n")
                for failure in failures:
                    summary.write(f"<pre>{html.escape(failure)}</pre>\n\n")
            if skipped:
                skipped_title = f"Skipped tests ({len(skipped)})"
                summary.write(f"<details><summary>{skipped_title}</summary>\n\n")
                for skip in skipped:
                    summary.write(f"<pre>{html.escape(skip)}</pre>\n\n")
                summary.write("</details>\n\n")

    return 1 if (failures or (suite and not is_successful)) else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results_dir", type=Path)
    parser.add_argument("log_file", type=Path, nargs="?")
    parser.add_argument("--pattern", default="TEST-*.xml", help="Recursive report glob relative to results_dir")
    parser.add_argument("--title", default="Test failures", help="Failure heading in the job summary")
    parser.add_argument("--suite", help="Suite name for markdown summary table")
    args = parser.parse_args()
    sys.exit(main(args.results_dir, args.log_file, args.pattern, args.title, args.suite))
