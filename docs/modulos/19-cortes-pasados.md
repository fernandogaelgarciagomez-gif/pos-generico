# 19 — Cortes pasados

Solo admin.

## Vista
- Filtros: rango de fechas (atajos: `H` hoy, `S` esta semana, `M` este mes), usuario.
- Tabla: Fecha, Usuario, Fondo, Total vendido, Efectivo esperado, Contado, Diferencia
  (roja/verde), # ventas.
- Pie: totales del periodo filtrado y suma de diferencias.

## Detalle (Enter)
- Muestra exactamente la misma vista del Paso 2 del corte, más:
  - Lista de ventas del turno (Enter en una venta → ver productos y pagos)
  - Movimientos de caja y abonos del turno
  - Notas del corte

## Exportar
- `F7` exporta a Excel la tabla filtrada (una hoja resumen) y, si está en detalle,
  una hoja con las ventas del turno.

## Criterios de aceptación
- [ ] Filtrado rápido aunque haya un año de cortes.
- [ ] El detalle coincide con lo que se vio al hacer el corte.
