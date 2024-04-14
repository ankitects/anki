@echo off
pushd "%~dp0"

set PYTHONWARNINGS=default
set PYTHONPYCACHEPREFIX=out\pycache
set ANKIDEV=1
set QTWEBENGINE_REMOTE_DEBUGGING=8080
set QTWEBENGINE_CHROMIUM_FLAGS=--remote-allow-origins=http://localhost:8080
set ANKI_API_PORT=40000
set ANKI_API_HOST=127.0.0.1

call tools\ninja pylib qt extract:win_amd64_audio || exit /b 1
.\out\pyenv\scripts\python tools\run.py %* || exit /b 1
popd
