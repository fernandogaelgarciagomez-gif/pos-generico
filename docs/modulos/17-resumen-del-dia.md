# 17 — Resumen del día

Vista rápida de cómo va el día, **sin cerrar el turno**.

## Contenido
Tarjetas arriba (grandes, como dashboard):
- Total vendido
- Número de ventas
- Ticket promedio
- Ganancia estimada (solo admin) = Σ (precio − costo) × cantidad

Desglose por método de pago:
| Método | Monto | # ventas |
|---|---|---|
| Efectivo | | |
| Tarjeta | | |
| Transferencia | | |
| Fiado | | |

Además:
- Abonos de fiados recibidos (por método)
- Entradas y retiros de caja
- **Efectivo que debería haber en caja ahora** = fondo + ventas efectivo + abonos efectivo
  + entradas − retiros (ventas en efectivo = `venta_pagos.monto`, ya sin el cambio)
- Ventas canceladas (número y monto)
- Top 10 productos más vendidos del día
- Ventas por hora (gráfica de barras simple con `BarChart` de JavaFX, colores del tema)

## Permisos
- Cajero: solo datos del turno actual, sin ganancia.
- Admin: puede elegir "Turno actual" o "Todo el día" (todos los turnos de hoy).

## Atajos
- `F7` exportar a Excel. `Esc` regresar.

## Criterios de aceptación
- [ ] Los números coinciden con lo que después muestra el corte.
- [ ] Cajero no ve ganancias.
