# Base de datos (SQLite)

Archivo: `%LOCALAPPDATA%\FGSistemas\POS\pos.db`. Activar `PRAGMA foreign_keys = ON;` en cada conexión.
Fechas como `TEXT` ISO-8601 (`2026-11-21T14:30:00`). Dinero en **centavos** (`INTEGER`).
Cantidades y stock en **milésimas** (`INTEGER`): 1 pza = 1000, 0.250 kg = 250.
Migraciones versionadas en tabla `schema_version`.

```sql
CREATE TABLE schema_version (version INTEGER NOT NULL);

CREATE TABLE negocio (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  nombre TEXT NOT NULL,
  direccion TEXT, telefono TEXT, rfc TEXT,
  mensaje_ticket TEXT DEFAULT '¡Gracias por su compra!',
  logo_path TEXT,
  stock_minimo_default INTEGER DEFAULT 5000,      -- milésimas (5 unidades)
  permitir_stock_negativo INTEGER NOT NULL DEFAULT 0,
  metodo_tarjeta INTEGER NOT NULL DEFAULT 1,      -- métodos de pago habilitados
  metodo_transferencia INTEGER NOT NULL DEFAULT 1, -- (efectivo siempre habilitado)
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
  codigo TEXT NOT NULL UNIQUE,          -- código de barras o clave interna
  nombre TEXT NOT NULL,
  categoria_id INTEGER REFERENCES categorias(id),
  precio_venta INTEGER NOT NULL,        -- centavos POR UNIDAD (pza, kg, m, l...)
  costo INTEGER NOT NULL DEFAULT 0,     -- centavos por unidad
  stock INTEGER NOT NULL DEFAULT 0,     -- milésimas
  stock_minimo INTEGER NOT NULL DEFAULT 5000, -- milésimas
  tipo_venta TEXT NOT NULL DEFAULT 'PIEZA'
    CHECK (tipo_venta IN ('PIEZA','PESO','FRACCION')),
  unidad TEXT NOT NULL DEFAULT 'pza',   -- PIEZA: pza/caja/paq · PESO: kg/g · FRACCION: m/l/cm
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);
CREATE INDEX idx_productos_nombre ON productos(nombre);
CREATE INDEX idx_productos_categoria ON productos(categoria_id);

CREATE TABLE clientes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL,
  telefono TEXT, notas TEXT,
  saldo INTEGER NOT NULL DEFAULT 0,     -- centavos que debe (se recalcula con movimientos)
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);

CREATE TABLE turnos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  abierto_en TEXT NOT NULL,
  fondo_inicial INTEGER NOT NULL,
  cerrado_en TEXT,
  estado TEXT NOT NULL CHECK (estado IN ('ABIERTO','CERRADO'))
);

CREATE TABLE ventas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  folio INTEGER NOT NULL UNIQUE,
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  cliente_id INTEGER REFERENCES clientes(id),   -- obligatorio si es fiado
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

CREATE TABLE venta_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id INTEGER NOT NULL REFERENCES ventas(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,            -- milésimas
  precio_unitario INTEGER NOT NULL,     -- precio al momento de la venta
  costo_unitario INTEGER NOT NULL,
  importe INTEGER NOT NULL
);

-- Una venta puede pagarse con varios métodos (pago mixto)
CREATE TABLE venta_pagos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  venta_id INTEGER NOT NULL REFERENCES ventas(id),
  metodo TEXT NOT NULL CHECK (metodo IN ('EFECTIVO','TARJETA','TRANSFERENCIA','FIADO')),
  monto INTEGER NOT NULL,
  recibido INTEGER,                     -- solo efectivo
  cambio INTEGER,                       -- solo efectivo
  referencia TEXT                       -- opcional: tarjeta / transferencia
);

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

CREATE TABLE entradas_mercancia (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  proveedor TEXT, factura TEXT, notas TEXT,
  total_costo INTEGER NOT NULL,
  fecha TEXT NOT NULL
);

CREATE TABLE entrada_detalle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  entrada_id INTEGER NOT NULL REFERENCES entradas_mercancia(id),
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  cantidad INTEGER NOT NULL,            -- milésimas
  costo_unitario INTEGER NOT NULL
);

CREATE TABLE movimientos_inventario (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  producto_id INTEGER NOT NULL REFERENCES productos(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('VENTA','CANCELACION','ENTRADA','AJUSTE')),
  cantidad INTEGER NOT NULL,            -- milésimas; positivo suma, negativo resta
  stock_resultante INTEGER NOT NULL,    -- milésimas
  referencia_id INTEGER,                -- id de venta / entrada
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  motivo TEXT,
  fecha TEXT NOT NULL
);

-- Entradas y salidas de efectivo que no son ventas (pago a proveedor, retiro, etc.)
CREATE TABLE movimientos_caja (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turno_id INTEGER NOT NULL REFERENCES turnos(id),
  usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('ENTRADA','RETIRO')),
  monto INTEGER NOT NULL,
  concepto TEXT NOT NULL,
  fecha TEXT NOT NULL
);

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
  diferencia INTEGER NOT NULL,          -- contado - esperado
  num_ventas INTEGER NOT NULL,
  num_canceladas INTEGER NOT NULL,
  ganancia_estimada INTEGER NOT NULL,
  notas TEXT
);

CREATE TABLE bitacora (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  usuario_id INTEGER REFERENCES usuarios(id),
  accion TEXT NOT NULL,
  detalle TEXT,
  fecha TEXT NOT NULL
);
```

## Reglas
- `folio` de venta consecutivo: `SELECT COALESCE(MAX(folio),0)+1` dentro de la transacción.
- `clientes.saldo` se actualiza en la misma transacción que la venta a fiado o el abono.
- `stock` puede quedar negativo solo si `negocio.permitir_stock_negativo = 1` (por defecto: no).
- Productos `PIEZA`: cantidades múltiplos de 1000 (enteras). `PESO`/`FRACCION`: hasta 3 decimales.
- `importe = round(precio_unitario × cantidad / 1000)` HALF_UP, en enteros.
- Índices recomendados para reportes: `ventas(turno_id)`, `ventas(fecha)`, `venta_pagos(venta_id)`,
  `venta_detalle(venta_id)`, `abonos(cliente_id)`, `abonos(turno_id)`,
  `movimientos_inventario(producto_id)`, `movimientos_caja(turno_id)`, `cortes(fecha)`.
