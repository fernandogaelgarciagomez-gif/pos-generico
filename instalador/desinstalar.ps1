# Desinstala el Punto de Venta: quita el programa, sus accesos directos y su registro en Windows.
# Los DATOS (ventas, inventario, respaldos) en %LOCALAPPDATA%\FGSistemas\POS NO se borran.

param([switch]$SinPreguntar)

$ErrorActionPreference = 'Stop'
$nombre = 'Punto de Venta'
$destino = $PSScriptRoot
$datos = Join-Path $env:LOCALAPPDATA 'FGSistemas\POS'

Write-Host ''
Write-Host "  Desinstalar el $nombre - FG Sistemas" -ForegroundColor White
Write-Host '  ------------------------------------------------------------'
Write-Host "  Se quitará el programa de $destino."
Write-Host "  Tus datos y respaldos se QUEDAN en $datos." -ForegroundColor Yellow
if (-not $SinPreguntar) {
    $r = Read-Host '  ¿Desinstalar? Escribe SI y presiona Enter'
    if ($r.Trim().ToUpper() -ne 'SI') { Write-Host '  No se desinstaló nada.'; Read-Host '  Presiona Enter para cerrar'; exit 0 }
}

if (Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path -like "$destino\*" }) {
    Write-Host '  El Punto de Venta está abierto: ciérralo y vuelve a desinstalar.' -ForegroundColor Red
    Read-Host '  Presiona Enter para cerrar'
    exit 1
}

$accesos = @(
    (Join-Path ([Environment]::GetFolderPath('Desktop')) "$nombre.lnk"),
    (Join-Path ([Environment]::GetFolderPath('Programs')) "FG Sistemas\$nombre.lnk")
)
foreach ($a in $accesos) { Remove-Item $a -Force -ErrorAction SilentlyContinue }
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'FG Sistemas'
if ((Test-Path $menu) -and -not (Get-ChildItem $menu)) { Remove-Item $menu -Force }
Remove-Item 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PuntoDeVentaFGSistemas' -Recurse -Force -ErrorAction SilentlyContinue

# El script vive dentro de la carpeta que se borra: se borra desde un proceso aparte, ya que este termine.
Start-Process -FilePath 'cmd.exe' -ArgumentList "/c timeout /t 2 /nobreak >nul & rmdir /s /q `"$destino`"" -WindowStyle Hidden

Write-Host ''
Write-Host "  Listo: $nombre desinstalado. Los datos siguen en $datos" -ForegroundColor Green
if (-not $SinPreguntar) { Read-Host '  Presiona Enter para cerrar' }
