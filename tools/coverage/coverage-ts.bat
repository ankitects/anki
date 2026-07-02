@echo off
setlocal

set "outdir=out\coverage\typescript"
set "YARN=out\extracted\node\yarn.cmd"

if not exist %outdir% mkdir %outdir%

set "COVERAGE_ARGS=--coverage.enabled true --coverage.provider=v8 --coverage.reporter=text-summary --coverage.reporter=json-summary"
if "%1"=="--html" set "COVERAGE_ARGS=%COVERAGE_ARGS% --coverage.reporter=html"

%YARN% vitest:once %COVERAGE_ARGS% --coverage.reportsDirectory=..\%outdir% --coverage.thresholds.lines=5 || exit /b 1

if "%1"=="--html" (
    echo TypeScript coverage report: %outdir%\index.html
)
