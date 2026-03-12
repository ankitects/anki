@echo off
call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"

REM Adiciona as ferramentas Unix do Git ao PATH
set "PATH=%PATH%;C:\Program Files\Git\usr\bin"

REM Verifica se o rsync esta acessivel
echo Verificando rsync...
where rsync
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] rsync nao encontrado no PATH! O build vai falhar.
    exit /b 1
) else (
    echo [OK] rsync encontrado.
)

REM Executa o comando de build original
echo Iniciando build "Original de Fabrica"...
call run.bat
