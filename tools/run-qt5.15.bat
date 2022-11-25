@echo off
pushd "%~dp0\.."

set PYTHONWARNINGS=default
set PYTHONPYCACHEPREFIX=out\pycache
set ANKIDEV=1

call tools\ninja pylib/anki qt/aqt pyenv-qt5.15 || exit /b 1
.\out\pyenv-qt5.15\scripts\python tools\run.py %* || exit /b 1
popd
