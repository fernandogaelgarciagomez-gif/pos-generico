# 23 — Respaldos

Implementado en `RespaldoService`. Nombre de los archivos: `pos_AAAA-MM-DD_HHmm[_motivo].db`
(decisión 55). Copia con `VACUUM INTO` de SQLite (consistente aunque la BD esté abierta).

## Automático
- Al abrir la app, si `respaldo_automatico = 1` y no hay respaldo de hoy:
  copiar la BD a `data/respaldos/pos_AAAA-MM-DD_HHmm.db`.
- También **después de cada corte de caja** (`pos_..._corte.db`), sin que un fallo afecte al corte.
- Borrar respaldos con más días que `dias_conservar_respaldos` (default 30). **Nunca se borra el más
  reciente** y no se tocan archivos con otro nombre.
- Si el respaldo falla: se registra en el log y el admin ve un aviso al entrar al menú, sin bloquear la app.

## Manual (pestaña Respaldos en Configuración)
- Casilla "Respaldo automático" y "Conservar respaldos de los últimos N días" (1–365); se guardan con `Ctrl+S`.
- **[F5] Respaldar ahora**: genera respaldo en `data/respaldos/` (y limpia los viejos).
- **[F6] Respaldar en otra ubicación…**: elegir carpeta (USB, Google Drive local, etc.).
- Lista de respaldos existentes con fecha, archivo y tamaño (el más reciente arriba).
- **Restaurar** (`R` sobre un respaldo, o **[Ctrl+O] Restaurar desde archivo…**) (decisión 56):
  1. Confirmación fuerte: escribir la palabra `RESTAURAR`.
  2. Se revisa que el archivo sea una BD íntegra del sistema (si no, no se toca nada).
  3. Antes de restaurar, respaldo del estado actual (`pos_..._antes-de-restaurar.db`).
  4. Reemplazar la BD (aplicando migraciones si el respaldo es de una versión anterior), cerrar sesión y
     volver al inicio.
  5. Registrar en bitácora (`RESTAURAR_RESPALDO`). Respaldar a mano también queda (`RESPALDO`).

## Exportar a Excel
- **[F7] Exportar todo a Excel**: un archivo con hojas Productos, Clientes, Ventas del mes,
  Cortes del mes (decisión 57), en `Documentos\Punto de Venta\Exportaciones\`.

## Criterios de aceptación
- [ ] Se genera un respaldo diario sin intervención.
- [ ] Restaurar un respaldo deja el sistema exactamente en ese estado.
- [ ] Se conservan solo los últimos N días.
- [ ] Un archivo que no es respaldo no se puede restaurar y no daña los datos.
