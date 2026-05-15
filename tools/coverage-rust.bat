@echo off
setlocal

if not exist out\coverage\rust mkdir out\coverage\rust
if not exist out\bin mkdir out\bin
if not exist out\bin\cargo-llvm-cov.exe (
    cargo install cargo-llvm-cov --version 0.8.4 --locked --root out || exit /b 1
)

set "ANKI_TEST_MODE=1"
out\bin\cargo-llvm-cov llvm-cov --workspace --locked --json --summary-only ^
    --output-path out\coverage\rust\coverage-summary.json --fail-under-lines 60 || exit /b 1
if "%1"=="--html" (
    out\bin\cargo-llvm-cov llvm-cov report --html --output-dir out\coverage\rust\html || exit /b 1
    echo Rust coverage report: out\coverage\rust\html\index.html
)
