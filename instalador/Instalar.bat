@echo off
chcp 65001 >nul
title Instalar Punto de Venta
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
pause
