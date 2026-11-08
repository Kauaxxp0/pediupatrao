@echo off
rem Testa a API do dashboard (o sistema precisa estar rodando)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0testar-dashboard-api.ps1"
echo.
pause
