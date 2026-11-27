# Decisiones tomadas (2026-11-21)

Este archivo **manda** sobre el resto de los docs si hay contradicción.

## Acordadas con el cliente
1. **Venta por peso y por fracción.** Cada producto tiene `tipo_venta`:
   `PIEZA` (enteros), `PESO` (kg/g, 3 decimales) o `FRACCION` (m/l/cm, 3 decimales).
   Todas las cantidades y el stock se guardan en **milésimas** (`long`). Se puede vender
   por cantidad (`0.250*código`) o por importe (`$50*código`). Ver `14-vender.md`.
2. **Carpeta de datos** fuera del proyecto y de OneDrive: `%LOCALAPPDATA%\FGSistemas\POS\`.
   El proyecto vive en `C:\dev\pos-generico`.
3. **PDF con OpenPDF** (`com.github.librepdf:openpdf`): corte de caja y estado de cuenta de fiados.
4. **`Ctrl+N` como alternativa a `Insert`** en todas las pantallas.

## Resueltas por el desarrollador
5. **Columnas nuevas en `negocio`**: `permitir_stock_negativo`, `metodo_tarjeta`,
   `metodo_transferencia`, `metodo_fiado`, `descuento_maximo_pct`.
6. **`venta_pagos.referencia`** para tarjeta/transferencia.
7. **Permisos `DESCUENTOS` y `RETIROS_CAJA`** agregados al enum (estaban en el módulo 21).
8. **`+` / `−` en Vender** solo actúan si el campo de código está vacío (un código puede tener guion).
   `*` siempre se escribe en el campo (es parte de la sintaxis `cant*código`).
9. **Atajos de una letra** solo funcionan con el foco fuera de campos de texto.
10. **Ganancia estimada** = Σ (precio − costo) × cantidad **− descuentos** de la venta.
11. **Pruebas JUnit 5** para: cuadre de corte, saldo de fiados = Σ fiado − Σ abonos,
    cálculos de `Cantidad`/`Dinero`, y rollback de venta.
12. **Redondeo**: importe por renglón con `HALF_UP` a centavos; el total es la suma de renglones
    menos el descuento. Nunca `double`.
13. **Stock mínimo y "Lista de compras"** también en milésimas; la cantidad sugerida se muestra
    con la unidad del producto.
14. **Herramientas instaladas**: JDK Temurin 17, Maven 3.9.9 (`C:\dev\tools\apache-maven-3.9.9`), Git.

## Agregadas después de la Fase 3 (cliente)
15. **Botones visibles para toda acción**, además de los atajos (barra inferior de acciones).
16. **Contador de dinero vendido en el menú principal**: total y número de ventas del turno actual;
    el admin ve además el total de hoy (todos los turnos).

## Fase 4
17. **Exportaciones a Excel** se guardan en `Documentos\Punto de Venta\Exportaciones\` con fecha y hora
    en el nombre, y se ofrece abrir el archivo. Sin diálogo de "Guardar como".
18. **Inventario carga los productos en memoria** y filtra al escribir (instantáneo con miles de productos).
19. **Stock inicial** de un producto nuevo se registra en kardex como AJUSTE "Stock inicial".
    Editar un producto **no** cambia el stock: para eso está el ajuste (con motivo).
20. **Entrada de mercancía**: captura en línea (código → cantidad → costo) en vez de editar celdas.
    Si el mismo producto se captura dos veces, se suma al renglón existente.
21. **Al cajero el servicio le entrega los productos sin costo** (no solo se oculta la columna).

## Después de la Fase 4 (cliente)
22. **"Cambiar mi contraseña" (Ctrl+P) solo en la pantalla Usuarios**, no en la barra superior.
    Consecuencia: el cajero no cambia su propia contraseña; se la restablece el admin.
23. **"Kardex" se muestra como "Movimientos (kardex)"** con una explicación en el diálogo.

## Fase 5 (Vender)
24. **Vender con mosaicos**: a la izquierda buscador + categorías (chips) + mosaicos grandes por producto
    con sus **iniciales** sobre un color (no logos); a la derecha el carrito, totales y botón Cobrar.
    Excepción a "no inventar colores": los mosaicos usan una paleta fija de 8 colores (`.mosaico-color-N`),
    asignada por el nombre del producto, para distinguirlos de un vistazo.
25. Los mosaicos muestran como máximo 60 productos; escribir en el buscador filtra (nombre/código).
26. Enter en el buscador: código exacto → agrega; si el filtro deja un solo producto → lo agrega;
    si no → abre la búsqueda (F2) con lo escrito.
27. **Pago mixto**: la parte en efectivo se registra exacta (sin cambio).
28. **Autorización de admin**: diálogo usuario + contraseña; el servicio recibe un objeto `Autorizacion`
    y valida que sea de un admin activo; queda en bitácora y en `ventas.autorizada_por`.
29. Clientes en esta fase: solo buscar / alta rápida (F4) para poder vender a fiado. La pantalla
    completa de fiados es la Fase 6.
30. **Muchas categorías**: los chips de Vender ocupan como máximo ~2 filas; el resto se ve con scroll
    (no empujan los mosaicos). Nombres muy largos se cortan con "…" y el completo sale en el tooltip.

## Fase 6 (Fiados)
31. **Saldo a favor** (queda al cancelar una venta fiada que ya tenía abonos): se descuenta solo en la
    siguiente venta **a fiado** del cliente (el saldo es un acumulado). No se puede abonar ni desactivar
    al cliente mientras tenga saldo a favor. Devolverlo en efectivo será un retiro de caja (Fase 7).
    En pantalla se muestra como "A favor $X" (en verde).
32. **Ventas canceladas en el estado de cuenta**: aparecen tachadas y no mueven el saldo acumulado.
    La verificación del saldo (Σ parte fiada de ventas completadas − Σ abonos) también las excluye.
33. **Pago mixto**: el cargo en el estado de cuenta es solo la parte fiada, no el total de la venta.
34. **Desactivar cliente** solo con saldo exactamente $0 (ni deuda ni saldo a favor). Se reactiva con
    `Supr` activando "Mostrar inactivos" (solo admin).
35. **Métodos de abono**: efectivo siempre; tarjeta y transferencia solo si están habilitados en el negocio.
36. **Foco inicial en la tabla** de Fiados para que `A` / `H` funcionen de inmediato (si la lista está
    vacía, en el buscador). `Enter` en la tabla: admin edita; el cajero (que no edita) abre el historial.
37. **Exportar estado de cuenta**: `F7` a PDF (para WhatsApp) y `Shift+F7` a Excel, en la carpeta de
    exportaciones. Desde el historial, `Enter` en una venta muestra sus productos (solo lectura: las
    cancelaciones se hacen desde Vender).
38. **Notas del cliente** en un campo de una línea (Enter avanza/guarda como en todo formulario).
39. Un cliente nuevo creado desde Fiados desactiva el filtro "Solo con adeudo" para que se vea en la lista.

## Después de la Fase 6 (cliente)
40. **Entrada de mercancía: buscar por nombre en el mismo campo del código.** Enter: código exacto → lo carga;
    si lo escrito coincide con un solo producto (código o nombre) → lo carga; con varios → abre la búsqueda
    (F2) con lo escrito; sin coincidencias → ofrece crear el producto (solo si parece un código válido).

## Fase 7 (Resumen y cortes)
41. **Una sola fuente de cifras**: el Resumen, el Corte y Cortes pasados calculan con el mismo código
    (`ResumenDao`), así los números siempre coinciden. Ventas en efectivo = `venta_pagos.monto` (sin cambio);
    las canceladas no suman en nada (solo se reportan aparte, número y monto).
42. **Ganancia** = Σ (importe − costo × cantidad, redondeado por renglón) − descuentos. La ve quien ve costos
    (admin); al cajero el servicio se la entrega vacía.
43. **"Todo el día"** (admin) suma todos los turnos de hoy. No muestra "efectivo que debería haber ahora"
    (cada turno ya hizo su corte); eso se ve en "Turno actual".
44. **Top 10** ordenado por importe (los productos se venden en unidades distintas: pza, kg, m).
45. **Corte**: lo hace quien está en el turno (admin o cajero). La vista previa no guarda nada; al cerrar se
    recalculan las cifras con los datos finales. Diferencia ≠ 0 → nota obligatoria. Contado por total o por
    denominación (`D`); el desglose no se guarda, solo el total.
46. **Corte de turno ajeno** (desde la pantalla de turno): permiso nuevo `CORTE_TURNO_AJENO` (admin); el cajero
    necesita autorización de admin. Al terminar pide el fondo del turno nuevo.
47. **Después del corte propio** se cierra sesión (el siguiente login pide fondo nuevo).
48. **Exportar el corte**: `F7` PDF y `Shift+F7` Excel al terminar el corte; en el detalle de Cortes pasados
    `F7` Excel (como pide el módulo 19, con hoja de ventas del turno) y `Shift+F7` PDF.
49. **Cortes pasados**: el filtro "Usuario" es quien abrió el turno; abre en la última semana; `H` / `S` / `M`
    hoy, esta semana (desde el lunes) y este mes. La diferencia sale roja si faltó y verde si sobró o cuadró.

## Después de la Fase 7 (cliente)
50. **En el corte, en grande arriba**: "Debe haber en caja (con fondo)" y "Total con todo y fondo"
    = lo que debe haber en caja + tarjeta + transferencia (ventas y abonos). El fiado no cuenta porque no se
    cobró. También sale arriba en el PDF y como renglón en el desglose/Excel.
51. **Pantalla "Gastos e ingresos"** (menú, `F10` o `0`; quien vende): usa los mismos movimientos de caja que
    `F8` en Vender (gasto = retiro, ingreso = entrada) y entran al corte. Categorías rápidas (teclas 1–9 con el
    foco en la categoría) + detalle; el detalle es obligatorio en "Otro". Los gastos del cajero piden
    autorización de admin (igual que los retiros). No se borran: un error se corrige con el movimiento contrario.
    Especificación: `docs/modulos/25-gastos-e-ingresos.md`.

## Fase 8 (Configuración y respaldos)
52. **Un solo "Guardar" (`Ctrl+S`)** en la barra inferior guarda todas las pestañas de Configuración a la vez
    (en vez de un botón por pestaña). El logo, las categorías y los respaldos se aplican al momento.
    `Esc` con cambios sin guardar pide confirmación. Cada guardado deja en bitácora qué cambió.
53. **Categorías**: se crean, se renombran y se **quitan solo si ningún producto las usa** (en vez de
    "desactivar", que pediría una columna nueva y filtrarlas en Vender e Inventario).
54. **Logo**: se copia a `img/logo.png` en la carpeta de datos (PNG o JPG, máximo 5 MB) y se ve en el login.
55. **Respaldos** con nombre `pos_AAAA-MM-DD_HHmm[_motivo].db` (`_corte` después de un corte,
    `_antes-de-restaurar` antes de restaurar). La limpieza por días **nunca borra el respaldo más reciente**
    y no toca archivos con otro nombre. Si el respaldo automático falla, el admin ve el aviso al entrar al menú.
56. **Restaurar** revisa antes que el archivo sea una BD íntegra del sistema (integridad, tablas, versión no
    más nueva, con un admin activo); la palabra `RESTAURAR` se acepta en mayúsculas o minúsculas. También se
    puede restaurar desde un archivo (`Ctrl+O`, p. ej. una USB). Al terminar se cierra la sesión.
57. **Exportar todo a Excel** usa el mes en curso para ventas y cortes; productos y clientes, todos
    (incluidos inactivos).
58. Los servicios ubican las carpetas `respaldos\` e `img\` junto a la BD abierta (en la app es
    `%LOCALAPPDATA%\FGSistemas\POS\`); así las pruebas nunca tocan los datos reales.

## Fase 9 (Pulido y errores humanos)
59. **Devolución parcial** desde `F11` (`D`, o `Shift+D` por folio para ventas de turnos ya cortados). El reembolso
    es en **efectivo** y queda como retiro de caja del turno actual; necesita autorización de admin si es cajero.
60. Reembolso **proporcional** a lo cobrado del producto y al descuento de la venta (HALF_UP); la suma de las
    devoluciones nunca pasa del total cobrado.
61. Ventas con parte **a fiado** no se devuelven por partes (se cancelan completas). Una venta con devoluciones
    ya no se puede cancelar. Kardex con tipo nuevo `DEVOLUCION` (migración V2, que reconstruye
    `movimientos_inventario` para ampliar el CHECK).
62. La **ganancia** del Resumen y del corte descuenta lo devuelto (importe − costo de lo devuelto).
63. **Topes de captura**: 999,999 unidades y $999,999.99 por campo. Un código de barras escaneado en un campo
    de cantidad o de dinero se rechaza.
64. **Avisos con confirmación** (foco en No) para cifras sospechosas: cantidades grandes, cambio de más de $1,000,
    precio 5× distinto o bajo el costo, costo 3× distinto o sobre el precio, fondo 5× el anterior, diferencia de
    corte grande (sugiere recontar sin revelar la cifra), factura repetida, producto con nombre repetido.
    Lista completa en `docs/06-errores-humanos.md`.
65. **No se saca de la caja más efectivo del que hay**: retiros, gastos, pagos de mercancía y reembolsos.
66. **Registro de errores** en `logs\pos0.log` … `pos4.log` (2 MB c/u, `pos0.log` es el más reciente). Los errores
    inesperados muestran un mensaje en español y el detalle va al log. Los errores de BD se traducen
    (ocupada, disco lleno, sin permisos, dañada).
67. **Una sola instancia** (candado `pos.lock`); avisos de Bloq Mayús en el login, de reloj de Windows atrasado
    al iniciar sesión, y al cerrar la ventana con una venta/entrada/configuración sin guardar.
68. **Empaquetado** con `empaquetar.ps1` (jpackage, trae su propio Java): carpeta + `.zip`; instalador `.exe` solo si
    está WiX Toolset 3. Antes de migrar el esquema de una BD con datos se copia a
    `respaldos\pos_…_antes-de-actualizar-vN.db`.
69. **Importar productos desde Excel** (Inventario, `Ctrl+I`, admin): lee la plantilla de inventario (hojas
    "Inventario" y "Productos nuevos") o cualquier hoja con columna "Producto". Primero muestra una **vista previa**
    (se da de alta / ya existe / error por fila) y no guarda nada hasta confirmar con `F10`.
70. Fila sin precio y sin cantidad = producto que la tienda no vende: se ignora. Fila con cantidad pero sin precio =
    error. La fila de ejemplo (EJ01 / "EJEMPLO…") y la de total se ignoran.
71. Solo se **dan de alta productos nuevos**: un código que ya existe se omite y nunca se sobrescribe. Sin código se
    busca por nombre (para no duplicar al importar dos veces) y si no existe se genera `NV001`, `NV002`…
72. Todo o nada: las filas válidas se guardan en una sola transacción; el stock entra al kardex como AJUSTE
    "Inventario inicial (Excel)" y queda en bitácora `IMPORTAR_PRODUCTOS`.

## Después de la Fase 9
73. **Ícono del programa** (carrito blanco con moneda verde sobre azul marino, paleta de 03-diseno-ui) en la ventana,
    los diálogos, el `.exe` y los accesos directos (`src/main/resources/img/icono*.png|.ico`).
74. **Instalador para Windows 10/11** (`instalador/`): `Instalar.bat` → `instalar.ps1`, sin permisos de administrador.
    Instala en `%LOCALAPPDATA%\Programs\Punto de Venta`, crea accesos en Escritorio y menú Inicio y lo registra en
    "Agregar o quitar programas" (HKCU). Revisa 64 bits y espacio, pide cerrar el programa si está abierto, guarda la
    versión anterior hasta terminar de copiar y desbloquea los archivos bajados de internet. Nunca toca los datos.
    `empaquetar.ps1` genera `Punto de Venta <versión> - instalador.zip` con todo eso.
75. **Zona horaria**: Java 17 asocia "(UTC-07:00) La Paz, Mazatlán" con `America/Chihuahua` (UTC-6 desde 2022) y las
    fechas salían una hora adelantadas. Al arrancar, `ZonaHoraria.corregir()` lee la hora real de Windows
    (`ActiveTimeBias`) y, si no coincide, usa `America/Mazatlan` (o la hora fija de Windows); queda en el log.
76. **Sin extraer librerías al arrancar**: las DLL de JavaFX se sacan del `.jar` y, junto con la de SQLite, van en
    `app\native`; el programa las carga con `java.library.path` y `org.sqlite.lib.path`. Nada se escribe en Temp ni en
    `%USERPROFILE%\.openjfx` (antivirus, usuarios con acentos, Temp lleno o sin permisos).
77. **Versión de 32 bits** en el mismo paquete: Java 17 x86 (Temurin, recortado con jlink con los mismos módulos que la
    de 64) + JavaFX 17.0.8 win-x86 (la última de 17 publicada para 32 bits). Arranca con `runtime\bin\javaw.exe` y los
    argumentos de `lanzador.txt` (también `Punto de Venta.cmd`). `instalar.ps1` elige la versión según el Windows.
78. **Instalador más seguro**: revisa Windows 10/11, espacio (600 MB), memoria y pantalla (avisos), guarda un registro en
    `logs\instalacion.log`, y al final abre el programa y comprueba que siga vivo; si se cierra, explica qué hacer.
    Memoria máxima de Java fija en 768 MB (`-Xmx768m`) para que funcione igual en 32 bits.
79. **Impresora de tickets térmica genérica** (ESC/POS, 58 mm = 32 columnas, 80 mm = 48): se instala en Windows con
    su controlador o como "Genérico / Solo texto" y se elige en Configuración > Ticket y lector. `TicketService` arma el
    ticket con `EscPos` y lo manda crudo con `javax.print` en un hilo aparte (la venta nunca espera a la impresora; si
    falla, aviso y se reimprime). Página de códigos PC850 con tabla propia (acentos, ñ, ¿, ¡); opción "sin acentos"
    para impresoras que no la soportan. Opciones: imprimir siempre / preguntar / nunca, cortar papel, abrir cajón al
    cobrar en efectivo (pulso ESC p por la impresora). Reimprimir: `Ctrl+P` en Vender (última venta) y `P` en Ventas
    del turno y en el detalle de una venta (sale marcado "REIMPRESIÓN"; si está cancelada, "VENTA CANCELADA").
80. **Configuración de hardware por computadora** en `<datos>\hardware.properties` (no en la BD): el nombre de la
    impresora es de cada Windows; restaurar un respaldo de otra PC no la cambia.
81. **Lector de código de barras genérico** (USB/inalámbrico en modo teclado, el de fábrica: teclea el código + Enter).
    No necesita controlador. Se quitan caracteres de control de prefijo/sufijo (`CodigoBarras.limpiar`). Al buscar,
    un código numérico de 12+ dígitos también se busca con/sin ceros iniciales (UPC-A 12 = EAN-13 con 0 = GTIN-14),
    en Vender, Entrada de mercancía y "Probar lector". En Vender, si el foco quedó fuera del campo de código, lo que
    teclee el lector se manda al campo (`LectorCodigos`, se puede desactivar). No se permite dar de alta el mismo
    código con y sin el 0 inicial (sería el mismo producto para el lector).
    **Inventario**: escanear (o escribir un código y Enter) en el buscador elige el producto si existe; si no existe,
    ofrece darlo de alta con ese código (también Ins/Ctrl+N con el código en el buscador). Con el código ya puesto,
    el formulario empieza en "Nombre" (igual al crear desde Entrada de mercancía).
82. **Probar lector** (Configuración > Ticket y lector, F9): muestra lo leído, el tipo (EAN-13, UPC-A, EAN-8, GTIN-14 o
    interno, con dígito verificador) y a qué producto corresponde. Si salen símbolos cambiados es el idioma de teclado
    del lector (configurarlo en Latinoamérica/Español con su manual).
83. **Sacar sin cobrar** (Vender, F9; migración V3): el cajero/recepcionista agrega los productos al carrito como en
    una venta y, en vez de cobrar, los saca del inventario con un **motivo obligatorio** (Uso interno, Consumo del
    personal, Cortesía a cliente, Muestra u otro escrito). **No pide autorización** (lo pidió el cliente), pero baja
    el stock con un movimiento `SALIDA` en el kardex, queda en la bitácora (`SALIDA_SIN_COBRO`) y en las tablas
    `salidas`/`salida_detalle` con el costo y el precio del momento. No es una venta ni mueve efectivo: no aparece en
    el corte. El admin lo revisa en **Inventario → Salidas sin cobro (F9)**: por periodo, quién, qué, motivo, cuánto
    le costó al negocio y cuánto se dejó de vender; exportable a Excel. Permiso `SALIDA_SIN_COBRO` (cajero y admin).
84. **Código que no existe en Vender**: si lo escaneado (o escrito) es un código válido con al menos un número y no
    coincide con ningún producto ni por nombre, sale "Producto no encontrado" con **Dar de alta (Enter)** / **Buscar
    por nombre** / Cancelar (Esc). "Dar de alta" abre el formulario de producto con el código ya puesto (empieza en
    "Nombre") y, al guardarlo, el producto **se agrega a la venta en curso** (respeta `3*código`). Solo para quien
    tiene `EDITAR_INVENTARIO` (admin); el cajero ve "pide al administrador que lo dé de alta" y puede buscar por
    nombre. Una palabra sin números ("coca") sigue yendo directo al buscador.
