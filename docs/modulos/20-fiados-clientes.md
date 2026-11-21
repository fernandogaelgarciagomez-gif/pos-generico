# 20 — Fiados y clientes

**Sin límite de crédito.** Se permiten abonos parciales.

## Vista principal
- Búsqueda (F2) por nombre o teléfono.
- Tabla: Nombre, Teléfono, Saldo (rojo si > 0), Última compra, Último abono.
- Filtro "Solo con adeudo" (activado por defecto).
- Pie: total que deben todos los clientes.

## Acciones
| Tecla | Acción | Cajero | Admin |
|---|---|---|---|
| Insert | Nuevo cliente (nombre obligatorio, teléfono, notas) | ✅ | ✅ |
| A | Abonar | ✅ | ✅ |
| H | Historial | ✅ | ✅ |
| Enter | Editar cliente | ❌ | ✅ |
| Supr | Desactivar cliente (solo si saldo = 0) | ❌ | ✅ |

## Abonar
- Diálogo: muestra saldo actual en grande.
- Monto (pre-llenado con el saldo completo, todo seleccionado para sobrescribir).
- Método: E efectivo / T tarjeta / R transferencia.
- No permitir abono mayor al saldo.
- Muestra "Saldo nuevo" en vivo.
- Enter confirma → transacción: insertar `abonos`, actualizar `clientes.saldo`.
- Toast "Abono registrado. Saldo restante: $X". El abono entra al turno actual (corte).
- Botón "Liquidar todo" (`L`) = abono por el saldo completo.

## Historial (estado de cuenta)
- Línea de tiempo mezclando ventas a fiado (+) y abonos (−) con saldo acumulado:
```
Fecha        Concepto              Cargo     Abono     Saldo
10/11 14:20  Venta #1023          $150.00             $150.00
12/11 10:05  Abono (efectivo)               $100.00    $50.00
15/11 18:40  Venta #1088          $ 80.00             $130.00
```
- Enter en una venta → ver productos.
- `F7` exportar estado de cuenta a PDF/Excel (para mandar al cliente por WhatsApp).

## Fiado desde Vender
- En Vender, F4 asigna cliente; al cobrar con "F Fiado" o mixto con parte fiada,
  se suma al saldo. Si el cliente no existe, crear rápido sin salir de la venta.

## Cancelación de una venta fiada
- Al cancelar, se resta del saldo del cliente la parte que fue fiada.
- Si el saldo quedaría negativo (ya había abonado), dejar saldo a favor y avisar al admin.

## Criterios de aceptación
- [ ] Saldo siempre igual a Σ ventas fiadas − Σ abonos (agregar verificación en pruebas).
- [ ] Abonos aparecen en el corte del turno en que se hicieron.
- [ ] Todo sin mouse.
