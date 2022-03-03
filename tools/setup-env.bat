@echo off
pushd "%~dp0"\..

REM add msys/bazel to path if they're not already on it
where /q bazel || (
    set PATH=c:\msys64\usr\bin;\bazel;"%PATH%"
)

if not exist windows.bazelrc (
    rem By default, Bazel will place build files in c:\users\<user>\_bazel_<user>, and this
    rem can lead to build failures when the path names grow too long. So on Windows, the
    rem default storage location is \bazel\anki instead.
    echo startup --output_user_root=\\bazel\\anki > windows.bazelrc
)

popd
