echo --- Building
set BAZEL=\bazel\bazel.exe --output_user_root=\bazel\ankici --output_base=\bazel\ankici\base 
set BUILDARGS=--config=ci

if exist \bazel\node_modules (
    move \bazel\node_modules ts\node_modules
)

:: rollup may fail on the first build, so we build once without checking return code
call %BAZEL% build %BUILDARGS% ... -k

:: now build/test
echo +++ Running tests
call %BAZEL% test %BUILDARGS% ...
IF %ERRORLEVEL% NEQ 0 exit /B 1

:: build the wheels
@REM call %BAZEL% build %BUILDARGS% pylib/anki:wheel qt/aqt:wheel
@REM IF %ERRORLEVEL% NEQ 0 exit /B 1

@REM :: install them into a new venv
@REM echo Creating venv...
@REM \python\python.exe -m venv venv
@REM IF %ERRORLEVEL% NEQ 0 exit /B 1
@REM call venv\scripts\activate

@REM :: expand wildcards
@REM for %%i in (bazel-bin/pylib/anki/*.whl) do set "pylib=%%~i"
@REM for %%i in (bazel-bin/qt/aqt/*.whl) do set "qt=%%~i"
@REM echo Installing wheels...
@REM venv\scripts\pip install bazel-bin/pylib/anki/%pylib% bazel-bin/qt/aqt/%qt%
@REM IF %ERRORLEVEL% NEQ 0 exit /B 1

@REM echo Importing...
@REM python -c "import aqt"
@REM IF %ERRORLEVEL% NEQ 0 exit /B 1
@REM echo Import succesful.

echo --- Cleanup
move ts\node_modules \bazel\node_modules
