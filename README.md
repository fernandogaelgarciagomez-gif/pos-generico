# POS Genérico — FG Sistemas

Punto de venta de escritorio (Java 17 + JavaFX + SQLite) construido por fases con **Claude Code**.
Ubicación: `C:\dev\pos-generico`. Avance: ver `docs/00-plan-por-fases.md` (sección "Estado").

## Requisitos en tu máquina
- JDK 17 (Temurin)
- Maven 3.8+ (instalado en `C:\dev\tools\apache-maven-3.9.9`)
- JavaFX lo baja Maven, no necesitas instalarlo aparte
- `.mvn/jvm.config` hace que Maven use los certificados de Windows (necesario con Avast)

## Correr el proyecto
```
mvn clean javafx:run
```

## Pruebas
```
mvn test
```

## Generar el programa para la tienda
```
powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
```
Corre las pruebas y deja en `target\instalador\` el archivo **Punto de Venta <versión> - instalador.zip**
(versiones de 64 y 32 bits, cada una con su propio Java, + `Instalar.bat`, que elige la correcta). La de 32 bits
necesita el JDK 17 x86 en `C:\dev\tools\jdk17-x86` (sin él, se omite). En la computadora de la tienda: descomprimir y doble clic en
`Instalar.bat` (ver `instalador/LEEME.txt`). Si está instalado [WiX Toolset 3](https://wixtoolset.org/), también
genera un instalador `.exe`.

## Datos
La base de datos, respaldos, logs e imágenes viven en `%LOCALAPPDATA%\FGSistemas\POS\`
(fuera del proyecto y de OneDrive). El registro de errores está en `logs\pos0.log` (el más reciente). Las exportaciones (Excel y PDF) van a
`Documentos\Punto de Venta\Exportaciones\`.

## Contenido
| Archivo | Para qué |
|---|---|
| CLAUDE.md | Reglas principales y stack |
| docs/00-plan-por-fases.md | Orden de trabajo |
| docs/01-arquitectura.md | Capas y navegación |
| docs/02-base-de-datos.md | Tablas SQL |
| docs/03-diseno-ui.md | Diseño azul marino |
| docs/04-teclado-y-atajos.md | Uso 100% con teclado |
| docs/05-decisiones.md | Decisiones tomadas (peso/fracción, carpeta de datos, PDF, etc.) |
| docs/06-errores-humanos.md | Errores humanos previstos y cómo se previenen |
| empaquetar.ps1 | Genera el programa para instalar en la tienda |
| docs/modulos/*.md | Cada pantalla, con criterios de aceptación |
