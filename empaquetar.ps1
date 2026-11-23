# Empaqueta el Punto de Venta para instalarlo en la computadora de la tienda (Fase 9).
#
# Resultado:
#   target\instalador\Punto de Venta\Punto de Venta.exe   (carpeta lista para copiar; trae su propio Java)
#   target\instalador\Punto de Venta.zip                  (la misma carpeta comprimida)
#   target\instalador\Punto de Venta-<versión>.exe        (instalador, solo si está instalado WiX Toolset 3)
#
# Uso (desde C:\dev\pos-generico):
#   powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
#   powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1 -SinPruebas

param([switch]$SinPruebas)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# PATH y JAVA_HOME frescos (las terminales abiertas antes de instalar Maven/JDK no los ven).
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') }
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { throw "No se encontró jpackage en $env:JAVA_HOME (se necesita el JDK 17)." }

# Versión del pom (jpackage solo acepta números: 0.1.0)
[xml]$pom = Get-Content pom.xml
$version = $pom.project.version
$nombreJar = "$($pom.project.artifactId)-$version.jar"

Write-Host "1/3 Compilando y probando ($version)..." -ForegroundColor Cyan
$args = @('clean', 'package')
if ($SinPruebas) { $args += '-DskipTests' }
& mvn @args
if ($LASTEXITCODE -ne 0) { throw 'Maven falló: revisa los errores de arriba.' }

Write-Host '2/3 Preparando archivos...' -ForegroundColor Cyan
$entrada = 'target\paquete'
if (Test-Path $entrada) { Remove-Item $entrada -Recurse -Force }
New-Item -ItemType Directory -Force "$entrada\lib" | Out-Null
Copy-Item "target\$nombreJar" $entrada
Copy-Item 'target\lib\*.jar' "$entrada\lib"

$destino = 'target\instalador'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }

$comunes = @(
    '--name', 'Punto de Venta',
    '--app-version', $version,
    '--vendor', 'FG Sistemas',
    '--description', 'Punto de venta FG Sistemas',
    '--input', $entrada,
    '--main-jar', $nombreJar,
    '--main-class', 'com.fgsistemas.pos.Lanzador',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--dest', $destino
)
$icono = 'src\main\resources\img\icono.ico'
if (Test-Path $icono) { $comunes += @('--icon', $icono) }

Write-Host '3/3 Generando la aplicación con jpackage...' -ForegroundColor Cyan
& $jpackage --type app-image @comunes
if ($LASTEXITCODE -ne 0) { throw 'jpackage falló.' }
Compress-Archive -Path "$destino\Punto de Venta" -DestinationPath "$destino\Punto de Venta.zip" -Force

# Instalador .exe con acceso directo y menú inicio: requiere WiX Toolset 3 (candle.exe / light.exe en el PATH).
if (Get-Command candle.exe -ErrorAction SilentlyContinue) {
    & $jpackage --type exe @comunes --win-shortcut --win-menu --win-menu-group 'FG Sistemas' --win-dir-chooser `
        --win-upgrade-uuid '6b1f6a52-3c2e-4f53-9b7e-1f0c5d2a8e41'
    if ($LASTEXITCODE -ne 0) { throw 'jpackage (instalador .exe) falló.' }
} else {
    Write-Host 'WiX Toolset no está instalado: se omitió el instalador .exe (la carpeta y el .zip sí se generaron).' -ForegroundColor Yellow
}

Write-Host ''
Write-Host "Listo. Revisa $destino" -ForegroundColor Green
Get-ChildItem $destino | Format-Table Name, Length -AutoSize
