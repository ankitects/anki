@echo off
pushd "%~dp0"\..
if exist out\wheels rmdir /s /q out\wheels
set RELEASE=2
tools\ninja wheels || exit /b 1
echo wheels are in out/wheels
popd
