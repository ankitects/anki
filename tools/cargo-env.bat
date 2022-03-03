@echo off
pushd "%~dp0"\..
call tools\setup-env.bat

FOR /F "tokens=*" %%g IN ('bazel info output_base --ui_event_filters=-INFO') do (SET BAZEL_EXTERNAL=%%g/external)
set PATH=%BAZEL_EXTERNAL%\rust_windows_x86_64\bin;%PATH%
popd
