# Diseño — Tema azul marino

## Paleta (variables en `tema-azul-marino.css`)
| Uso | Color |
|---|---|
| Fondo principal | `#0B1A33` |
| Superficie / tarjetas | `#13284D` |
| Superficie elevada (diálogos, inputs) | `#1B3563` |
| Botón principal | `#1F4E99` |
| Botón hover / foco | `#2E6BD1` |
| Acento (borde de foco, selección) | `#4FA3FF` |
| Texto principal | `#FFFFFF` |
| Texto secundario | `#A9BCDB` |
| Éxito | `#22C55E` |
| Advertencia (stock bajo) | `#F59E0B` |
| Peligro (cancelar, diferencia negativa) | `#EF4444` |
| Bordes sutiles | `#2A4475` |

## Tipografía
- Fuente del sistema (Segoe UI en Windows). Base 16 px.
- Títulos de pantalla: 28 px negrita. Totales en venta: 48 px negrita.
- Cifras de dinero alineadas a la derecha, con fuente tabular si está disponible.

## Componentes
- **Botones grandes del menú**: 260×140 px, radio 16 px, ícono arriba + texto + atajo
  (ej. "F1") en esquina en texto secundario. En grid centrado (`GridPane` dentro de
  `StackPane`), 4 columnas, espacio 24 px.
- **Foco visible siempre**: borde 3 px `#4FA3FF` + ligero brillo. Es clave porque el
  cajero usa solo teclado: debe ver en todo momento dónde está.
- **Inputs**: fondo `#1B3563`, texto blanco, alto 44 px, radio 8 px.
- **Tablas**: filas alternas `#13284D` / `#16305A`, fila seleccionada `#2E6BD1`.
  Filas con stock bajo con texto o etiqueta en `#F59E0B`.
- **Barra superior** en todas las pantallas (excepto login/bienvenida): nombre del negocio,
  usuario y rol, turno abierto desde, fecha/hora. Altura 56 px, fondo `#13284D`.
- **Barra inferior de acciones**: un **botón por cada acción** de la pantalla, con su atajo
  escrito en una etiqueta dentro del botón (ej. `[F2] Buscar`, `[F10] Cobrar`, `[Esc] Menú`).
  Sirve con mouse y recuerda los atajos. Fondo `#13284D`. Los botones no reciben foco con Tab
  (para no estorbar el flujo de teclado).
- **Diálogos**: centrados, fondo `#1B3563`, overlay oscuro detrás, siempre cerrables con `Esc`.
- **Toasts** de confirmación (venta registrada, guardado): esquina inferior derecha, 2.5 s;
  si salen varios a la vez se apilan hacia arriba.
- **Barra superior**: ya no lleva "Cambiar contraseña" (está en Usuarios).
- **Contador de ventas (menú)**: tarjeta con "Vendido en este turno" en verde (`#22C55E`) y, para admin,
  "Vendido hoy".
- **Íconos** del menú: Ikonli (Material Design 2), color acento; blanco cuando el botón tiene foco.

## Vender
- **Mosaicos** de 176 px: cuadro de color con las **iniciales** del producto (44 px, blanco), nombre
  (2 líneas), precio en acento y "Quedan X" (verde / naranja stock bajo / rojo agotado).
  Paleta fija de 8 colores `.mosaico-color-0..7` asignada por nombre (excepción documentada,
  05-decisiones #24).
- **Chips** de categorías redondeados; el elegido en `#2E6BD1` con borde acento. Máximo ~2 filas
  visibles con scroll.
- Botón **Cobrar** verde (`#22C55E`, texto oscuro) a todo lo ancho del panel derecho; el **cambio** en el
  diálogo de cobro va en 48 px verde.
- Métodos de pago como botones grandes con su letra (E, T, R, F, M) en una "tecla".

## Login y bienvenida
- Tarjeta centrada de 420 px de ancho sobre fondo `#0B1A33`, con nombre/logo del negocio arriba.
