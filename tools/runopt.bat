@echo off
pushd "%~dp0"\..
call tools\setup-env.bat

set BUILDARGS=-c opt
call .\run.bat %*
popd