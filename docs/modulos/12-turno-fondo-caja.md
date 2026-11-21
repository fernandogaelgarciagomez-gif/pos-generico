# 12 — Turno y fondo de caja inicial

El sistema maneja **una caja** con **un turno abierto a la vez**.

## Después de cada login
1. Si **no hay turno abierto** → pantalla obligatoria "Abrir turno" (tarjeta centrada, con botones
   "Abrir turno (Enter)" y "Regresar al inicio de sesión (Esc)"):
   - "¿Con cuánto efectivo inicias la caja?"
   - Campo de monto (acepta 0). Enter confirma.
   - Se crea `turnos` con `estado='ABIERTO'`, `fondo_inicial`, `usuario_id`.
   - No se puede saltar: `Esc` regresa al login (cierra sesión).
2. Si **hay turno abierto del mismo usuario** → continuar directo al menú.
3. Si **hay turno abierto de otro usuario**:
   - Mensaje: "Hay un turno abierto de **{nombre}** desde las {hora}."
   - Opciones:
     - **Continuar con ese turno** (las ventas se registran con el usuario actual,
       pero dentro del mismo turno). Permitido a cualquier rol.
     - **Hacer corte de ese turno y abrir uno nuevo** (solo admin, o autorización admin).

## Reglas
- El fondo se pregunta **siempre** que se abre un turno nuevo, sin excepción.
- El turno no se cierra al cerrar sesión ni al cerrar la app; solo con el corte de caja.
- Si la app se abre y el turno abierto es de un día anterior, mostrar aviso:
  "Este turno lleva abierto desde ayer. Se recomienda hacer corte." (no bloquea).

## Criterios de aceptación
- [ ] Imposible llegar al menú sin turno abierto.
- [ ] El fondo inicial aparece después en el corte de caja.
- [ ] Cerrar y abrir la app mantiene el mismo turno.
