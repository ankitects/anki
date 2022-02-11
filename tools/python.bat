@echo off
pushd "%~dp0"\..
call tools\setup-env.bat

bazel run python --ui_event_filters=-INFO -- %*
popd
