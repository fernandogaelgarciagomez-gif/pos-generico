# Consulta desde el celular (PENDIENTE)

Pedido del cliente (27/11/2026): ver la información de la tienda desde el celular **fuera de la tienda**.
Acordado con el cliente; falta planearlo técnicamente por fases antes de programarlo.

## Alcance de la primera versión
- **Solo lectura.** Nada se modifica desde el celular.
- **Página web** (no app de Play Store/App Store), con ícono en la pantalla de inicio.
- **Desde cualquier lugar**, sin Tailscale ni VPN, **sin pagar servidores**.

## Cómo se acordó hacerlo
```
PC del negocio ──(resumen cada 5 min y al hacer el corte)──► Firebase (plan gratis Spark) ◄── celular del dueño
```
- El Punto de Venta **sube un resumen** (no la base de datos) a Firestore; la PC **no recibe conexiones**
  (no se abren puertos del módem; muchos módems con CGNAT tampoco lo permitirían).
- La página web vive en Firebase Hosting (gratis). El dueño entra con correo y contraseña (Firebase Auth).
- Si la PC está apagada o sin internet, el celular muestra lo último que se subió con la hora
  ("Actualizado hoy 9:45 p. m."). Al volver el internet, se sube lo pendiente.
- No es tiempo real: hasta ~5 minutos de atraso (suficiente para el dueño).

## Qué mostrará
- **Hoy**: total vendido, número de ventas, ganancia, efectivo en caja, gastos.
- **Cortes** de los últimos días.
- **Stock bajo** (qué comprar).
- **Fiados**: quién debe y cuánto (o solo el total; ver preguntas abiertas).
- **Más vendidos** del día / la semana.

## Descartado (y por qué)
- Tailscale / VPN: el cliente no la quiere.
- Cloudflare Tunnel: gratis solo con dirección que cambia al reiniciar; con dominio fijo ya cuesta (~$200/año).
- Abrir puertos + DuckDNS: inseguro y muchos módems en México (CGNAT) no lo permiten.
- App nativa: publicar y mantener versiones Android/iPhone no se justifica para solo consulta.
- Vender o editar desde el celular: más adelante, si lo pide (permisos, bitácora, turnos).

## Preguntas abiertas (resolver antes de planear)
1. **Fiados**: ¿con nombre de cliente y saldo, o solo el total fiado? (los nombres quedarían en la nube de Google)
2. **Cuenta de Google del negocio** para crear el proyecto de Firebase: ¿la crea el cliente o el desarrollador?
   (Claude no crea cuentas).

## Condiciones a decirle al cliente
- La tienda necesita internet para que el celular vea datos recientes.
- Depende del plan gratuito de Firebase (límites muy por encima de lo que usa una tienda; Google podría cambiarlos).
