# 25 — Gastos e ingresos

Pedido del cliente después de la Fase 7 (decisión 51). Se abre desde el menú con `F10` o `0`.
Lo ve quien tiene permiso `VENDER` (admin y cajero).

Registra dinero que sale o entra a la caja **sin ser una venta**. Usa los mismos movimientos de caja
que `F8` en Vender (`movimientos_caja`): un **gasto** es un `RETIRO` y un **ingreso** es una `ENTRADA`.
Los dos entran al corte del turno (`18-corte-de-caja.md`).

## Pantalla
- Arriba, tres tarjetas del turno actual: **Gastos del turno** (− $), **Ingresos del turno** (+ $)
  y **Neto** (ingresos − gastos).
- Tabla con los movimientos del turno: Hora, Tipo (Gasto / Ingreso), Concepto, Registró, Monto
  (gasto en rojo con "−", ingreso en verde con "+"). Seleccionado el más reciente.
- Foco inicial en la tabla (para que `G` / `I` funcionen de inmediato).
- Barra inferior: `[G] Nuevo gasto`, `[I] Nuevo ingreso`, `[Esc] Menú`.

## Nuevo gasto / ingreso (formulario)
Campos: **Categoría** → **Monto** → **Detalle**. Enter avanza; en el último registra.

| Gastos | Ingresos |
|---|---|
| 1 Pago a proveedor | 1 Cambio o morralla |
| 2 Servicios (luz, agua, internet) | 2 Aportación del dueño |
| 3 Renta | 3 Cobro de un servicio |
| 4 Sueldos o pagos a personal | 4 Reposición de un gasto |
| 5 Insumos o limpieza del negocio | 5 Otro ingreso |
| 6 Comida | |
| 7 Retiro del dueño | |
| 8 Devolución de dinero a un cliente | |
| 9 Otro gasto | |

- Categoría con ↑ ↓ o con las teclas `1`–`9` (solo con el foco en la categoría; en Monto los números
  se escriben).
- Monto mayor a $0.
- Detalle opcional, **obligatorio** en "Otro gasto" / "Otro ingreso".
- Se guarda como concepto `Categoría: detalle` (o solo la categoría si no hay detalle).
- **Gastos del cajero**: piden autorización de admin (igual que los retiros de `F8`, permiso
  `RETIROS_CAJA`). Los ingresos no la piden.
- Queda en bitácora (`RETIRO_CAJA` / `ENTRADA_CAJA`). Toast "Gasto registrado" / "Ingreso registrado".

## Reglas
- Requiere turno abierto; los movimientos son del turno actual.
- **No se borran ni se editan.** Un error se corrige registrando el movimiento contrario.
- Devolver en efectivo un saldo a favor de un cliente (decisión 31) se registra como gasto
  "Devolución de dinero a un cliente".

## Criterios de aceptación
- [ ] `F10` / `0` desde el menú abre la pantalla, para admin y cajero.
- [ ] Un gasto resta y un ingreso suma en "Debe haber en caja" del corte, al centavo.
- [ ] El cajero no puede registrar un gasto sin autorización de admin (validado en `CajaService`).
- [ ] Sin detalle en "Otro" no se guarda y el formulario muestra el error sin cerrarse.
- [ ] Todo se opera con teclado y cada acción tiene su botón visible.
