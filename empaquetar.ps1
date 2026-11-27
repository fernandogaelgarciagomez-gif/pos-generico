# Empaqueta el Punto de Venta para instalarlo en la computadora de la tienda.
#
# Resultado (un solo .zip para cualquier Windows 10/11; Instalar.bat elige la versión de 64 o 32 bits):
#   target\instalador\Punto de Venta <versión>\
#       Punto de Venta\              versión de 64 bits (Punto de Venta.exe, con su propio Java)
#       Punto de Venta (32 bits)\    versión de 32 bits (Java 17 x86 + JavaFX 17.0.8 x86), si hay JDK de 32 bits
#       Instalar.bat, instalar.ps1, desinstalar.ps1, LEEME.txt, version.txt
#   target\instalador\Punto de Venta <versión> - instalador.zip   (lo que se lleva a la tienda)
#
# Las librerías nativas (JavaFX, SQLite) van sueltas en app\native y se cargan desde ahí: el programa no extrae
# nada a Temp ni a la carpeta del usuario (menos problemas con antivirus, usuarios con acentos o Temp lleno).
#
# Uso (desde C:\dev\pos-generico):
#   powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
#   powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1 -SinPruebas
#   ... -Jdk32 'C:\ruta\al\jdk-17-x86'    (por defecto busca en C:\dev\tools\jdk17-x86)

param(
    [switch]$SinPruebas,
    [string]$Jdk32 = '',
    # Actualización automática (decisión 85): novedades que verá el admin, y si esta versión cambia librerías o
    # el Java incluido (entonces el programa pide usar el instalador completo en vez de actualizarse solo).
    [string]$Notas = '',
    [switch]$RequiereInstalador,
    [string]$Repo = 'fernandogaelgarciagomez-gif/pos-generico'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

# PATH y JAVA_HOME frescos (las terminales abiertas antes de instalar Maven/JDK no los ven).
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') }
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { throw "No se encontró jpackage en $env:JAVA_HOME (se necesita el JDK 17)." }
if (-not $Jdk32) {
    $Jdk32 = Get-ChildItem 'C:\dev\tools\jdk17-x86' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\jlink.exe') } | Select-Object -First 1 -ExpandProperty FullName
}
if ($Jdk32 -and -not (Test-Path (Join-Path $Jdk32 'bin\jlink.exe'))) {
    Write-Host "No hay un JDK de 32 bits en '$Jdk32': se omite la versión de 32 bits." -ForegroundColor Yellow
    $Jdk32 = ''
}

[xml]$pom = Get-Content pom.xml
$version = $pom.project.version
$nombreJar = "$($pom.project.artifactId)-$version.jar"
# Nombre FIJO del programa en la instalación: no cambia entre versiones, así la actualización automática solo
# reemplaza este archivo y los accesos directos siguen sirviendo (decisión 85).
$jarFijo = 'pos-generico.jar'
$javafx32 = '17.0.8'   # última versión de JavaFX 17 publicada para Windows de 32 bits
$opcionesJava = @('-Dfile.encoding=UTF-8', '-Xmx768m')

# ------------------------------------------------------------------ utilidades

# Copia las DLL de la raíz de un .jar a $carpeta y las quita del .jar (así JavaFX no las extrae a la carpeta del usuario).
function Sacar-Nativas([string]$jar, [string]$carpeta) {
    New-Item -ItemType Directory -Force $carpeta | Out-Null
    $z = [IO.Compression.ZipFile]::Open((Resolve-Path $jar), 'Update')
    try {
        foreach ($e in @($z.Entries | Where-Object { $_.FullName -like '*.dll' -and $_.FullName -notlike '*/*' })) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $carpeta $e.Name), $true)
            $e.Delete()
        }
    } finally { $z.Dispose() }
}

