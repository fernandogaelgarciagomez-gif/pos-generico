# 15 — Inventario

## Vista principal
- Campo de búsqueda arriba (filtra por código, nombre o categoría mientras se escribe).
- Filtros: Categoría (ComboBox), "Solo stock bajo" (F5), "Mostrar inactivos" (solo admin).
- Tabla: Código, Nombre, Categoría, Precio (`$189.90/kg`), Costo (solo admin), Stock
  (`12.350 kg`, `40 pza`), Mínimo, Estado.
- Filas con `stock <= stock_minimo` marcadas en naranja con ícono ⚠.
- Pie de tabla (solo admin): total de productos, valor del inventario a costo y a precio.

## Acciones (admin)
- **Insert / Ctrl+N**: nuevo producto → formulario (Tab/Enter avanzan):
  Código, Nombre, Categoría (con opción de crear nueva), **Se vende por**
  (Pieza / Peso / Fracción), Unidad (se ajusta al tipo: pza·caja·paq / kg·g / m·l·cm),
  Precio venta (por unidad), Costo (por unidad), Stock inicial, Stock mínimo
  (default de configuración). Stock y mínimo aceptan 3 decimales si es peso/fracción.
- **Enter**: editar producto seleccionado. Cambios de precio se registran en bitácora.
- **Ajuste de stock** (`Ctrl+A`): cantidad nueva o +/−, motivo obligatorio
  (merma, conteo físico, daño, etc.) → `movimientos_inventario` tipo AJUSTE.
- **Desactivar** (`Supr`): no se borra; queda inactivo y no aparece en ventas.
- **Movimientos (kardex)** (`K`): historial de todo lo que entró y salió del producto (ventas,
  cancelaciones, entradas, ajustes) con el stock resultante después de cada movimiento.
- **Lista de compras** (`F8`), **Exportar a Excel** (`F7`), **Solo stock bajo** (`F5`).
- En el formulario de producto, **[Ctrl+G] + Nueva** crea una categoría al momento.
- **F7 Exportar a Excel** la vista actual.

## Cajero
- Solo consulta: buscar, ver precio y stock. Sin costo, sin editar.

## Alertas de stock bajo
- Insignia en el menú principal (admin).
- Al iniciar sesión un admin, si hay productos en stock bajo, toast:
  "⚠ 7 productos con stock bajo (F2 para ver)".
- Botón/atajo para generar "Lista de compras" (productos en stock bajo con cantidad
  sugerida = mínimo × 2 − stock) exportable a Excel.

## Validaciones
- Código único. Precio > 0. Costo ≥ 0. Advertir si precio < costo (permitir, pero confirmar).

## Criterios de aceptación
- [ ] Búsqueda instantánea con 5,000 productos (índices en BD).
- [ ] Alertas visibles y correctas.
- [ ] Cajero no puede editar ni ver costos.
