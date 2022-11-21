@echo off
REM separate build+run steps so build env doesn't leak into subprocesses
cargo build -p runner --target-dir=out/rust || exit /b 1
out\rust\debug\runner build %* || exit /b 1
