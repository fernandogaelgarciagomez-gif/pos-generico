# Publica en GitHub Releases la versión ya empaquetada con empaquetar.ps1 (decisión 85).
# Los programas instalados la encuentran solos (Configuración > Actualizaciones, y un aviso al admin una vez al día).
#
# Sube: pos-generico.jar + actualizacion.properties (actualización automática) y el instalador .zip completo.
# Requiere: gh (GitHub CLI) con sesión iniciada, y el repositorio público (si no, los programas no pueden bajarla).
#
# Uso (desde C:\dev\pos-generico, después de empaquetar.ps1):
#   powershell -ExecutionPolicy Bypass -File .\publicar.ps1
#   ... -Notas 'Qué trae esta versión'

param(
    [string]$Notas = '',
    [string]$Repo = 'fernandogaelgarciagomez-gif/pos-generico'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

[xml]$pom = Get-Content pom.xml
$version = $pom.project.version
$destino = 'target\instalador'
$act = Join-Path $destino 'actualizacion'
$zip = Join-Path $destino "Punto de Venta $version - instalador.zip"
foreach ($f in @("$act\pos-generico.jar", "$act\actualizacion.properties", $zip)) {
    if (-not (Test-Path $f)) { throw "Falta $f. Corre primero empaquetar.ps1." }
}
if (-not ((Get-Content "$act\actualizacion.properties") -contains "version=$version")) {
    throw "actualizacion.properties no es de la versión $version. Vuelve a correr empaquetar.ps1."
}

# Nombre sin espacios para el instalador (GitHub cambia los espacios por puntos).
$zipPublicado = Join-Path $destino "Punto-de-Venta-$version-instalador.zip"
Copy-Item $zip $zipPublicado -Force

$texto = "Punto de Venta $version`n`n"
if ($Notas) { $texto += "$Notas`n`n" }
$texto += "- Si ya tienes el programa instalado: Configuración > Actualizaciones > Buscar (F5) > Descargar e instalar (F6).`n"
$texto += "- Instalación nueva: baja Punto-de-Venta-$version-instalador.zip, Extraer todo y doble clic en Instalar.bat."

& gh release create "v$version" --repo $Repo --title "Punto de Venta $version" --notes $texto --latest `
    "$act\pos-generico.jar" "$act\actualizacion.properties" $zipPublicado
if ($LASTEXITCODE -ne 0) { throw 'No se pudo publicar la versión en GitHub.' }
Write-Host "Publicada v$version en https://github.com/$Repo/releases" -ForegroundColor Green
