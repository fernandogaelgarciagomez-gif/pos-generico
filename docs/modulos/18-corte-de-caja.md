# 18 — Corte de caja

Cierra el turno actual. Lo puede hacer el admin o el cajero del turno.

## Paso 1: Conteo de efectivo (a ciegas)
- El cajero cuenta el dinero **sin ver cuánto debería haber** (evita que "ajuste" el conteo).
- Opción A: un campo con el total contado.
- Opción B (`D` para alternar): desglose por denominación
  ($1000, $500, $200, $100, $50, $20, monedas $10, $5, $2, $1, $0.50); calcula el total solo.
  Tab avanza entre denominaciones.
- Campo de notas opcional.
- Enter → siguiente paso.

## Paso 2: Resultado
```
Fondo inicial                 $  500.00
+ Ventas en efectivo          $ 3,240.00
+ Abonos en efectivo          $   300.00
+ Entradas de caja            $     0.00
− Retiros de caja             $   450.00
= EFECTIVO ESPERADO           $ 3,590.00
  Efectivo contado            $ 3,580.00
  DIFERENCIA                  −$   10.00   (rojo si falta, verde si sobra/cuadra)

Otros (no están en caja):
  Tarjeta                     $ 1,200.00
  Transferencia               $   800.00
  Vendido a fiado             $   350.00
  Abonos tarjeta/transf.      $   100.00

Ventas: 87 · Canceladas: 2 ($120.00)
Ganancia estimada (admin): $ 1,950.00
```
- Si la diferencia ≠ 0, pedir nota obligatoria explicando.

## Paso 3: Confirmar
- "¿Cerrar turno? Esta acción no se puede deshacer." (Enter = Sí, Esc = No)
- Transacción: insertar `cortes`, marcar turno CERRADO, bitácora.
- Ofrecer "¿Retirar efectivo y dejar fondo para el siguiente turno?" → solo informativo
  (el nuevo fondo se pregunta al abrir el siguiente turno).
- Exportar el corte a PDF o Excel (`F7`). Imprimir queda para el futuro.
- Cerrar sesión y volver al login.

## Criterios de aceptación
- [ ] Fórmula exacta al centavo, probada con ventas mixtas, fiados, abonos y cancelaciones.
- [ ] Las ventas canceladas NO suman.
- [ ] Después del corte, el siguiente login pide fondo nuevo.
