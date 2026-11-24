@echo off
setlocal

set "outdir=out\coverage\typescript"
set "YARN=out\extracted\node\yarn.cmd"

if not exist %outdir% mkdir %outdir%

set "COVERAGE_ARGS=--coverage.enabled true --coverage.provider=v8"
if "%1"=="--html" set "ANKI_COVERAGE_HTML=1"

%YARN% vitest:once %COVERAGE_ARGS% --coverage.reportsDirectory=..\%outdir% --coverage.thresholds.lines=5 || exit /b 1

if "%1"=="--html" (
    echo TypeScript coverage report: %outdir%\index.html
)
