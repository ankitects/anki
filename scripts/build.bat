@echo off

if not exist WORKSPACE (
    echo Run from project root
    exit /b 1
)

rd /s /q bazel-dist

set BUILDARGS=-k -c opt dist --color=yes --@rules_rust//worker:use_worker=False
call .\bazel build %BUILDARGS%
:: repeat on failure
IF %ERRORLEVEL% NEQ 0 call .\bazel build %BUILDARGS%

tar xvf bazel-bin\dist.tar
