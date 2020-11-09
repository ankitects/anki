set BAZEL=\bazel\bazel.exe --output_user_root=\bazel\anki --output_base=\bazel\anki\base 
set BUILDARGS=--config=ci

echo Building...
:: rollup may fail on the first build, so we build once without checking return code
call %BAZEL% build %BUILDARGS% ... -k

:: now build/test
echo Running tests...
call %BAZEL% test %BUILDARGS% ...
IF %ERRORLEVEL% NEQ 0 exit /B 1

:: build the wheels
call %BAZEL% build %BUILDARGS% pylib/anki:wheel qt/aqt:wheel
IF %ERRORLEVEL% NEQ 0 exit /B 1

:: install them into a new venv
echo Creating venv...
\python\python.exe -m venv venv
IF %ERRORLEVEL% NEQ 0 exit /B 1
call venv\scripts\activate

:: expand wildcards
for %%i in (bazel-bin/pylib/anki/*.whl) do set "pylib=%%~i"
for %%i in (bazel-bin/qt/aqt/*.whl) do set "qt=%%~i"
echo Installing wheels...
venv\scripts\pip install bazel-bin/pylib/anki/%pylib% bazel-bin/qt/aqt/%qt% pyqtwebengine
IF %ERRORLEVEL% NEQ 0 exit /B 1

echo Importing...
python -c "import aqt"
IF %ERRORLEVEL% NEQ 0 exit /B 1
echo Import succesful.

echo All tests pass.
