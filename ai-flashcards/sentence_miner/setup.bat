@echo off
REM Sentence Miner – setup script (Windows)
REM Creates .venv, installs deps, and writes run.bat (GUI) and mine.bat (CLI).

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "VENV=%SCRIPT_DIR%.venv"

echo.
echo Sentence Miner Setup
echo ====================

REM ── Locate Python 3.9+ ───────────────────────────────────────────────────
set "PYTHON="
for %%c in (python python3 py) do (
    if "!PYTHON!"=="" (
        %%c --version >nul 2>&1 && set "PYTHON=%%c"
    )
)

if "!PYTHON!"=="" (
    echo.
    echo [ERROR] Python not found in PATH.
    echo         Download it from https://www.python.org/downloads/
    echo         Tick "Add Python to PATH" during install.
    pause
    exit /b 1
)

for /f "tokens=*" %%v in ('!PYTHON! -c "import sys; print('.'.join(map(str,sys.version_info[:3])))"') do set PY_VER=%%v
echo [OK] Using Python !PY_VER!

REM ── Tkinter check ────────────────────────────────────────────────────────
!PYTHON! -c "import tkinter" >nul 2>&1
if errorlevel 1 (
    echo.
    echo [WARN] tkinter not available — the GUI (run.bat) will not work.
    echo        Re-run the Python installer and tick "tcl/tk and IDLE".
    echo        The CLI (mine.bat) will still work without tkinter.
    echo.
    set /p ans="Continue anyway? [y/N] "
    if /i not "!ans!"=="y" exit /b 1
)

REM ── Check external tools ─────────────────────────────────────────────────
echo.
where ffmpeg >nul 2>&1 && (
    echo [OK] ffmpeg found
) || (
    echo [WARN] ffmpeg not found in PATH ^(required at runtime^)
    echo        Install: https://ffmpeg.org/download.html
    echo        or: winget install ffmpeg
)

where yt-dlp >nul 2>&1 && (
    echo [OK] yt-dlp found
) || (
    echo [WARN] yt-dlp not found in PATH ^(required at runtime^)
    echo        Install: pip install yt-dlp
    echo        or: winget install yt-dlp
)

REM ── Create / reuse venv ───────────────────────────────────────────────────
if exist "!VENV!\Scripts\activate.bat" (
    echo [OK] Reusing existing .venv
) else (
    echo [..] Creating virtual environment at .venv ...
    !PYTHON! -m venv "!VENV!"
    if errorlevel 1 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

set "PIP=!VENV!\Scripts\pip.exe"
set "PYVENV=!VENV!\Scripts\python.exe"

echo [..] Upgrading pip ...
"!PIP!" install --quiet --upgrade pip

echo [..] Installing Python dependencies ...
"!PIP!" install --quiet -r "!SCRIPT_DIR!requirements_miner.txt"
if errorlevel 1 (
    echo [ERROR] pip install failed.
    pause
    exit /b 1
)

REM ── Write run.bat (GUI) ───────────────────────────────────────────────────
(
    echo @echo off
    echo "!PYVENV!" "!SCRIPT_DIR!gui.py" %%*
) > "!SCRIPT_DIR!run.bat"

REM ── Write mine.bat (CLI) ──────────────────────────────────────────────────
(
    echo @echo off
    echo REM Sentence Miner CLI -- pass a YouTube URL and options
    echo REM Example: mine.bat "https://youtu.be/..." --deck Spanish --language es --limit 20
    echo "!PYVENV!" "!SCRIPT_DIR!main.py" %%*
) > "!SCRIPT_DIR!mine.bat"

echo.
echo =============================================
echo  Setup complete!
echo.
echo  Launch the GUI:
echo    run.bat
echo.
echo  Or use the CLI:
echo    mine.bat "https://youtu.be/VIDEO" --deck Spanish --language es
echo.
echo  Activate the venv manually:
echo    .venv\Scripts\activate
echo    python gui.py
echo =============================================
echo.
pause
