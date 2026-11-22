# 18 — Corte de caja

Cierra el turno actual. Lo hace quien está en el turno (admin o cajero).
El corte de un turno abierto por **otro usuario** se hace desde la pantalla de turno
(`12-turno-fondo-caja.md`): permiso `CORTE_TURNO_AJENO` (admin) o autorización de admin
(decisión 46).

Las cifras salen de `ResumenDao`, igual que en el Resumen del día y en Cortes pasados (decisión 41).

## Paso 1: Conteo de efectivo (a ciegas)
- El cajero cuenta el dinero **sin ver cuánto debería haber** (evita que "ajuste" el conteo).
- Opción A: un campo con el total contado.
- Opción B (`D` para alternar): desglose por denominación
  ($1000, $500, $200, $100, $50, $20, monedas $10, $5, $2, $1, $0.50); calcula el total solo.
  Tab avanza entre denominaciones. El desglose no se guarda, solo el total.
- Campo de notas opcional.
- Enter → siguiente paso. Esc → regresar al menú (o a la pantalla de turno si es corte ajeno).

## Paso 2: Resultado
La vista previa no guarda nada. **En grande, arriba** (decisión 50):

| Tarjeta | Qué es |
|---|---|
| **Debe haber en caja (con fondo)** | = efectivo esperado; debajo, lo contado y la diferencia |
| **Total con todo y fondo** | efectivo esperado + tarjeta + transferencia (ventas y abonos). El fiado **no** cuenta porque no se cobró |

Debajo, el desglose:
```
Fondo inicial                 $  500.00
+ Ventas en efectivo          $ 3,240.00
+ Abonos en efectivo          $   300.00
+ Entradas de caja            $     0.00   (ingresos de F8 y de Gastos e ingresos)
− Retiros de caja             $   450.00   (retiros de F8 y gastos de Gastos e ingresos)
= EFECTIVO ESPERADO           $ 3,590.00
  Efectivo contado            $ 3,580.00
  DIFERENCIA                  −$   10.00   (rojo si falta, verde si sobra/cuadra)

Otros (no están en caja):
  Tarjeta                     $ 1,200.00
  Transferencia               $   800.00
  Vendido a fiado             $   350.00
  Abonos tarjeta/transf.      $   100.00

TOTAL CON TODO Y FONDO (sin fiado)  $ 5,690.00

Ventas: 87 · Canceladas: 2 ($120.00)
Ganancia estimada (admin): $ 1,950.00
```
- Ventas en efectivo = `venta_pagos.monto` (sin el cambio). Las canceladas no suman.
- Si la diferencia ≠ 0, pedir nota obligatoria explicando.
- Enter → cerrar turno. Esc → regresar al conteo.

## Paso 3: Confirmar y terminar
- "¿Cerrar turno? Esta acción no se puede deshacer." (Enter = Sí, Esc = No)
- Transacción: se **recalculan las cifras** con los datos finales (si hubo movimientos mientras se
  contaba, se avisa), se inserta `cortes`, se marca el turno CERRADO y queda en bitácora.
- Sugerencia informativa: dejar el fondo para el siguiente turno y retirar el resto
  (el nuevo fondo se pregunta al abrir el siguiente turno).
- Exportar: `F7` PDF y `Shift+F7` Excel (las dos tarjetas grandes salen arriba en el PDF y como
  renglón en el Excel). Imprimir queda para el futuro.
- Enter:
  - Corte propio → cerrar sesión y volver al login (decisión 47).
  - Corte de turno ajeno → pedir el fondo del turno nuevo.

## Criterios de aceptación
- [ ] Fórmula exacta al centavo, probada con ventas mixtas, fiados, abonos, gastos/ingresos y cancelaciones.
- [ ] Las ventas canceladas NO suman.
- [ ] "Total con todo y fondo" = efectivo esperado + tarjeta + transferencia (ventas y abonos), sin fiado.
- [ ] Después del corte, el siguiente login pide fondo nuevo.
