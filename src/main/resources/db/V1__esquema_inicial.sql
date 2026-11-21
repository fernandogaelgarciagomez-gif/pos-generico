-- V1: esquema inicial (docs/02-base-de-datos.md + docs/05-decisiones.md)
-- Dinero en centavos. Cantidades y stock en milésimas (1 pza = 1000).
-- schema_version la crea Migraciones antes de correr este script.

CREATE TABLE negocio (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  nombre TEXT NOT NULL,
  direccion TEXT,
  telefono TEXT,
  rfc TEXT,
  mensaje_ticket TEXT DEFAULT '¡Gracias por su compra!',
  logo_path TEXT,
  stock_minimo_default INTEGER DEFAULT 5000,
  permitir_stock_negativo INTEGER NOT NULL DEFAULT 0,
  metodo_tarjeta INTEGER NOT NULL DEFAULT 1,
  metodo_transferencia INTEGER NOT NULL DEFAULT 1,
  metodo_fiado INTEGER NOT NULL DEFAULT 1,
  descuento_maximo_pct INTEGER NOT NULL DEFAULT 20,
  respaldo_automatico INTEGER DEFAULT 1,
  dias_conservar_respaldos INTEGER DEFAULT 30
);

CREATE TABLE usuarios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL,
  usuario TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  rol TEXT NOT NULL CHECK (rol IN ('ADMIN','CAJERO')),
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);

CREATE TABLE categorias (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL UNIQUE
);

CREATE TABLE productos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT NOT NULL UNIQUE,
  nombre TEXT NOT NULL,
  categoria_id INTEGER REFERENCES categorias(id),
  precio_venta INTEGER NOT NULL,
  costo INTEGER NOT NULL DEFAULT 0,
  stock INTEGER NOT NULL DEFAULT 0,
  stock_minimo INTEGER NOT NULL DEFAULT 5000,
  tipo_venta TEXT NOT NULL DEFAULT 'PIEZA' CHECK (tipo_venta IN ('PIEZA','PESO','FRACCION')),
  unidad TEXT NOT NULL DEFAULT 'pza',
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);
CREATE INDEX idx_productos_nombre ON productos(nombre);
CREATE INDEX idx_productos_categoria ON productos(categoria_id);

CREATE TABLE clientes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL,
  telefono TEXT,
  notas TEXT,
  saldo INTEGER NOT NULL DEFAULT 0,
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);
CREATE INDEX idx_clientes_nombre ON clientes(nombre);

CREATE TABLE turnos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  abierto_en TEXT NOT NULL,
  fondo_inicial INTEGER NOT NULL,
  cerrado_en TEXT,
  estado TEXT NOT NULL CHECK (estado IN ('ABIERTO','CERRADO'))
);
CREATE INDEX idx_turnos_estado ON turnos(estado);

CREATE TABLE ventas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  folio INTEGER NOT NULL UNIQUE,
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  cliente_id INTEGER REFERENCES clientes(id),
  subtotal INTEGER NOT NULL,
  descuento INTEGER NOT NULL DEFAULT 0,
  total INTEGER NOT NULL,
  estado TEXT NOT NULL CHECK (estado IN ('COMPLETADA','CANCELADA')),
  fecha TEXT NOT NULL,
  cancelada_en TEXT,
  cancelada_por INTEGER REFERENCES usuarios(id),
  autorizada_por INTEGER REFERENCES usuarios(id),
  motivo_cancelacion TEXT
);
CREATE INDEX idx_ventas_turno ON ventas(turno_id);
CREATE INDEX idx_ventas_fecha ON ventas(fecha);
CREATE INDEX idx_ventas_cliente ON ventas(cliente_id);

CREATE TABLE venta_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id INTEGER NOT NULL REFERENCES ventas(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,
  precio_unitario INTEGER NOT NULL,
  costo_unitario INTEGER NOT NULL,
  importe INTEGER NOT NULL
);
CREATE INDEX idx_venta_detalle_venta ON venta_detalle(venta_id);
CREATE INDEX idx_venta_detalle_producto ON venta_detalle(producto_id);

CREATE TABLE venta_pagos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id INTEGER NOT NULL REFERENCES ventas(id),
  metodo TEXT NOT NULL CHECK (metodo IN ('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO')),
  monto INTEGER NOT NULL,
  recibido INTEGER,
  cambio INTEGER,
  referencia TEXT
);
CREATE INDEX idx_venta_pagos_venta ON venta_pagos(venta_id);

CREATE TABLE abonos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  cliente_id INTEGER NOT NULL REFERENCES clientes(id),
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  monto INTEGER NOT NULL,
  metodo TEXT NOT NULL CHECK (metodo IN ('EFECTIVO','TARJETA','TRANSFERENCIA')),
  saldo_anterior INTEGER NOT NULL,
  saldo_nuevo INTEGER NOT NULL,
  fecha TEXT NOT NULL,
  notas TEXT
);
CREATE INDEX idx_abonos_cliente ON abonos(cliente_id);
CREATE INDEX idx_abonos_turno ON abonos(turno_id);

CREATE TABLE entradas_mercancia (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  proveedor TEXT,
  factura TEXT,
  notas TEXT,
  total_costo INTEGER NOT NULL,
  fecha TEXT NOT NULL
);
CREATE INDEX idx_entradas_fecha ON entradas_mercancia(fecha);

CREATE TABLE entrada_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  entrada_id INTEGER NOT NULL REFERENCES entradas_mercancia(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,
  costo_unitario INTEGER NOT NULL
);
CREATE INDEX idx_entrada_detalle_entrada ON entrada_detalle(entrada_id);

CREATE TABLE movimientos_inventario (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('VENTA','CANCELACION','ENTRADA','AJUSTE')),
  cantidad INTEGER NOT NULL,
  stock_resultante INTEGER NOT NULL,
  referencia_id INTEGER,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  motivo TEXT,
  fecha TEXT NOT NULL
);
CREATE INDEX idx_mov_inv_producto ON movimientos_inventario(producto_id);

CREATE TABLE movimientos_caja (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('ENTRADA','RETIRO')),
  monto INTEGER NOT NULL,
  concepto TEXT NOT NULL,
  fecha TEXT NOT NULL
);
CREATE INDEX idx_mov_caja_turno ON movimientos_caja(turno_id);

CREATE TABLE cortes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turno_id INTEGER NOT NULL UNIQUE REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  fecha TEXT NOT NULL,
  fondo_inicial INTEGER NOT NULL,
  ventas_efectivo INTEGER NOT NULL,
  ventas_tarjeta INTEGER NOT NULL,
  ventas_transferencia INTEGER NOT NULL,
  ventas_fiado INTEGER NOT NULL,
  abonos_efectivo INTEGER NOT NULL,
  abonos_otros INTEGER NOT NULL,
  entradas_caja INTEGER NOT NULL,
  retiros_caja INTEGER NOT NULL,
  efectivo_esperado INTEGER NOT NULL,
  efectivo_contado INTEGER NOT NULL,
  diferencia INTEGER NOT NULL,
  num_ventas INTEGER NOT NULL,
  num_canceladas INTEGER NOT NULL,
  ganancia_estimada INTEGER NOT NULL,
  notas TEXT
);
CREATE INDEX idx_cortes_fecha ON cortes(fecha);

CREATE TABLE bitacora (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER REFERENCES usuarios(id),
  accion TEXT NOT NULL,
  detalle TEXT,
  fecha TEXT NOT NULL
);
CREATE INDEX idx_bitacora_fecha ON bitacora(fecha);
