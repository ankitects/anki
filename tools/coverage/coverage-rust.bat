@echo off
setlocal

rem cargo-llvm-cov's llvm-profdata uses the host toolchain, which is incompatible
rem with the profraw files produced when running on Windows ARM64.
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    echo Rust coverage is not supported on Windows ARM64 ^(llvm-profdata format mismatch^).
    echo Run on Linux or let CI enforce coverage.
    exit /b 0
)

set "outdir=out\coverage\rust"
set "LLVMCOVPATH=out\bin"

if not exist %outdir% mkdir %outdir%
if not exist %LLVMCOVPATH% mkdir %LLVMCOVPATH%
if not exist %LLVMCOVPATH%\cargo-llvm-cov.exe (
    cargo install cargo-llvm-cov --version 0.8.4 --locked --root out || exit /b 1
)

set "ANKI_TEST_MODE=1"
%LLVMCOVPATH%\cargo-llvm-cov llvm-cov --workspace --locked --json --summary-only ^
    --output-path %outdir%\coverage-summary.json --fail-under-lines 60 || exit /b 1

if "%1"=="--html" (
    %LLVMCOVPATH%\cargo-llvm-cov llvm-cov report --html --output-dir %outdir%\html || exit /b 1
    echo Rust coverage report: %outdir%\html\index.html
)
