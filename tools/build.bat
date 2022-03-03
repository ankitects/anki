@echo off
pushd "%~dp0"\..
call tools\setup-env.bat

echo --- Build wheels

set cwd=%CD%
set dist=.bazel\out\dist

bazel build -c opt wheels --color=yes || exit /b 1
if exist %dist% (
    rd /s /q %dist% || exit /b 1
)
md %dist% || exit /b 1
cd %dist%
tar xvf %cwd%\.bazel\bin\wheels.tar || exit /b 1
echo wheels are in %dist%
popd
