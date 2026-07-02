@echo off
set CARGO_TARGET_DIR=%~dp0..\out\rust
REM separate build+run steps so build env doesn't leak into subprocesses
cargo build -p runner --release || exit /b 1
out\rust\release\runner build %* || exit /b 1
