:: ensure wheels are built
pushd ..\..
call scripts\build || exit /b
set ROOT=%CD%
popd

:: ensure venv exists
set OUTPUT_ROOT=%ROOT%/bazel-pkg
set VENV=%OUTPUT_ROOT%/venv
if not exist %VENV% (
   mkdir %OUTPUT_ROOT%
   pushd %ROOT%
   call scripts\python -m venv %VENV% || exit /b
   popd
)

:: run the rest of the build in Python
FOR /F "tokens=*" %%g IN ('call ..\..\bazel.bat info output_base --ui_event_filters=-INFO') do (SET BAZEL_EXTERNAL=%%g/external)
call %ROOT%\scripts\cargo-env
call ..\..\bazel.bat query @pyqt515//:*
%VENV%\scripts\python build.py %ROOT% %BAZEL_EXTERNAL% || exit /b
