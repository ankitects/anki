#!/bin/bash

echo "Fetching jobs JSON for workflow run $1"

rm -f emulator_perf_results_page*.json

REPO_URL=https://api.github.com/repos/mikehardy/Anki-Android
PER_PAGE=100
PAGE=1
curl --silent "$REPO_URL/actions/runs/$1/jobs?per_page=$PER_PAGE&page=$PAGE" > emulator_perf_results_page"$PAGE".json

TOTAL_COUNT=$(jq '.total_count' emulator_perf_results.json)
LAST_PAGE=$((TOTAL_COUNT / PER_PAGE + 1))
echo "$TOTAL_COUNT jobs so $LAST_PAGE pages"
for ((PAGE=2; PAGE <= LAST_PAGE; PAGE++)); do
  echo "On iteration $PAGE"
  curl --silent "$REPO_URL/actions/runs/$1/jobs?per_page=$PER_PAGE&page=$PAGE" > emulator_perf_results_page"$PAGE".json
done

jq -s 'def deepmerge(a;b):
  reduce b[] as $item (a;
    reduce ($item | keys_unsorted[]) as $key (.;
      $item[$key] as $val | ($val | type) as $type | .[$key] = if ($type == "object") then
        deepmerge({}; [if .[$key] == null then {} else .[$key] end, $val])
      elif ($type == "array") then
        (.[$key] + $val | unique)
      else
        $val
      end)
    );
  deepmerge({}; .)' emulator_perf_results_page*.json > emulator_perf_results.json

rm -f emulator_perf_results_page*.json