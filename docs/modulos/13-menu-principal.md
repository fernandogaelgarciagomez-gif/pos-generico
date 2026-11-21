# 13 — Menú principal

## Diseño
- Barra superior (negocio, usuario, rol, turno, fecha/hora).
- Arriba del grid: **contador de dinero vendido** — "Vendido en este turno" (total y # de ventas
  completadas). El admin ve además "Vendido hoy (todos los turnos)". Las canceladas no suman.
- Centro: grid de **botones grandes centrados** (ver `03-diseno-ui.md`), 4 columnas; la última fila
  también queda centrada.
- Barra inferior de acciones: ayuda ("Clic o F1–F9 · Flechas · Enter") y botón **[F12] Cerrar sesión**.

## Botones (en este orden)
| # | Botón | Atajo | Admin | Cajero |
|---|---|---|---|---|
| 1 | Vender | F1 | ✅ | ✅ |
| 2 | Inventario | F2 | ✅ | ✅ (solo consulta) |
| 3 | Entrada de mercancía | F3 | ✅ | ❌ |
| 4 | Resumen del día | F4 | ✅ | ✅ (solo su turno) |
| 5 | Corte de caja | F5 | ✅ | ✅ |
| 6 | Cortes pasados | F6 | ✅ | ❌ |
| 7 | Fiados / Clientes | F7 | ✅ | ✅ (vender fiado y abonar) |
| 8 | Usuarios | F8 | ✅ | ❌ |
| 9 | Configuración | F9 | ✅ | ❌ |

- Los botones sin permiso **no se muestran** (el grid se reacomoda y sigue centrado).
- Foco inicial en "Vender".
- Flechas mueven el foco en la cuadrícula (con salto de fila).

## Avisos en el menú
- Si hay productos con stock bajo: insignia naranja en el botón Inventario con la cantidad
  (ej. "⚠ 7"). Solo visible para admin.
- Si el turno es de un día anterior: banda de aviso arriba del grid.

## Criterios de aceptación
- [ ] Cajero solo ve sus 5 botones; admin ve los 9.
- [ ] Cada atajo funciona y se ignora si el rol no tiene permiso.
- [ ] Botones centrados en cualquier resolución.
- [ ] El contador refleja las ventas del turno (y del día para admin) al volver al menú.
