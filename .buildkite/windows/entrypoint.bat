echo --- Building
set BAZEL=\bazel\bazel.exe --output_user_root=\bazel\ankici --output_base=\bazel\ankici\base 
set BUILDARGS=--config=ci

echo +++ Building and testing

if exist \bazel\node_modules (
    move \bazel\node_modules .\node_modules
)

call %BAZEL% test %BUILDARGS% ...
IF %ERRORLEVEL% NEQ 0 exit /B 1

echo --- Cleanup
move node_modules \bazel\node_modules
