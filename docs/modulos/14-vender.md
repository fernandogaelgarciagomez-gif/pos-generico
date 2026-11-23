# 14 — Vender

La pantalla más importante. Debe ser **rápida** y 100% operable con teclado.

## Layout (actualizado, 05-decisiones #24–26)
- Izquierda: campo grande "Código, cantidad*código o busca por nombre" arriba; debajo
  "Productos — N de M coinciden", **chips de categorías** (Todas + cada categoría; con muchas
  categorías se ven ~2 filas y el resto con scroll) y **mosaicos** de productos: iniciales del
  nombre sobre un color, nombre, precio y "Quedan X" (naranja si stock bajo, rojo "Agotado").
  Escribir filtra los mosaicos; clic en un mosaico lo agrega. Máximo 60 mosaicos a la vez.
- Derecha: cliente asignado, carrito (Producto, Cant., Precio, Importe), subtotal, descuento,
  **TOTAL en 48 px**, número de artículos y botón verde **Cobrar (F10)**.
- Abajo: barra de botones con todas las acciones y su atajo.
- Foco permanente en el campo de código: después de cualquier acción vuelve ahí.
- Enter en el campo: código exacto → agrega; si el filtro deja un solo producto → lo agrega;
  si no → aviso y búsqueda (F2) con lo escrito.

## Agregar productos
- Escribir código + Enter → agrega 1 pieza. Si ya está en el carrito, suma cantidad.
- `3*` + código + Enter → agrega 3 piezas.
- Productos **por peso o fracción** (`tipo_venta` PESO/FRACCION):
  - Código + Enter (sin cantidad) → diálogo "Cantidad (kg)" / "(m)" con el foco puesto;
    muestra precio por unidad e importe en vivo. Pestaña/tecla `I` para capturar **por importe**
    ("$50 de jamón") y el sistema calcula la cantidad.
  - `0.250*código` → agrega directo 0.250 kg. `$50*código` → agrega lo que alcanza con $50
    (cantidad redondeada a 3 decimales; el importe se recalcula con la cantidad redondeada).
  - Cada captura es un renglón nuevo (no se suman pesos al mismo renglón).
  - Columna Cant. muestra unidad: `0.250 kg`, `1.500 m`, `3 pza`.
- Productos por pieza rechazan cantidades con decimales ("Este producto se vende por pieza").
- Código no encontrado → aviso breve "Producto no encontrado" y abre búsqueda (F2)
  con el texto escrito.
- **F2 Buscar**: diálogo con campo de texto y tabla que filtra por nombre/código al escribir;
  ↓ para bajar a la tabla, Enter para agregar, Esc para cerrar.
- Si el producto no tiene stock suficiente: avisar y no agregar (salvo que la configuración
  permita stock negativo).
- Si el producto queda en stock bajo después de la venta, no interrumpir; solo se refleja en alertas.

## Editar carrito
- ↑ ↓ seleccionan renglón (desde el campo de código también).
- `+` / `−` cambian cantidad en 1 (solo con el campo de código vacío; en peso/fracción
  abren el diálogo de cantidad). `Ctrl+Q` cambia cantidad con diálogo.
  `Supr` quita (sin confirmación, es reversible).
- "Número de artículos" en el panel derecho: suma de piezas + número de renglones a granel.
- **F6 Descuento**: monto o porcentaje sobre el total. Cajero requiere autorización admin.

## Cobrar (F10)
Diálogo centrado con el total grande y métodos:
- **E Efectivo**: campo "Recibido" (pre-llenado con el total, todo seleccionado);
  muestra cambio en grande en verde. No permite recibido < total.
- **T Tarjeta**: confirma monto (sin hardware; solo registro). Campo opcional de referencia.
- **R Transferencia**: confirma monto, campo opcional de referencia.
- **F Fiado**: requiere cliente (si no hay, abre buscador de clientes F4). Suma al saldo del cliente.
- **M Mixto**: tabla de pagos donde se reparte el total entre varios métodos
  (ej. $200 efectivo + $150 fiado). Muestra "Restante" hasta llegar a 0.
- Enter confirma.

## Al confirmar (transacción única)
1. Insertar `ventas` (folio consecutivo), `venta_detalle`, `venta_pagos`.
2. Restar stock y registrar `movimientos_inventario` tipo VENTA.
3. Si hay parte fiada: sumar a `clientes.saldo`.
4. Toast "Venta #folio registrada · Cambio $X" y carrito limpio, foco en código.
5. Ticket: si hay impresora térmica configurada (Configuración > Ticket y lector), se imprime según la opción
   (siempre / preguntar / nunca) y, si se pagó en efectivo y está activado, se abre el cajón. `Ctrl+P` reimprime.
   Si la impresora falla la venta ya quedó registrada: solo se avisa (05-decisiones.md #79).

## Otras acciones
- **F4 Cliente**: buscar/asignar cliente a la venta (o crear uno rápido con nombre y teléfono).
- **F8 Movimiento de caja**: registrar entrada o retiro de efectivo con concepto
  (ej. "Pago a proveedor de refrescos"). Cajero: solo retiros con autorización admin.
- **F11 Ventas del turno**: lista de ventas del turno actual → ver detalle → cancelar
  (ver `24-cancelaciones.md`).
- **Esc**: si hay productos, confirma "¿Vaciar venta?"; si está vacío, regresa al menú.

## Criterios de aceptación
- [ ] Venta de 3 productos en efectivo en menos de 15 s sin mouse.
- [ ] Pago mixto cuadra exacto al centavo.
- [ ] Venta de 0.375 kg a $189.90/kg da $71.21 y descuenta 0.375 del stock.
- [ ] `$50*código` en producto a $120/kg agrega 0.417 kg por $50.04.
- [ ] Stock y saldo de cliente correctos tras la venta.
- [ ] Si falla algo a mitad, no queda nada guardado (rollback).
