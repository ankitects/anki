@echo off
REM FlashAI – setup script (Windows)
REM Creates .venv, installs deps, and writes run.bat

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "VENV=%SCRIPT_DIR%.venv"

echo.
echo FlashAI Setup
echo =============

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
    echo         Make sure to tick "Add Python to PATH" during install.
    pause
    exit /b 1
)

for /f "tokens=*" %%v in ('!PYTHON! -c "import sys; print('.'.join(map(str,sys.version_info[:3])))"') do set PY_VER=%%v
echo [OK] Using Python !PY_VER!

REM ── Check tkinter ────────────────────────────────────────────────────────
!PYTHON! -c "import tkinter" >nul 2>&1
if errorlevel 1 (
    echo.
    echo [WARN] tkinter does not seem to be available.
    echo        Re-run the Python installer and ensure "tcl/tk and IDLE" is checked.
    echo.
    set /p ans="Continue anyway? [y/N] "
    if /i not "!ans!"=="y" exit /b 1
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

echo [..] Installing dependencies ...
"!PIP!" install --quiet -r "!SCRIPT_DIR!requirements.txt"

REM ── Write run.bat ─────────────────────────────────────────────────────────
(
    echo @echo off
    echo "!PYVENV!" "!SCRIPT_DIR!flashai.py" %%*
) > "!SCRIPT_DIR!run.bat"

echo.
echo =============================================
echo  Setup complete!
echo.
echo  Run the desktop app:
echo    run.bat
echo.
echo  Or activate the venv manually:
echo    .venv\Scripts\activate
echo    python flashai.py
echo =============================================
echo.
pause
