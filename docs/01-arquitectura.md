# Arquitectura

## Capas
```
Controller (JavaFX)  ->  Service (reglas + permisos)  ->  DAO (JDBC)  ->  SQLite
```
- **Controller**: solo UI, eventos de teclado y llamar servicios. Sin SQL.
- **Service**: valida datos, valida permisos (`Sesion.requiere(Permiso.X)`),
  maneja transacciones.
- **DAO**: SQL con `PreparedStatement` siempre (nunca concatenar strings).

## Arranque (`App.java`)
```
iniciar BD + migraciones
ejecutar respaldo automático si toca (ver 23-respaldos.md)
si UsuarioDao.existeAdmin() == false -> Bienvenida
si no -> Login
```

## Navegación
- Una sola `Stage` maximizada. `Navegador.ir("vista")` reemplaza la raíz de la `Scene`.
- Cada vista al cargarse debe poner el foco en su primer campo (`Platform.runLater(campo::requestFocus)`).
- `Esc` en cualquier pantalla (excepto login y bienvenida) regresa al menú principal,
  pidiendo confirmación si hay datos sin guardar (ej. venta con productos).

## Sesión (`Sesion`)
- `usuarioActual`, `turnoActual`
- `esAdmin()`, `tiene(Permiso p)`, `requiere(Permiso p)` (lanza excepción si no)
- Cerrar sesión limpia todo y vuelve a Login (el turno sigue abierto hasta hacer corte).

## Permisos (enum `Permiso`)
VENDER, VER_INVENTARIO, EDITAR_INVENTARIO, ENTRADA_MERCANCIA, VER_RESUMEN_DIA,
HACER_CORTE, VER_CORTES_PASADOS, FIADOS_VENDER, FIADOS_ABONAR, FIADOS_ADMINISTRAR,
CANCELAR_VENTA, DESCUENTOS, RETIROS_CAJA, USUARIOS, CONFIGURACION, RESPALDOS.
Tabla de qué tiene cada rol en `docs/modulos/21-usuarios-y-permisos.md`.

## Autorización de admin en caliente
Para acciones restringidas hechas desde la sesión de un cajero (ej. cancelar venta),
se abre un diálogo pequeño "Autorización de administrador" que pide usuario + contraseña
de un admin. Si es válido, se permite **solo esa acción** y se registra quién autorizó.

Implementación: `AuthService.autorizarAdmin(usuario, contraseña, acción)` valida y devuelve un
`Autorizacion(adminId, adminNombre)` (y deja `AUTORIZACION` o `AUTORIZACION_FALLIDA` en bitácora).
El servicio protegido recibe ese objeto y llama a `Autorizaciones.verificar(conexión, permiso, auth)`:
si el usuario ya tiene el permiso no hace falta; si no, comprueba en BD que sea un admin activo.
Se usa en: cancelar venta, descuentos y retiros de caja (`ventas.autorizada_por`).

## Piezas reutilizables de la interfaz
- `util/Acciones`: botón visible + atajo en la barra inferior de cada pantalla.
- `controller/Formulario`: diálogo de captura (Enter avanza, en el último guarda; Esc cancela;
  muestra el error del servicio sin cerrarse).
- `controller/Tablas`: columnas y diálogo de tabla de solo lectura (kardex, historial, detalle).
- `controller/BuscadorProductos`: búsqueda F2 (Entrada y Vender).
- `controller/Exportar` + `service/ExportService`: Excel en `Documentos\Punto de Venta\Exportaciones`.
- `controller/Flujo`: navegación login → turno → menú y manejo de errores de BD.

## Bitácora
Tabla `bitacora`: toda acción sensible (login, cancelación, corte, cambio de precio,
alta/baja de usuario, restaurar respaldo) con usuario, fecha y detalle.
