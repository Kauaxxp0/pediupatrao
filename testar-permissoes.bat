@echo off
rem Executa o teste de permissoes (o sistema precisa estar rodando no NetBeans)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0testar-permissoes.ps1"
echo.
pause
