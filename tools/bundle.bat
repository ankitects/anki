@echo off
pushd "%~dp0"\..
call tools\setup-env.bat

:: ensure wheels are built and set up Rust env
call tools\build || exit /b 1
call tools\cargo-env
set ROOT=%CD%

:: ensure venv exists
echo --- Setup venv
set OUTPUT_ROOT=%ROOT%\.bazel\out\build
set VENV=%OUTPUT_ROOT%\venv-AMD64
if not exist %VENV% (
   mkdir %OUTPUT_ROOT%
   call tools\python -m venv %VENV% || exit /b
)

:: pyoxidizer requires python.org for build
set PATH=\python39;%PATH%

:: run the rest of the build in Python
echo --- Fetching extra deps
FOR /F "tokens=*" %%g IN ('bazel info output_base --ui_event_filters=-INFO') do (SET BAZEL_EXTERNAL=%%g/external)
bazel query @pyqt515//:* > nul
bazel query @pyoxidizer//:* > nul
bazel query @audio_win_amd64//:* > nul

echo --- Build bundle
pushd qt\bundle
%VENV%\scripts\python build.py %ROOT% %BAZEL_EXTERNAL% || exit /b
popd
popd