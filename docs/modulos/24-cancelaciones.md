# 24 — Cancelación de ventas

## Dónde
- En Vender → `F11 Ventas del turno` → seleccionar venta → `C Cancelar`.
- Admin también desde Cortes pasados → detalle → venta (solo si el turno sigue abierto;
  ventas de turnos ya cortados **no se cancelan**, se hace devolución en el turno actual).

## Flujo
1. Mostrar detalle de la venta (productos, pagos, cliente).
2. Si el usuario es cajero → diálogo "Autorización de administrador" (usuario + contraseña).
3. Motivo obligatorio (lista rápida con teclas 1–4: Error de captura, Cliente se arrepintió,
   Producto defectuoso, Otro + texto).
4. Confirmar (Enter = Sí, Esc = No; el foco inicia en **No**).

## Al cancelar (transacción)
- `ventas.estado = 'CANCELADA'`, guardar quién canceló, quién autorizó, motivo y fecha.
- Regresar stock: `movimientos_inventario` tipo CANCELACION por cada producto.
- Si tuvo parte fiada: restar del saldo del cliente.
- El efectivo devuelto se refleja automáticamente porque la venta deja de contar en el corte.
- Bitácora.

## Devolución parcial (Fase 9, decisiones 59–62)
- Dónde: Vender → `F11` → `D` sobre una venta, o `Shift+D` para cualquier venta por **folio**
  (también de turnos ya cortados).
- Tabla con cada producto: vendido, ya devuelto y cuánto devolver (`Enter` cambia la cantidad, `T` todo).
  El reembolso se ve en vivo. `F10` → (autorización de admin si es cajero) → motivo (teclas 1–4) →
  confirmación con el foco en No.
- Al devolver (transacción): registro en `devoluciones` / `devolucion_detalle`, stock de regreso con kardex
  `DEVOLUCION`, **retiro de caja** "Devolución #N de la venta #F" en el turno actual y bitácora.
- Reembolso proporcional a lo cobrado del producto **y al descuento** de la venta; la suma de devoluciones
  nunca pasa del total cobrado.
- No se puede: devolver más de lo vendido menos lo ya devuelto, devolver una venta cancelada o con parte a
  fiado (esa se cancela completa), reembolsar más del efectivo de la caja, ni **cancelar** una venta que ya
  tuvo devoluciones.
- La ganancia del Resumen y del corte descuenta lo devuelto.

## Criterios de aceptación
- [ ] Cajero no puede cancelar sin credenciales de admin.
- [ ] Venta cancelada no suma en resumen ni en corte, pero aparece listada como cancelada.
- [ ] Stock y saldo de cliente regresan correctamente.
- [ ] Devolución parcial: regresa stock, el efectivo esperado baja por el reembolso y no se puede devolver de más.
