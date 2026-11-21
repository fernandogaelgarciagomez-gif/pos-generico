# 22 — Configuración

Solo admin. Pantalla con pestañas navegables con `Ctrl+Tab`.

## Pestaña Negocio
- Nombre (obligatorio), dirección, teléfono, RFC.
- Logo: seleccionar imagen (PNG/JPG). Se copia a `data/img/logo.png`.
  (Elegir archivo es la única acción que puede usar el diálogo del sistema.)
- Mensaje para ticket.
- Los cambios se reflejan de inmediato en login, barra superior y exportaciones.

## Pestaña Inventario
- Stock mínimo por defecto para productos nuevos.
- Permitir vender sin stock (stock negativo): Sí/No (default No).
- Administrar categorías (agregar, renombrar, desactivar).

## Pestaña Ventas
- Métodos de pago habilitados (casillas): Efectivo (siempre), Tarjeta, Transferencia, Fiado.
- Descuento máximo que se puede autorizar (%).

## Pestaña Respaldos
- Ver `23-respaldos.md`.

## Pestaña Acerca de
- Versión, "Desarrollado por FG Sistemas", datos de contacto (configurables en código).

## Notas para que sea genérico
- Todo texto que dependa del negocio sale de la tabla `negocio`, nunca hardcodeado.
- Guardar con `Ctrl+S` o botón Guardar al final de cada pestaña.

## Criterios de aceptación
- [ ] Cambiar el nombre del negocio se ve en el login al cerrar sesión.
- [ ] Deshabilitar un método de pago lo oculta en el cobro.
