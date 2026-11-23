# Errores humanos y cómo los previene el sistema (Fase 9)

Revisión de los errores que una persona puede cometer al usar el punto de venta y la protección que tiene
cada uno. Hay tres tipos de protección:

- **Bloqueo**: el servicio rechaza la operación (no se puede hacer, aunque se intente desde otra pantalla).
- **Aviso**: la cifra es posible pero sospechosa; se pide confirmar. El foco empieza en **No**
  (`Alertas.confirmarPeligro`), así que un Enter de más no acepta el error.
- **Red de seguridad**: si el error ya pasó, se puede recuperar.

Los avisos viven en `service/Avisos` y `Cantidad.avisoCantidadInusual`; los bloqueos en los servicios.

## Captura de cantidades y dinero

| Error | Protección | Tipo |
|---|---|---|
| Escanear un código de barras en el campo de **cantidad** (millones de piezas) | Máximo 999,999 unidades por captura (`Cantidad.MAXIMO`), en todo el sistema | Bloqueo |
| Escanear un código en un campo de **dinero** (fondo, recibido, precio, costo…) | Máximo $999,999.99 por captura (`Dinero.MAXIMO`) | Bloqueo |
| Teclear "50" en vez de "5" (venta, entrada, ajuste, stock inicial) | Más de 500 piezas o más de 100 kg/m/l en una captura pide confirmar | Aviso |
| Recibir efectivo con un cero de más ("Recibido $2,000" para $18) | Si el cambio pasa de $1,000 se pide confirmar antes de cobrar | Aviso |
| Recibido menor que el total | No deja cobrar | Bloqueo (ya existía) |

## Precios, costos e inventario

| Error | Protección | Tipo |
|---|---|---|
| Precio con un cero de más o de menos | Cambio de 5× o más (o 5 veces menos) contra el precio anterior pide confirmar | Aviso |
| Precio menor que el costo (vender con pérdida) | Pide confirmar | Aviso |
| Costo de la **caja completa** en vez de una unidad | Costo 3× mayor/menor que el anterior pide confirmar (entrada y edición) | Aviso |
| Costo de compra mayor que el precio de venta | Pide confirmar y sugiere ajustar el precio | Aviso |
| Dar de alta el mismo producto dos veces con otro código | Si ya hay un producto con ese nombre, pide confirmar | Aviso |
| Código repetido | No deja guardar | Bloqueo (ya existía) |
| Capturar **dos veces la misma factura** (el stock se sumaría dos veces) | Si la factura (y el proveedor) ya se registró, muestra cuál y pide confirmar | Aviso |
| Quitar una categoría que tiene productos | No deja | Bloqueo (Fase 8) |

## Caja, cortes y turnos

| Error | Protección | Tipo |
|---|---|---|
| Retiro, gasto o pago de mercancía mayor al efectivo de la caja (un cero de más) | No se puede sacar más de lo que debe haber; el formulario muestra cuánto hay | Bloqueo |
| Fondo de caja con un cero de más | 5× el último fondo (y $1,000 más), o $10,000 sin historial, pide confirmar | Aviso |
| Conteo del corte equivocado (billetes pegados, contar dos veces) | Diferencia de $200 o más (o 5 % de lo esperado) sugiere **volver a contar** antes de cerrar, sin revelar la cifra esperada | Aviso |
| Cerrar el turno con diferencia sin explicación | Nota obligatoria | Bloqueo (Fase 7) |
| Reloj de Windows atrasado (ventas con fecha equivocada) | Al iniciar sesión, si la fecha de la PC es anterior al último movimiento, avisa | Aviso |
| Turno abierto desde días anteriores | Aviso al entrar al menú | Aviso (Fase 2) |

## Ventas, cancelaciones y devoluciones

| Error | Protección | Tipo |
|---|---|---|
| Devolver más de lo vendido, o devolverlo dos veces | Por producto: vendido − ya devuelto | Bloqueo |
| Cancelar una venta que ya tuvo devoluciones (stock y dinero se regresarían dos veces) | No deja; se devuelve el resto | Bloqueo |
| Reembolsar de más por redondeo | El total devuelto nunca pasa de lo cobrado | Bloqueo |
| Devolver por partes una venta fiada (descuadra el saldo del cliente) | No deja; se cancela completa | Bloqueo |
| Vaciar una venta con Esc por accidente | Pide confirmar | Aviso (Fase 5) |
| Cancelar una venta sin querer | Motivo + confirmación con foco en No + autorización de admin si es cajero | Aviso (Fase 5) |

## Sistema y datos

| Error | Protección | Tipo |
|---|---|---|
| Cerrar la ventana (X / Alt+F4) con una venta sin cobrar, una entrada sin guardar o cambios en Configuración | Pide confirmar | Aviso |
| Abrir el sistema dos veces (doble clic de más) | La segunda vez avisa que ya está abierto y se cierra | Bloqueo |
| Contraseña "incorrecta" por Bloq Mayús | Aviso "Bloq Mayús está activado" junto a la contraseña | Aviso |
| Restaurar el respaldo equivocado | Escribir RESTAURAR + respaldo automático del estado actual | Red de seguridad (Fase 8) |
| Restaurar un archivo que no es respaldo | Se revisa antes; no se toca nada | Bloqueo (Fase 8) |
| Una actualización del sistema que falla a la mitad | Antes de cambiar el esquema se copia la BD a `respaldos\pos_…_antes-de-actualizar-vN.db` | Red de seguridad |
| Olvidar respaldar | Respaldo automático diario y después de cada corte | Red de seguridad (Fase 8) |
| Errores técnicos que asustan ("SQLITE_BUSY…") | Mensajes en español entendibles; el detalle va a `logs\pos0.log` | — |

## Pendientes / ideas (no hechos)

- **Límite de crédito por cliente** (fiado): necesitaría una columna nueva; hoy solo se ve el saldo al fiar.
- **Recordatorio de respaldo externo** (USB) cada N días.
- **Corte con turno abierto varios días**: hoy solo se avisa; se podría exigir el corte.
