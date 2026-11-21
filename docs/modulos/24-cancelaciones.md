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

## Devolución parcial (opcional, fase de pulido)
- Cancelar solo algunos renglones: crear registro de devolución que regrese stock y
  descuente el monto en el turno actual como retiro de caja.

## Criterios de aceptación
- [ ] Cajero no puede cancelar sin credenciales de admin.
- [ ] Venta cancelada no suma en resumen ni en corte, pero aparece listada como cancelada.
- [ ] Stock y saldo de cliente regresan correctamente.
