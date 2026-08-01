@echo off

set RELEASE=2
tools\ninja installer || exit /b 1
