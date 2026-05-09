@echo off
rem
rem Install our latest anki/aqt code into the launcher venv

rmdir /s /q out\wheels 2>nul
call tools\ninja wheels
set VIRTUAL_ENV=%LOCALAPPDATA%\AnkiProgramFiles\.venv
for %%f in (out\wheels\*.whl) do out\extracted\uv\uv pip install "%%f"