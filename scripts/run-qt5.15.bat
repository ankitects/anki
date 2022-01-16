REM run this from the scripts folder, not from root

set PYTHONWARNINGS=default
call ..\bazel.bat run %BUILDARGS% //qt:runanki_qt515 -k -- %*
