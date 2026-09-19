@echo off
pushd "%~dp0"\..
set RELEASE=2
just wheels || exit /b 1
echo wheels are in out/wheels
popd
