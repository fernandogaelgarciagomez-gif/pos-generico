# Instala (o actualiza) el Punto de Venta en esta computadora. No necesita permisos de administrador.
#
# Uso normal: doble clic en "Instalar.bat" (que está junto a este archivo).
# - Elige sola la versión de 64 o de 32 bits según el Windows.
# - Instala en %LOCALAPPDATA%\Programs\Punto de Venta, crea accesos directos en el Escritorio y en el menú Inicio
#   y lo registra en "Agregar o quitar programas".
# - Al final abre el programa y revisa que arranque.
# Los datos (ventas, inventario, respaldos) viven aparte, en %LOCALAPPDATA%\FGSistemas\POS, y NUNCA se tocan al
# instalar, actualizar o desinstalar. Queda un registro en %LOCALAPPDATA%\FGSistemas\POS\logs\instalacion.log.

param(
    [string]$Destino = (Join-Path $env:LOCALAPPDATA 'Programs\Punto de Venta'),
    [switch]$SinAccesos,    # pruebas: no crear accesos directos ni registrar en Windows
    [switch]$SinAbrir,      # no abrir el programa al terminar
    [switch]$Forzar32       # pruebas: instalar la versión de 32 bits aunque Windows sea de 64
)

$ErrorActionPreference = 'Stop'
$nombre = 'Punto de Venta'
$datos = Join-Path $env:LOCALAPPDATA 'FGSistemas\POS'
$version = '0.0.0'
$archivoVersion = Join-Path $PSScriptRoot 'version.txt'
if (Test-Path $archivoVersion) { $version = (Get-Content $archivoVersion -Raw).Trim() }

try {
    New-Item -ItemType Directory -Force (Join-Path $datos 'logs') | Out-Null
    Start-Transcript -Path (Join-Path $datos 'logs\instalacion.log') -Append | Out-Null
} catch { }

function Paso($texto) { Write-Host "  > $texto" -ForegroundColor Cyan }
function Aviso($texto) { Write-Host "  ! $texto" -ForegroundColor Yellow }
function Fallo($texto) {
    Write-Host ''
    Write-Host "  X $texto" -ForegroundColor Red
    Write-Host ''
    Write-Host "  Si necesitas ayuda, manda a soporte el archivo $datos\logs\instalacion.log"
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}

Write-Host ''
Write-Host "  Instalador del $nombre $version - FG Sistemas" -ForegroundColor White
Write-Host '  ------------------------------------------------------------'

# ------------------------------------------------------------------ 1. Requisitos
Paso 'Revisando la computadora...'
$so = Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue
if ($so) { Write-Host "    $($so.Caption) $($so.OSArchitecture), versión $($so.Version)" }
if ([Environment]::OSVersion.Version.Major -lt 10) {
    Fallo 'Se necesita Windows 10 u 11 (esta computadora tiene una versión anterior de Windows).'
}
$es64 = [Environment]::Is64BitOperatingSystem -and -not $Forzar32
$origen = if ($es64) { Join-Path $PSScriptRoot $nombre } else { Join-Path $PSScriptRoot "$nombre (32 bits)" }
if (-not (Test-Path $origen)) {
    if (-not (Test-Path (Join-Path $PSScriptRoot $nombre))) {
        Fallo "No se encontró la carpeta '$nombre' junto al instalador. Descomprime TODO el .zip (clic derecho > Extraer todo) y vuelve a intentar."
    }
    Fallo 'Este Windows es de 32 bits y este paquete no trae la versión de 32 bits. Pide a FG Sistemas el paquete completo.'
}
Write-Host "    Se instalará la versión de $(if ($es64) { '64' } else { '32' }) bits."

$unidad = Get-PSDrive -Name ($env:LOCALAPPDATA.Substring(0, 1))
if ($unidad.Free -lt 600MB) {
    Fallo ('Falta espacio en el disco: se necesitan al menos 600 MB libres (hay {0:N0} MB).' -f ($unidad.Free / 1MB))
}
if ($so -and $so.TotalVisibleMemorySize -lt 1900000) {
    Aviso ('La computadora tiene {0:N1} GB de memoria. El programa funciona, pero puede ir lento (se recomiendan 4 GB).' -f ($so.TotalVisibleMemorySize / 1MB))
}
try {
    Add-Type -AssemblyName System.Windows.Forms
    $pantalla = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    Write-Host "    Pantalla: $($pantalla.Width) x $($pantalla.Height)"
    if ($pantalla.Width -lt 1024 -or $pantalla.Height -lt 700) {
        Aviso 'La pantalla es más chica de 1024 x 700: algunas partes del programa podrían no verse completas.'
    }
} catch { }

# ------------------------------------------------------------------ 2. El programa no debe estar abierto
$abierto = { Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path -like "$Destino\*" } }
$intentos = 0
while (& $abierto) {
    $intentos++
    if ($intentos -gt 3) { Fallo 'El Punto de Venta sigue abierto. Ciérralo y vuelve a ejecutar el instalador.' }
    Write-Host ''
    Aviso 'El Punto de Venta está abierto. Termina la venta que estés haciendo y ciérralo.'
    Read-Host '    Presiona Enter cuando lo hayas cerrado'
}

