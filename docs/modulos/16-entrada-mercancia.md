# 16 — Entrada de mercancía

Para registrar lo que llega del proveedor y sumar stock. Solo admin.

## Layout (parecido a Vender, para que sea familiar)
- Arriba: Proveedor (texto con autocompletado de proveedores usados antes),
  No. de factura/nota (opcional).
- Campo de código + **línea de captura** (producto, cantidad, costo unitario, subtotal) + tabla:
  Código, Producto, Stock actual, Entra, Costo unitario, Subtotal, Stock nuevo.
- Derecha: total a costo, número de productos, piezas (+ renglones a granel), casilla
  "Pagado de la caja" y notas.
- Abajo: botones Buscar (F2), Editar renglón (Enter), Quitar (Supr), Guardar (F10), Historial (F11), Menú (Esc).

## Flujo
1. Código + Enter → carga el producto en la línea de captura y pone foco en Cantidad
   (la cantidad acepta decimales si el producto es por peso/fracción).
2. Enter → pasa a Costo unitario (pre-llenado con el costo actual).
3. Enter → agrega a la tabla y regresa al campo de código.
   Si el producto ya estaba en la lista, la cantidad **se suma** al renglón existente.
4. Si el código no existe: ofrecer "Crear producto nuevo" con el formulario de producto
   (ya con el código escrito) y continuar la captura. `F2` busca por nombre.
5. **F10 Guardar entrada** → confirmación con resumen.
6. Por cada producto cuyo costo cambió: diálogo con costo anterior → nuevo, margen antes/ahora y
   **precio sugerido** que mantiene el margen; Esc deja el precio igual.

## Al guardar (transacción)
- Insertar `entradas_mercancia` + `entrada_detalle`.
- Sumar stock y registrar `movimientos_inventario` tipo ENTRADA.
- Si el costo cambió: actualizar `productos.costo` y preguntar
  "¿Actualizar también el precio de venta?" (mostrar margen actual vs nuevo).
- Opcional: casilla "Pagado de la caja" → crea `movimientos_caja` RETIRO por el total.

## Historial
- `F11`: lista de entradas pasadas con filtro por fecha y proveedor; Enter ver detalle.

## Criterios de aceptación
- [ ] Stock aumenta correctamente y queda en kardex.
- [ ] Flujo completo sin mouse.
- [ ] Si se marca "pagado de la caja", se refleja en el corte.
