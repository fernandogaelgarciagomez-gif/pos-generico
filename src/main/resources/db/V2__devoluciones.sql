-- V2 (Fase 9): devolución parcial de ventas (24-cancelaciones.md).
-- Una devolución regresa al stock parte de una venta y devuelve el dinero en efectivo
-- como retiro de caja del turno actual.

CREATE TABLE devoluciones (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id INTEGER NOT NULL REFERENCES ventas(id),
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  autorizada_por INTEGER REFERENCES usuarios(id),
  motivo TEXT NOT NULL,
  total INTEGER NOT NULL,              -- centavos devueltos al cliente
  fecha TEXT NOT NULL
);
CREATE INDEX idx_devoluciones_venta ON devoluciones(venta_id);
CREATE INDEX idx_devoluciones_turno ON devoluciones(turno_id);
CREATE INDEX idx_devoluciones_fecha ON devoluciones(fecha);

CREATE TABLE devolucion_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  devolucion_id INTEGER NOT NULL REFERENCES devoluciones(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,           -- milésimas
  importe INTEGER NOT NULL,            -- centavos devueltos por este renglón
  costo_unitario INTEGER NOT NULL      -- costo de la venta original (para descontar la ganancia)
);
CREATE INDEX idx_devolucion_detalle_devolucion ON devolucion_detalle(devolucion_id);

-- movimientos_inventario: agregar el tipo DEVOLUCION (SQLite no permite cambiar un CHECK: se reconstruye).
CREATE TABLE movimientos_inventario_v2 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('VENTA','CANCELACION','ENTRADA','AJUSTE','DEVOLUCION')),
  cantidad INTEGER NOT NULL,
  stock_resultante INTEGER NOT NULL,
  referencia_id INTEGER,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  motivo TEXT,
  fecha TEXT NOT NULL
);
INSERT INTO movimientos_inventario_v2 (id, producto_id, tipo, cantidad, stock_resultante, referencia_id, usuario_id,
  motivo, fecha)
  SELECT id, producto_id, tipo, cantidad, stock_resultante, referencia_id, usuario_id, motivo, fecha
  FROM movimientos_inventario;
DROP TABLE movimientos_inventario;
ALTER TABLE movimientos_inventario_v2 RENAME TO movimientos_inventario;
CREATE INDEX idx_mov_inv_producto ON movimientos_inventario(producto_id);
