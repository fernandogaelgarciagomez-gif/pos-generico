# 21 — Usuarios y permisos

Solo admin.

## Roles
| Permiso | ADMIN | CAJERO |
|---|---|---|
| VENDER | ✅ | ✅ |
| VER_INVENTARIO | ✅ | ✅ (sin costos) |
| EDITAR_INVENTARIO | ✅ | ❌ |
| ENTRADA_MERCANCIA | ✅ | ❌ |
| VER_RESUMEN_DIA | ✅ | ✅ (solo su turno, sin ganancia) |
| HACER_CORTE | ✅ | ✅ |
| VER_CORTES_PASADOS | ✅ | ❌ |
| FIADOS_VENDER | ✅ | ✅ |
| FIADOS_ABONAR | ✅ | ✅ |
| FIADOS_ADMINISTRAR (editar/desactivar clientes) | ✅ | ❌ |
| CANCELAR_VENTA | ✅ | ❌ (con autorización admin) |
| DESCUENTOS | ✅ | ❌ (con autorización admin) |
| RETIROS DE CAJA | ✅ | ❌ (con autorización admin) |
| USUARIOS | ✅ | ❌ |
| CONFIGURACION | ✅ | ❌ |
| RESPALDOS | ✅ | ❌ |

Implementar como `Map<Rol, Set<Permiso>>` en código (no en BD por ahora).
Dejar preparado para que en el futuro haya permisos personalizados por usuario.

## Pantalla de usuarios
- Tabla: Nombre, Usuario, Rol, Activo, Creado.
- `Insert`/`Ctrl+N` nuevo usuario: Nombre, Usuario, Contraseña, Confirmar, Rol.
- `Enter` (o doble clic) editar: nombre, rol, activo (reactivar = marcar Activo). "Restablecer contraseña" (`R`).
- `Supr` desactivar (nunca borrar: tiene ventas asociadas).
- `Ctrl+P` **Cambiar mi contraseña** (el usuario en sesión, pidiendo la actual).
- Todas estas acciones también están como botones en la barra inferior.

## Reglas
- No se puede desactivar ni quitar rol admin al **último admin activo**.
- Un admin no puede desactivarse ni quitarse el rol de admin a sí mismo.
- **Cambiar mi contraseña** vive solo en esta pantalla (decisión del cliente, 05-decisiones #22):
  el cajero no la cambia él mismo; se la restablece el admin (`R`).
- Todo cambio se registra en bitácora.

## Criterios de aceptación
- [ ] Un cajero intentando una acción restringida (aunque llame al servicio directo)
      recibe error de permisos.
- [ ] Siempre queda al menos un admin activo.
