@echo off

if "%NOCOMP%"=="1" (
    set NO_COMPRESS=1
    set CODESIGN=0
) else (
    set CODESIGN=1
    set NO_COMPRESS=0
)
cargo run --bin build_win
