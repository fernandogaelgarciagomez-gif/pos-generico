-- V3: salidas sin cobro (uso interno, consumo del personal, cortesías).
-- El producto sale del inventario sin venta y sin tocar la caja; queda quién, cuándo, qué y por qué.

CREATE TABLE salidas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turno_id INTEGER REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  motivo TEXT NOT NULL,
  fecha TEXT NOT NULL
);
CREATE INDEX idx_salidas_fecha ON salidas(fecha);

CREATE TABLE salida_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  salida_id INTEGER NOT NULL REFERENCES salidas(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,           -- milésimas
  costo_unitario INTEGER NOT NULL,     -- centavos (lo que le cuesta al negocio)
  precio_unitario INTEGER NOT NULL     -- centavos (lo que se dejó de cobrar)
);
CREATE INDEX idx_salida_detalle_salida ON salida_detalle(salida_id);

-- movimientos_inventario: agregar el tipo SALIDA (SQLite no permite cambiar un CHECK: se reconstruye).
CREATE TABLE movimientos_inventario_v3 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('VENTA','CANCELACION','ENTRADA','AJUSTE','DEVOLUCION','SALIDA')),
  cantidad INTEGER NOT NULL,
  stock_resultante INTEGER NOT NULL,
  referencia_id INTEGER,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  motivo TEXT,
  fecha TEXT NOT NULL
);
INSERT INTO movimientos_inventario_v3 (id, producto_id, tipo, cantidad, stock_resultante, referencia_id, usuario_id,
  motivo, fecha)
  SELECT id, producto_id, tipo, cantidad, stock_resultante, referencia_id, usuario_id, motivo, fecha
  FROM movimientos_inventario;
DROP TABLE movimientos_inventario;
ALTER TABLE movimientos_inventario_v3 RENAME TO movimientos_inventario;
CREATE INDEX idx_mov_inv_producto ON movimientos_inventario(producto_id);