# Copia la DLL de SQLite de la arquitectura indicada (x86_64 | x86) a $carpeta\sqlitejdbc.dll.
function Copiar-Sqlite([string]$jar, [string]$arquitectura, [string]$carpeta) {
    $z = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $jar))
    try {
        $e = $z.GetEntry("org/sqlite/native/Windows/$arquitectura/sqlitejdbc.dll")
        if (-not $e) { throw "El jar de SQLite no trae la librería para Windows $arquitectura." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $carpeta 'sqlitejdbc.dll'), $true)
    } finally { $z.Dispose() }
}

# ------------------------------------------------------------------ 1. compilar

Write-Host "1/4 Compilando y probando ($version)..." -ForegroundColor Cyan
$mvnArgs = @('clean', 'package')
if ($SinPruebas) { $mvnArgs += '-DskipTests' }
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw 'Maven falló: revisa los errores de arriba.' }
$sqliteJar = (Get-ChildItem 'target\lib\sqlite-jdbc-*.jar' | Select-Object -First 1).FullName

$destino = 'target\instalador'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
$entrega = Join-Path $destino "Punto de Venta $version"
New-Item -ItemType Directory -Force $entrega | Out-Null

# ------------------------------------------------------------------ 2. versión de 64 bits (jpackage)

Write-Host '2/4 Versión de 64 bits...' -ForegroundColor Cyan
$in64 = 'target\paquete64'
if (Test-Path $in64) { Remove-Item $in64 -Recurse -Force }
New-Item -ItemType Directory -Force "$in64\lib" | Out-Null
Copy-Item "target\$nombreJar" "$in64\$jarFijo"
Copy-Item 'target\lib\*.jar' "$in64\lib"
Get-ChildItem "$in64\lib\javafx-*-win.jar" | ForEach-Object { Sacar-Nativas $_.FullName "$in64\native" }
Copiar-Sqlite $sqliteJar 'x86_64' "$in64\native"

$jp = @(
    '--type', 'app-image', '--name', 'Punto de Venta', '--app-version', $version, '--vendor', 'FG Sistemas',
    '--description', 'Punto de venta FG Sistemas', '--input', $in64, '--main-jar', $jarFijo,
    '--main-class', 'com.fgsistemas.pos.Lanzador', '--dest', 'target\app64',
    '--icon', 'src\main\resources\img\icono.ico',
    # Igual que el predeterminado de jpackage pero SIN --strip-native-commands: el ayudante de la actualización
    # automática necesita runtime\bin\javaw.exe (decisión 85).
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files'
)
foreach ($o in $opcionesJava + @('-Djava.library.path=$APPDIR\native', '-Dorg.sqlite.lib.path=$APPDIR\native',
        '-Dorg.sqlite.lib.name=sqlitejdbc.dll')) { $jp += @('--java-options', $o) }
if (Test-Path 'target\app64') { Remove-Item 'target\app64' -Recurse -Force }
& $jpackage @jp
if ($LASTEXITCODE -ne 0) { throw 'jpackage falló.' }
Move-Item 'target\app64\Punto de Venta' $entrega

# ------------------------------------------------------------------ 3. versión de 32 bits (jlink + javaw)

