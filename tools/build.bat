@echo off
pushd "%~dp0"\..
set RELEASE=1
.\ninja wheels || exit /b 1
popd
