# Uso 100% con teclado (y también con mouse)

Objetivo: el cajero opera todo sin tocar el mouse. Esto se prueba en cada fase.
**Además**, cada acción tiene un botón visible en la barra inferior con su atajo escrito
(`[F2] Buscar`), para quien prefiera el mouse. Ninguna función existe solo como tecla.

## Reglas generales (todas las pantallas)
- `Tab` avanza al siguiente campo/botón en **orden lógico** (de arriba a abajo, izquierda a derecha).
  Definir el orden explícitamente; no confiar en el orden por defecto.
- `Shift+Tab` regresa.
- `Enter` en un campo de formulario **también avanza** al siguiente campo; en el último
  campo ejecuta la acción principal (Guardar / Entrar / Cobrar).
- `Esc` cancela el diálogo o regresa al menú (con confirmación si hay cambios).
- Al abrir cualquier pantalla o diálogo, el foco cae en el primer campo útil.
- En tablas: flechas ↑ ↓ para moverse, `Enter` para seleccionar/editar, `Supr` para quitar.
- Campos numéricos: solo aceptan números; al recibir foco seleccionan todo el texto
  (para que el cajero sobrescriba sin borrar).
- Los diálogos de confirmación: `Enter` = Sí, `Esc` = No. El botón peligroso
  (cancelar venta, borrar) **nunca** es el predeterminado.
- Ningún componente debe "atrapar" el foco (cuidado con TableView y ComboBox).
- Implementación: los atajos se registran con `addEventFilter(KeyEvent.KEY_PRESSED, ...)` sobre la
  **raíz de cada pantalla** (no sobre la Scene, para que se vayan al cambiar de pantalla).
  Usar `util/Acciones.agregar(...)`, que registra botón + atajo en una sola llamada.
- Doble clic en una fila de cualquier tabla = `Enter` (editar / ver detalle).
- **`Insert` siempre tiene alternativa `Ctrl+N`** (muchas laptops no tienen Insert).
  Mostrar ambas en la barra de atajos: `Insert/Ctrl+N Nuevo`.
- **Atajos de una sola letra** (`A`, `H`, `L`, `K`, `C`, `D`, `S`, `M`, etc.) solo se activan
  cuando el foco **no** está en un campo de texto (normalmente, con el foco en la tabla).
  Excepción: diálogo de Cobro, donde E/T/R/F/M funcionan mientras no haya un campo de monto
  en edición.

## Menú principal
| Tecla | Acción |
|---|---|
| F1 o 1 | Vender |
| F2 o 2 | Inventario |
| F3 o 3 | Entrada de mercancía |
| F4 o 4 | Resumen del día |
| F5 o 5 | Corte de caja |
| F6 o 6 | Cortes pasados |
| F7 o 7 | Fiados / Clientes |
| F8 o 8 | Usuarios (admin) |
| F9 o 9 | Configuración (admin) |
| F10 o 0 | Gastos e ingresos |
| F12 | Cerrar sesión |
| Flechas | Moverse entre botones |
| Enter | Abrir botón enfocado |

## Pantalla Vender
| Tecla | Acción |
|---|---|
| (escribir) + Enter | Agregar producto por código |
| F2 | Buscar producto por nombre |
| `cant*código` | Cantidad antes del código: `3*750100`, `0.250*JAM01` (kg), `1.5*TELA` (m) |
| `$importe*código` | Vender por importe: `$50*JAM01` calcula la cantidad (solo peso/fracción) |
| + / − | Subir / bajar 1 la cantidad del renglón seleccionado (**solo si el campo de código está vacío**; si tiene texto, se escriben normal). En productos por peso/fracción abre el diálogo de cantidad. |
| `Ctrl+Q` | Cambiar cantidad del renglón seleccionado (diálogo) |
| Supr | Quitar renglón seleccionado |
| F4 | Asignar cliente (para fiado) |
| F6 | Descuento (requiere admin si es cajero) |
| F8 | Entrada / retiro de efectivo de caja |
| F10 | Cobrar |
| F11 | Ventas del turno → `Enter` detalle, `C` cancelar (motivo con teclas 1–4) |
| Esc | Vaciar venta (con confirmación) / limpiar el campo / regresar al menú |
| ↑ ↓ | Elegir renglón del carrito sin salir del campo de código |

