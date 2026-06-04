@echo off
pushd "%~dp0"

set PYTHONPYCACHEPREFIX=out\pycache

@if not defined PYENV set PYENV=out\pyenv

call tools\ninja pylib 1>&2 || exit /b 1
set "PYTHONPATH=pylib;out\pylib;%PYTHONPATH%"
%PYENV%\Scripts\python -m anki.cli %* || exit /b 1
popd
