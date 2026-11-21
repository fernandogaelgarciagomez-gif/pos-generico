# 10 — Bienvenida y creación del primer admin

Se muestra **solo** cuando no existe ningún usuario con rol ADMIN en la BD.

## Paso 1: Bienvenida
- Tarjeta centrada: logo genérico, "¡Bienvenido a tu Punto de Venta!",
  texto corto: "Vamos a configurar tu sistema en 2 pasos."
- Botón **Comenzar** con el foco puesto (Enter avanza).

## Paso 2: Crear administrador
Campos (Tab / Enter avanzan):
1. Nombre completo
2. Usuario (sin espacios, minúsculas, 3–20 caracteres)
3. Contraseña (mínimo 6 caracteres)
4. Confirmar contraseña
5. Botón **Siguiente**

Validaciones en vivo debajo de cada campo (texto rojo pequeño).

## Paso 3: Datos del negocio
1. Nombre del negocio (obligatorio)
2. Teléfono (opcional)
3. Dirección (opcional)
4. Botón **Terminar**

## Al terminar
- En una sola transacción: insertar admin (contraseña con BCrypt) + fila `negocio` (id=1).
- Registrar en bitácora "Sistema configurado por primera vez".
- Mostrar "¡Listo! Ya puedes iniciar sesión." y llevar al Login con el usuario ya escrito
  y el foco en la contraseña.

## Indicador de pasos
Arriba de la tarjeta: `● ○ ○` → `● ● ○` → `● ● ●`. `Esc` regresa al paso anterior.

## Criterios de aceptación
- [ ] Con BD vacía, al abrir aparece la bienvenida; con admin existente nunca aparece.
- [ ] No se puede avanzar con campos inválidos o contraseñas distintas.
- [ ] La contraseña queda hasheada en BD.
- [ ] Todo el flujo se completa sin mouse.
