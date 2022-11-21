set PATH=c:\cargo\bin;%PATH%

echo +++ Building and testing

if exist \buildkite\state\out (
    move \buildkite\state\out .
)
if exist \buildkite\state\node_modules (
    move \buildkite\state\node_modules .
)

call tools\ninja build pylib/anki qt/aqt check || exit /b 1

echo --- Cleanup
move out \buildkite\state\
move node_modules \buildkite\state\
