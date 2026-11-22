# 22 — Configuración

Solo admin (menú `F9` o `9`). Pantalla con pestañas; `Ctrl+Tab` / `Ctrl+Shift+Tab` cambian de pestaña
y ponen el foco en su primer campo.

**Guardar**: un solo botón **[Ctrl+S] Guardar** en la barra inferior guarda todas las pestañas a la vez
(decisión 52). El logo, las categorías y los respaldos se aplican al momento con su propio botón.
`Esc` regresa al menú; si hay cambios sin guardar pide confirmación. Cada guardado queda en bitácora
(`CONFIGURACION`) con lo que cambió.

## Pestaña Negocio
- Nombre (obligatorio), dirección, teléfono, RFC (se guarda en mayúsculas), mensaje para ticket.
- Logo (decisión 54): **[Ctrl+L] Elegir logo…** abre el diálogo del sistema (única acción que lo usa),
  PNG o JPG de hasta 5 MB. Se copia a `data/img/logo.png` y se ve en vista previa.
  **[Ctrl+Shift+L] Quitar logo**.
- Los cambios se reflejan en login, barra superior y exportaciones (la barra se refresca al guardar).

## Pestaña Inventario
- Stock mínimo por defecto para productos nuevos (en unidades, hasta 3 decimales; se guarda en milésimas).
- Permitir vender sin stock (stock negativo): casilla (default No).
- Categorías (decisión 53), se aplican al momento: tabla Categoría · Productos.
  `Ins`/`Ctrl+N` nueva, `Enter` renombrar, `Supr` quitar (solo si ningún producto la usa).

## Pestaña Ventas
- Métodos de pago habilitados (casillas): Efectivo (siempre, deshabilitada), Tarjeta, Transferencia, Fiado.
  Tarjeta y transferencia también aplican a los abonos de fiados.
- Descuento máximo que se puede autorizar (0–100 %).

## Pestaña Respaldos
- Ver `23-respaldos.md`.

## Pestaña Acerca de
- Nombre de la app, versión, "Desarrollado por FG Sistemas", contacto (`AppConfig.CONTACTO_*`; si está
  vacío no se muestra), carpeta de datos, versión de la BD y de Java.

## Notas para que sea genérico
- Todo texto que dependa del negocio sale de la tabla `negocio`, nunca hardcodeado.
- Permiso `CONFIGURACION` validado en `NegocioService`; categorías con `EDITAR_INVENTARIO`.

## Criterios de aceptación
- [ ] Cambiar el nombre del negocio se ve en la barra superior al guardar y en el login al cerrar sesión.
- [ ] Deshabilitar un método de pago lo oculta en el cobro.
- [ ] El logo elegido se ve en el login.
- [ ] Una categoría con productos no se puede quitar; renombrarla cambia el nombre en sus productos.
- [ ] Salir con cambios sin guardar pide confirmación.