En el diálogo de **cantidad** (productos por peso/fracción): `I` alterna entre capturar
cantidad (kg, m…) o importe ($). Enter agrega.

## Diálogo de Cobro
| Tecla | Acción |
|---|---|
| E | Efectivo |
| T | Tarjeta |
| R | Transferencia |
| F | Fiado (pide cliente si no hay) |
| M | Pago mixto (método + monto + Enter por cada pago; `Supr` quita un pago) |
| F4 | Elegir / crear cliente sin salir del cobro |
| Enter | Confirmar |

En la **autorización de administrador** (cajero cancelando, dando descuento o retirando de caja):
usuario admin → contraseña → Enter.

## Otras pantallas
- **Inventario**: `F2` buscar, `Ins`/`Ctrl+N` nuevo producto, `Enter` editar, `Ctrl+A` ajustar stock,
  `K` movimientos (kardex), `Supr` desactivar/reactivar, `F5` solo stock bajo, `F7` exportar,
  `F8` lista de compras, `Esc` menú (o limpiar la búsqueda). En el formulario: `Ctrl+G` nueva categoría,
  ↑ ↓ cambian "Se vende por".
- **Entrada de mercancía**: Proveedor → Factura → Código o nombre (Enter) → Cantidad (Enter) → Costo (Enter);
  `F2` buscar, `Enter` en la tabla editar renglón, `Supr` quitar, `F10` guardar, `F11` historial,
  `Esc` cancelar la captura / salir (confirma si hay renglones).
- **Usuarios**: `Ins`/`Ctrl+N` nuevo, `Enter` editar, `R` restablecer contraseña, `Supr` desactivar,
  `Ctrl+P` cambiar mi contraseña, `Esc` menú.
- **Fiados**: `F2` buscar cliente, `Ins`/`Ctrl+N` nuevo cliente, `A` abonar, `H` historial,
  `F5` solo con adeudo, `Esc` menú (o limpiar la búsqueda). Admin: `Enter` editar, `Supr` desactivar/reactivar
  (cajero: `Enter` abre el historial). En Abonar: `E` / `T` / `R` método, `L` liquidar todo, `Enter` confirma.
  En el historial: `Enter` ver productos de la venta, `F7` PDF, `Shift+F7` Excel.
- **Gastos e ingresos**: `G` nuevo gasto, `I` nuevo ingreso (categoría con ↑ ↓ o 1–9), `Esc` menú.
- **Resumen del día**: `T` turno actual / `D` todo el día (admin), `F5` actualizar, `F7` Excel, `Esc` menú.
- **Corte de caja**: Paso 1 contar (`D` total o por denominación, Tab entre denominaciones, `Enter` continuar,
  `Esc` regresar); Paso 2 resultado (`Enter` cerrar turno, `Esc` regresar al conteo); Paso 3 `F7` PDF,
  `Shift+F7` Excel, `Enter` terminar.
- **Configuración** (admin): `Ctrl+Tab` / `Ctrl+Shift+Tab` cambian de pestaña, `Ctrl+S` guarda todas las
  pestañas, `Esc` menú (confirma si hay cambios). Negocio: `Ctrl+L` elegir logo, `Ctrl+Shift+L` quitarlo.
  Inventario: `Ins`/`Ctrl+N` nueva categoría, `Enter` renombrar, `Supr` quitar. Respaldos: `F5` respaldar ahora,
  `F6` en otra ubicación, `R` restaurar el seleccionado, `Ctrl+O` restaurar desde archivo, `F7` exportar todo.
- **Cortes pasados**: `H` hoy, `S` esta semana, `M` este mes, flechas para elegir, `Enter` ver detalle,
  `F7` exportar a Excel. En el detalle: `Enter` ver la venta, `F7` Excel, `Shift+F7` PDF.