# ------------------------------------------------------------------ 3. Copiar
# Si ya estaba instalado se guarda la versión anterior hasta terminar; si algo falla, se regresa a ella.
$anterior = "$Destino.anterior"
$actualizacion = Test-Path $Destino
if ($actualizacion) { Paso 'Actualizando la instalación anterior...' } else { Paso "Copiando el programa a $Destino ..." }
try {
    if (Test-Path $anterior) { Remove-Item $anterior -Recurse -Force }
    if ($actualizacion) { Rename-Item $Destino $anterior }
    New-Item -ItemType Directory -Force (Split-Path $Destino) | Out-Null
    Copy-Item $origen $Destino -Recurse -Force
    Copy-Item (Join-Path $PSScriptRoot 'desinstalar.ps1') $Destino -Force
    Set-Content -Path (Join-Path $Destino 'version.txt') -Value $version -Encoding ASCII
    # Archivos bajados de internet / WhatsApp vienen "bloqueados" por Windows: se desbloquean.
    Get-ChildItem $Destino -Recurse -File | Unblock-File -ErrorAction SilentlyContinue
    if (Test-Path $anterior) { Remove-Item $anterior -Recurse -Force -ErrorAction SilentlyContinue }
} catch {
    $error1 = $_.Exception.Message
    if (Test-Path $anterior) {
        if (Test-Path $Destino) { Remove-Item $Destino -Recurse -Force -ErrorAction SilentlyContinue }
        Rename-Item $anterior $Destino -ErrorAction SilentlyContinue
        Fallo "No se pudo copiar el programa ($error1). Se dejó la versión anterior."
    }
    Fallo "No se pudo copiar el programa: $error1. Si hay antivirus, permite la carpeta '$Destino' y vuelve a intentar."
}

# Cómo se arranca: la versión de 64 bits trae Punto de Venta.exe; la de 32 bits, javaw + lanzador.txt.
$exe = Join-Path $Destino "$nombre.exe"
if (Test-Path $exe) {
    $programa = $exe; $argumentos = ''; $icono = "$exe,0"
} else {
    $programa = Join-Path $Destino 'runtime\bin\javaw.exe'
    $argumentos = (Get-Content (Join-Path $Destino 'lanzador.txt') -Raw).Trim()
    $icono = (Join-Path $Destino 'icono.ico') + ',0'
    if (-not (Test-Path $programa)) { Fallo 'La instalación quedó incompleta (falta runtime\bin\javaw.exe). Vuelve a descomprimir el .zip.' }
}

# ------------------------------------------------------------------ 4. Accesos directos y registro en Windows
if (-not $SinAccesos) {
    Paso 'Creando accesos directos (Escritorio y menú Inicio)...'
    try {
        $shell = New-Object -ComObject WScript.Shell
        $menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'FG Sistemas'
        New-Item -ItemType Directory -Force $menu | Out-Null
        foreach ($lnk in @((Join-Path ([Environment]::GetFolderPath('Desktop')) "$nombre.lnk"), (Join-Path $menu "$nombre.lnk"))) {
            $a = $shell.CreateShortcut($lnk)
            $a.TargetPath = $programa
            $a.Arguments = $argumentos
            $a.WorkingDirectory = $Destino
            $a.IconLocation = $icono
            $a.Description = 'Punto de Venta - FG Sistemas'
            $a.Save()
        }
    } catch {
        Aviso "No se pudieron crear los accesos directos ($($_.Exception.Message)). Se puede abrir desde $Destino."
    }

    Paso 'Registrando en "Agregar o quitar programas"...'
    try {
        $clave = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PuntoDeVentaFGSistemas'
        New-Item -Path $clave -Force | Out-Null
        $desinstalar = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$Destino\desinstalar.ps1`""
        $tamanoKb = [int]((Get-ChildItem $Destino -Recurse -File | Measure-Object Length -Sum).Sum / 1KB)
        $valores = [ordered]@{
            DisplayName = $nombre; DisplayVersion = $version; Publisher = 'FG Sistemas'
            DisplayIcon = $icono; InstallLocation = $Destino; UninstallString = $desinstalar
            NoModify = 1; NoRepair = 1; EstimatedSize = $tamanoKb
        }
        foreach ($k in $valores.Keys) {
            $tipo = if ($valores[$k] -is [int]) { 'DWord' } else { 'String' }
            New-ItemProperty -Path $clave -Name $k -Value $valores[$k] -PropertyType $tipo -Force | Out-Null
        }
    } catch {
        Aviso "No se pudo registrar en Windows ($($_.Exception.Message)). El programa funciona igual."
    }
}

# ------------------------------------------------------------------ 5. Abrir y revisar que arranque
Write-Host ''
Write-Host "  Listo: $nombre $version instalado." -ForegroundColor Green
Write-Host "  Programa: $Destino"
if (Test-Path (Join-Path $datos 'pos.db')) {
    Write-Host "  Datos:    $datos  (se conservaron las ventas e inventario que ya había)"
} else {
    Write-Host "  Datos:    $datos  (al abrirlo por primera vez pide crear el administrador)"
}

if (-not $SinAbrir) {
    Write-Host ''
    Paso 'Abriendo el Punto de Venta y revisando que arranque (unos segundos)...'
    $inicio = @{ FilePath = $programa; WorkingDirectory = $Destino; PassThru = $true }
    if ($argumentos) { $inicio.ArgumentList = $argumentos }
    $p = Start-Process @inicio
    $vivo = $true
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        if ($p.HasExited) { $vivo = $false; break }
    }
    $yaAbierto = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path -like "$Destino\*" }
    if ($vivo -or $yaAbierto) {
        Write-Host '  El Punto de Venta está abierto y funcionando.' -ForegroundColor Green
    } else {
        Write-Host ''
        Aviso "El programa se cerró al arrancar (código $($p.ExitCode))."
        Aviso '  1. Ábrelo otra vez con el icono del Escritorio.'
        Aviso '  2. Si hay antivirus, agrega como excepción la carpeta:'
        Aviso "     $Destino"
        Aviso "  3. Si sigue igual, manda a soporte los archivos de $datos\logs"
    }
}
Write-Host ''
try { Stop-Transcript | Out-Null } catch { }
