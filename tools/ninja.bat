@echo off
set CARGO_TARGET_DIR=%~dp0..\out\rust
set "RECONFIGURE_KEY=%SOURCEMAP%;%HMR%;%CI%"
set RUNNER_PROFILE=release
if "%CI%"=="true" if "%RELEASE%"=="" set RUNNER_PROFILE=ci
REM separate build+run steps so build env doesn't leak into subprocesses
cargo build -p runner --profile %RUNNER_PROFILE% || exit /b 1
out\rust\%RUNNER_PROFILE%\runner build %* || exit /b 1
