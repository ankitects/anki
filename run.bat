@echo off
pushd "%~dp0"

set PYTHONWARNINGS=default
set PYTHONPYCACHEPREFIX=out\pycache
set ANKIDEV=1

call tools\ninja pylib qt extract:win_amd64_audio || exit /b 1
.\out\pyenv\scripts\python tools\run.py %* || exit /b 1
popd
