call .\out\extracted\node\yarn %*
if errorlevel 1 exit /b %errorlevel%
call .\out\extracted\node\yarn run generate-licenses > ts/licenses.json
