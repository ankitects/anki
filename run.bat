@echo off
pushd "%~dp0"
call tools\setup-env.bat

set PYTHONWARNINGS=default
bazel run %BUILDARGS% //qt:runanki -k -- %* || exit /b 1
popd
