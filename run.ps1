$env:Path = "C:\msys64\usr\bin;" + [Environment]::GetEnvironmentVariable('Path','Machine') + ";" + [Environment]::GetEnvironmentVariable('Path','User')
just run
