@echo off
set CARGO_TARGET_DIR=%~dp0\..\out\rust
REM separate build+run steps so build env doesn't leak into subprocesses
cargo build -p runner || exit /b 1
out\rust\debug\runner build %* || exit /b 1
