set PYTHONWARNINGS=default
call .\bazel.bat run %BUILDARGS% //qt:runanki -k -- %*
