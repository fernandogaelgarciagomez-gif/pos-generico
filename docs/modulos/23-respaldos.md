# 23 — Respaldos

## Automático
- Al abrir la app, si `respaldo_automatico = 1` y no hay respaldo de hoy:
  copiar la BD a `data/respaldos/pos_AAAA-MM-DD_HHmm.db`.
- Usar `VACUUM INTO 'ruta'` de SQLite (copia consistente aunque la BD esté abierta).
- También hacer respaldo automático **después de cada corte de caja**.
- Borrar respaldos más antiguos que `dias_conservar_respaldos` (default 30).
- Si el respaldo falla: registrar en log y mostrar aviso al admin, sin bloquear la app.

## Manual (pestaña Respaldos en Configuración)
- **Respaldar ahora**: genera respaldo en `data/respaldos/`.
- **Respaldar en otra ubicación**: elegir carpeta (USB, Google Drive local, etc.).
- Lista de respaldos existentes con fecha y tamaño.
- **Restaurar** (`R` sobre un respaldo):
  1. Confirmación fuerte: escribir la palabra `RESTAURAR`.
  2. Antes de restaurar, hacer respaldo del estado actual (por seguridad).
  3. Reemplazar la BD y reiniciar la app (volver al login).
  4. Registrar en bitácora.

## Exportar a Excel
- Botón "Exportar todo a Excel": un archivo con hojas Productos, Clientes, Ventas del mes,
  Cortes del mes.

## Criterios de aceptación
- [ ] Se genera un respaldo diario sin intervención.
- [ ] Restaurar un respaldo deja el sistema exactamente en ese estado.
- [ ] Se conservan solo los últimos N días.
