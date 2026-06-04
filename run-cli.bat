@echo off
pushd "%~dp0"

set PYTHONPYCACHEPREFIX=out\pycache

@if not defined PYENV set PYENV=out\pyenv

set "BUILD_LOG=%TEMP%\anki-cli-build-%RANDOM%%RANDOM%.log"
call tools\ninja pylib > "%BUILD_LOG%" 2>&1
if errorlevel 1 (
    type "%BUILD_LOG%" 1>&2
    del "%BUILD_LOG%" > nul 2>&1
    popd
    exit /b 1
)
del "%BUILD_LOG%" > nul 2>&1

set "PYTHONPATH=pylib;out\pylib;%PYTHONPATH%"
%PYENV%\Scripts\python -m anki.cli %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
