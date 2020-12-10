@echo off

if not exist WORKSPACE (
    echo Run from project root
    exit /b 1
)

rd /s /q bazel-dist

call .\bazel build -k -c opt dist --color=yes
:: repeat on failure
IF %ERRORLEVEL% NEQ 0 call .\bazel build -k -c opt dist --color=yes

tar xvf bazel-bin\dist.tar
