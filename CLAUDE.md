# CLAUDE.md — POS Genérico (FG Sistemas)

Lee este archivo completo antes de escribir código. Es la fuente de verdad del proyecto.
Después lee `docs/00-plan-por-fases.md` y trabaja **una fase a la vez**.

## Qué es
Sistema de punto de venta de escritorio, genérico (sirve para cualquier negocio pequeño:
papelería, abarrotes, tienda de ropa, etc.). Una sola caja, sin hardware por ahora
(sin lector de código de barras ni impresora; el código de barras se teclea).

## Stack obligatorio
- **Java 17** (no usar features de versiones más nuevas)
- **JavaFX 17+** para la interfaz (FXML + CSS)
- **SQLite** vía `org.xerial:sqlite-jdbc` (archivo `pos.db` en la carpeta de datos, ver abajo)
- **Maven** como build tool (`javafx-maven-plugin` para correr: `mvn javafx:run`)
- **jBCrypt** (`org.mindrot:jbcrypt`) para hashear contraseñas
- **Apache POI** (`poi-ooxml`) para exportar a Excel
- **OpenPDF** (`com.github.librepdf:openpdf`) para exportar a PDF (corte, estado de cuenta)
- **JUnit 5** para pruebas de servicios (cuadre de corte, saldos, cantidades)
- Sin frameworks pesados (nada de Spring). JDBC directo con DAOs.

## Carpeta de datos
Nunca dentro del proyecto ni en OneDrive (SQLite + sincronización = corrupción, y
`Archivos de programa` no es escribible). Ruta: `%LOCALAPPDATA%\FGSistemas\POS\`
con subcarpetas `respaldos\`, `logs\`, `img\`. Se resuelve en `AppConfig.carpetaDatos()`
y se puede sobrescribir con la propiedad de sistema `-Dpos.datos=...` (útil en pruebas).
En los docs, toda mención a `data/` se refiere a esta carpeta.

## Reglas de oro (no negociables)
1. **Todo se opera con teclado.** El cajero nunca debe necesitar el mouse.
   `Tab` avanza, `Shift+Tab` regresa, `Enter` confirma, `Esc` cancela/regresa.
   Ver `docs/04-teclado-y-atajos.md`. Cada pantalla nueva debe cumplirlo.
   **Y además, toda acción tiene un botón visible** (con su atajo escrito en el botón) para quien
   prefiera el mouse. Ninguna función existe solo como atajo. Usar `util/Acciones` para registrar
   botón + atajo juntos en la barra inferior de cada pantalla.
2. **Dinero en centavos (`long`)** en BD y lógica. Nunca `double` para dinero.
   Solo se formatea a `$1,234.50` al mostrar (`Locale es-MX`).
   **Cantidades en milésimas (`long`)**: 1 pieza = 1000, 0.250 kg = 250, 1.5 m = 1500.
   Aplica a stock, mínimos, cantidades vendidas/recibidas y movimientos. Utilidad `Cantidad`.
   Importe = `round(precio_centavos × cantidad_milésimas / 1000)` con `HALF_UP`
   (usar `BigDecimal` o aritmética entera; nunca `double`). Ver `docs/05-decisiones.md`.
3. **Contraseñas siempre con BCrypt.** Nunca guardar texto plano.
4. **Permisos se validan en el servicio**, no solo ocultando botones en la UI.
5. **Ninguna venta se borra.** Se cancela (cambia estado) y queda en bitácora.
6. **Todo movimiento de stock queda registrado** en `movimientos_inventario`.
7. **Transacciones de BD** en ventas, cancelaciones, entradas, abonos y cortes.
8. Diseño **azul marino** según `docs/03-diseno-ui.md`. No inventar otros colores.
9. Textos de la interfaz **en español de México**.
10. Al terminar cada fase: compilar, correr, y verificar los criterios de aceptación
    del módulo correspondiente antes de pasar a la siguiente.

## Estructura de paquetes
```
src/main/java/com/fgsistemas/pos/
  App.java                 -> arranque, decide primera vez vs login
  config/                  -> Database.java, migraciones, AppConfig
  model/                   -> POJOs (Usuario, Producto, Venta, ...)
  dao/                     -> acceso a datos (UsuarioDao, ProductoDao, ...)
  service/                 -> reglas de negocio + validación de permisos
  controller/              -> controladores JavaFX (uno por pantalla)
  util/                    -> Dinero, Cantidad, Teclado, Alertas, Sesion, Navegador
src/main/resources/
  fxml/                    -> una vista por pantalla
  css/tema-azul-marino.css
  img/
src/test/java/...          -> pruebas JUnit 5 de servicios
```
(La BD, respaldos y logs viven en `%LOCALAPPDATA%\FGSistemas\POS\`, no en el proyecto.)

## Flujo general de la app
1. Abre la app → ¿existe algún usuario admin en BD?
   - **No** → Bienvenida + crear perfil de admin + datos del negocio (`docs/modulos/10-...`)
   - **Sí** → Login centrado (`11-login.md`)
2. Login correcto → ¿hay turno abierto?
   - No → pedir **fondo de caja inicial** (`12-turno-fondo-caja.md`)
3. Menú principal con botones grandes centrados (`13-menu-principal.md`)

## Índice de documentación
- `docs/00-plan-por-fases.md` — orden de construcción
- `docs/01-arquitectura.md` — capas, navegación, sesión
- `docs/02-base-de-datos.md` — esquema SQL completo
- `docs/03-diseno-ui.md` — paleta y componentes
- `docs/04-teclado-y-atajos.md` — navegación sin mouse
- `docs/05-decisiones.md` — decisiones tomadas con el cliente (manda sobre lo demás)
- `docs/modulos/` — especificación de cada pantalla con criterios de aceptación

## Si tienes dudas
Si algo no está especificado, elige la opción más simple que respete las reglas de oro,
y deja un comentario `// DECISION:` explicando qué elegiste.