if ($Jdk32) {
    Write-Host "3/4 Versión de 32 bits (JDK: $Jdk32)..." -ForegroundColor Cyan
    $fx = 'target\javafx-x86'
    if (-not (Test-Path $fx)) { New-Item -ItemType Directory -Force $fx | Out-Null }
    foreach ($m in 'base', 'controls', 'fxml', 'graphics') {
        & mvn -q dependency:copy "-Dartifact=org.openjfx:javafx-${m}:${javafx32}:jar:win-x86" "-DoutputDirectory=$fx"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo bajar javafx-$m $javafx32 win-x86." }
    }
    $app32 = Join-Path $entrega 'Punto de Venta (32 bits)'
    New-Item -ItemType Directory -Force "$app32\app\lib" | Out-Null
    Copy-Item "target\$nombreJar" "$app32\app\$jarFijo"
    Get-ChildItem 'target\lib\*.jar' | Where-Object { $_.Name -notlike 'javafx-*' } | Copy-Item -Destination "$app32\app\lib"
    Copy-Item "$fx\javafx-*-$javafx32-win-x86.jar" "$app32\app\lib"
    Get-ChildItem "$app32\app\lib\javafx-*-win-x86.jar" | ForEach-Object { Sacar-Nativas $_.FullName "$app32\app\native" }
    Copiar-Sqlite $sqliteJar 'x86' "$app32\app\native"
    Copy-Item 'src\main\resources\img\icono.ico' $app32

    # Los mismos módulos de Java que jpackage puso en la versión de 64 bits.
    $release = Get-Content (Join-Path $entrega 'Punto de Venta\runtime\release') | Where-Object { $_ -like 'MODULES=*' }
    $modulos = ($release -replace '^MODULES="?', '' -replace '"$', '').Trim() -split '\s+' -join ','
    & (Join-Path $Jdk32 'bin\jlink.exe') --add-modules $modulos --output "$app32\runtime" --strip-debug `
        --no-header-files --no-man-pages --compress=2
    if ($LASTEXITCODE -ne 0) { throw 'jlink (32 bits) falló.' }

    # Cómo se arranca (lo usan el instalador para el acceso directo y "Punto de Venta.cmd").
    $lanzador = (($opcionesJava + @('-Djava.library.path=app\native', '-Dorg.sqlite.lib.path=app\native',
            '-Dorg.sqlite.lib.name=sqlitejdbc.dll', '-cp', "`"app\$jarFijo;app\lib\*`"",
            'com.fgsistemas.pos.Lanzador')) -join ' ')
    Set-Content -Path "$app32\lanzador.txt" -Value $lanzador -Encoding ASCII
    Set-Content -Path "$app32\Punto de Venta.cmd" -Encoding ASCII -Value @(
        '@echo off', 'cd /d "%~dp0"', "start `"`" `"runtime\bin\javaw.exe`" $lanzador")
} else {
    Write-Host '3/4 Sin JDK de 32 bits (C:\dev\tools\jdk17-x86): se omite la versión de 32 bits.' -ForegroundColor Yellow
}

# ------------------------------------------------------------------ 4. paquete de entrega

Write-Host '4/4 Armando el paquete de entrega...' -ForegroundColor Cyan
Copy-Item 'instalador\Instalar.bat', 'instalador\instalar.ps1', 'instalador\desinstalar.ps1', 'instalador\LEEME.txt' $entrega
Set-Content -Path "$entrega\version.txt" -Value $version -Encoding ASCII
Compress-Archive -Path $entrega -DestinationPath "$destino\Punto de Venta $version - instalador.zip" -Force

# Archivos de la actualización automática (los sube publicar.ps1 a GitHub Releases).
$act = Join-Path $destino 'actualizacion'
if (Test-Path $act) { Remove-Item $act -Recurse -Force }
New-Item -ItemType Directory -Force $act | Out-Null
Copy-Item "target\$nombreJar" "$act\$jarFijo"
$hash = (Get-FileHash "$act\$jarFijo" -Algorithm SHA256).Hash.ToLower()
$bytes = (Get-Item "$act\$jarFijo").Length
$notasProp = $Notas.Replace('\', '\\').Replace("`r", '').Replace("`n", '\n')
$manifiesto = @(
    "version=$version",
    "jar=https://github.com/$Repo/releases/download/v$version/$jarFijo",
    "sha256=$hash",
    "bytes=$bytes",
    "requiereInstalador=$(if ($RequiereInstalador) { 'true' } else { 'false' })",
    "pagina=https://github.com/$Repo/releases/tag/v$version",
    "notas=$notasProp"
)
[IO.File]::WriteAllLines("$act\actualizacion.properties", [string[]]$manifiesto, (New-Object Text.UTF8Encoding $false))

Write-Host ''
Write-Host "Listo. Revisa $destino" -ForegroundColor Green
Get-ChildItem $destino | Format-Table Name, Length -AutoSize
Get-ChildItem $entrega | Format-Table Name -AutoSize
