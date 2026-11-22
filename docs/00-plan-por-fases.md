# Plan por fases

Trabaja una fase a la vez. No avances sin que compile, corra y cumpla los criterios.

## Estado (actualizar al cerrar cada fase)
| Fase | Estado | Notas |
|---|---|---|
| 1 Base | ✅ Verificada por el cliente | |
| 2 Primera vez, login, turno | ✅ Verificada | "Corte y abrir nuevo" del turno ajeno: hecho en la Fase 7 |
| 3 Menú y usuarios | ✅ Verificada | + botones visibles y contador de ventas (pedido del cliente) |
| 4 Inventario y entradas | ✅ Verificada | + categoría nueva desde el formulario |
| 5 Vender y cancelaciones | ✅ Verificada | Vender con mosaicos de iniciales; clientes solo alta rápida |
| 6 Fiados | ✅ Verificada | Saldo a favor se usa en la siguiente venta a fiado; ver decisiones 31–39 |
| 7 Resumen y cortes | ✅ Verificada | + corte de turno ajeno, total con todo y fondo, Gastos e ingresos; decisiones 41–51 |
| 8 Configuración y respaldos | ⏳ Siguiente | |
| 9 Pulido | Pendiente | Incluye devolución parcial (24-cancelaciones) |

Las decisiones tomadas en el camino están en `docs/05-decisiones.md`.

## Fase 1 — Base del proyecto
- `pom.xml` con Java 17, JavaFX, sqlite-jdbc, jbcrypt, poi-ooxml, openpdf, junit-jupiter
- `.gitignore` y `git init`
- `AppConfig.carpetaDatos()` → `%LOCALAPPDATA%\FGSistemas\POS\` (ver `05-decisiones.md`)
- `Database.java`: crea `pos.db` en esa carpeta y ejecuta migraciones (`docs/02-base-de-datos.md`)
- `tema-azul-marino.css` con la paleta completa (`docs/03-diseno-ui.md`)
- Utilidades: `Dinero` (centavos ↔ texto), `Cantidad` (milésimas ↔ texto con unidad,
  importe = precio × cantidad), `Navegador` (cambio de pantallas),
  `Sesion` (usuario y turno actuales), `Teclado` (helpers de atajos), `Alertas`
- Ventana que abre maximizada con fondo azul marino
- ✅ Criterio: `mvn javafx:run` abre la ventana y se crea la BD con todas las tablas;
  `mvn test` pasa las pruebas de `Dinero` y `Cantidad`

## Fase 2 — Primera vez, login y turno
- `10-bienvenida-primer-admin.md`
- `11-login.md`
- `12-turno-fondo-caja.md`
- ✅ Criterio: primera ejecución pide admin; siguientes abren login; al entrar pide fondo

## Fase 3 — Menú principal y usuarios
- `13-menu-principal.md`
- `21-usuarios-y-permisos.md`
- ✅ Criterio: admin crea un cajero; el cajero entra y solo ve sus opciones

## Fase 4 — Inventario y entradas
- `15-inventario.md`
- `16-entrada-mercancia.md`
- ✅ Criterio: alta de productos, entrada de mercancía suma stock, alertas de stock bajo

## Fase 5 — Vender
- `14-vender.md` (incluye métodos de pago)
- `24-cancelaciones.md`
- ✅ Criterio: venta completa solo con teclado en menos de 15 segundos para 3 productos

## Fase 6 — Fiados
- `20-fiados-clientes.md`
- ✅ Criterio: venta a fiado, abono parcial, saldo correcto e historial

## Fase 7 — Resumen y cortes
- `17-resumen-del-dia.md`
- `18-corte-de-caja.md`
- `19-cortes-pasados.md`
- `25-gastos-e-ingresos.md` (agregado a pedido del cliente al cerrar la fase)
- ✅ Criterio: el corte cuadra fondo + efectivo + abonos − retiros contra lo contado

## Fase 8 — Configuración y respaldos
- `22-configuracion.md`
- `23-respaldos.md`
- ✅ Criterio: cambiar nombre del negocio se refleja en todo; respaldo automático diario

## Fase 9 — Pulido
- Revisar que TODAS las pantallas funcionen sin mouse (`docs/04-teclado-y-atajos.md`)
- Mensajes de error claros, sin stack traces al usuario
- Log de errores en `data/logs/pos.log`
- Generar `.jar` ejecutable (`maven-shade-plugin` o `jpackage` para instalador Windows)
