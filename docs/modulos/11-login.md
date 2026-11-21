# 11 — Login

Pantalla que aparece siempre al abrir (una vez configurado el sistema) y al cerrar sesión.

## Diseño
- Fondo `#0B1A33`. Tarjeta **centrada vertical y horizontalmente**, 420 px de ancho.
- Arriba: logo (si hay) y nombre del negocio desde la tabla `negocio`.
- Campos: Usuario → Contraseña → botón **Entrar**.
- Abajo, en pequeño: fecha y hora actual, versión del sistema.

## Comportamiento
- Foco inicial en Usuario (o en Contraseña si viene del paso de bienvenida).
- Enter en Usuario → pasa a Contraseña. Enter en Contraseña → intenta entrar.
- Error: "Usuario o contraseña incorrectos" (no decir cuál de los dos falló);
  se limpia la contraseña y el foco regresa a ella.
- Usuario inactivo: "Este usuario está desactivado. Contacta al administrador."
- Tras 5 intentos fallidos seguidos: bloquear 30 segundos con cuenta regresiva.
- Registrar en bitácora logins exitosos y fallidos.
- Cerrar la app desde el login: `Alt+F4` o botón discreto "Salir".

## Al entrar
- Guardar usuario en `Sesion`.
- Ir a `12-turno-fondo-caja.md` (verificar turno) y de ahí al menú principal.

## Criterios de aceptación
- [ ] Tarjeta perfectamente centrada en cualquier resolución (probar 1366×768 y 1920×1080).
- [ ] Login completo solo con teclado.
- [ ] Bloqueo temporal tras 5 intentos.
