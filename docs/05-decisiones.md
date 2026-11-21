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
