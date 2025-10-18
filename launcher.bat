@echo off

.\tools\ninja.bat node_modules extract:uv ts:generated

mkdir ,\target\debug
mkdir .\target\release

copy .\.python-version .\target\debug\
copy .\qt\launcher\versions.py .\target\debug\
copy .\qt\launcher\pyproject.toml .\target\debug\
copy .\out\extracted\uv\uv.exe .\target\debug\

copy .\.python-version .\target\release\
copy .\qt\launcher\versions.py .\target\release\
copy .\qt\launcher\pyproject.toml .\target\release\
copy .\out\extracted\uv\uv.exe .\target\release\
