@echo off
setlocal

if "%1"=="pylib" (
    set "PYTHONPATH=out/pylib"
    set "source=pylib/anki"
    set "outdir=out\coverage\python-pylib"
    set "tests=pylib/tests"
    set "threshold=65"
) else if "%1"=="qt" (
    set "PYTHONPATH=pylib;out/pylib;out/qt"
    set "source=qt/aqt"
    set "outdir=out\coverage\python-qt"
    set "tests=qt/tests"
    set "threshold=20"
) else (
    echo Usage: %0 [pylib^|qt] [--html]
    exit /b 1
)

set "ANKI_TEST_MODE=1"
if not exist %outdir% mkdir %outdir%
out\pyenv\Scripts\python -m coverage run --source=%source% --data-file=%outdir%\.coverage -m pytest -p no:cacheprovider %tests% || exit /b 1
out\pyenv\Scripts\python -m coverage json --data-file=%outdir%\.coverage -o %outdir%\coverage-summary.json || exit /b 1
out\pyenv\Scripts\python -m coverage report --data-file=%outdir%\.coverage --fail-under=%threshold% || exit /b 1

if "%2"=="--html" (
    out\pyenv\Scripts\python -m coverage html --data-file=%outdir%\.coverage -d %outdir%\html --fail-under=%threshold% || exit /b 1
    echo Python %1 coverage report: %outdir%\html\index.html
)
