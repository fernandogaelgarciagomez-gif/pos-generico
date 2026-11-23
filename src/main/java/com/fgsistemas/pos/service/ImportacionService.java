package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.CategoriaDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;
import com.fgsistemas.pos.util.Texto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importar productos desde Excel (Inventario inicial, Fase 9). Lee la plantilla "Inventario - …xlsx"
 * (hojas "Inventario" y "Productos nuevos") o cualquier hoja con los mismos encabezados.
 * <p>
 * Reglas (decisiones 69–72):
 * <ul>
 *   <li>Primero se revisa todo y se muestra una vista previa; nada se guarda hasta confirmar.</li>
 *   <li>Fila sin precio y sin cantidad = producto que la tienda no vende: se ignora en silencio.</li>
 *   <li>Solo se dan de alta productos nuevos; un código que ya existe se omite (nunca se sobrescribe un producto).</li>
 *   <li>Todo o nada: se importan todas las filas válidas en una sola transacción.</li>
 *   <li>El stock contado entra al kardex como AJUSTE "Inventario inicial (Excel)".</li>
 * </ul>
 */
public class ImportacionService {

    public enum Estado { NUEVO, YA_EXISTE, ERROR }

    /** Una fila del Excel ya revisada. */
    public record Fila(String hoja, int numero, String codigo, String nombre, String categoria, TipoVenta tipo,
                       String unidad, long precio, long costo, long cantidad, long minimo, Estado estado,
                       String mensaje) {
        public boolean seImporta() {
            return estado == Estado.NUEVO;
        }
    }

    /** Vista previa: todas las filas con datos, y los totales para el resumen. */
    public record Vista(List<Fila> filas) {
        public long nuevos() {
            return filas.stream().filter(Fila::seImporta).count();
        }

        public long existentes() {
            return filas.stream().filter(f -> f.estado() == Estado.YA_EXISTE).count();
        }

        public long errores() {
            return filas.stream().filter(f -> f.estado() == Estado.ERROR).count();
        }

        /** Valor a costo de lo que se va a importar. */
        public long valorCosto() {
            return filas.stream().filter(Fila::seImporta).mapToLong(f -> Cantidad.importe(f.costo(), f.cantidad())).sum();
        }
    }

    static final List<String> HOJAS_PREFERIDAS = List.of("inventario", "productos nuevos");
    private static final String MOTIVO_KARDEX = "Inventario inicial (Excel)";

    private final ProductoDao productoDao = new ProductoDao();
    private final CategoriaDao categoriaDao = new CategoriaDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /** Lee y revisa el archivo sin guardar nada. */
    public Vista leer(Path archivo) throws IOException, SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        if (archivo == null || !Files.isRegularFile(archivo)) {
            throw new IllegalArgumentException("No se encontró el archivo.");
        }
        List<Crudo> crudos = new ArrayList<>();
        try (InputStream in = Files.newInputStream(archivo); Workbook libro = WorkbookFactory.create(in)) {
            List<Sheet> hojas = new ArrayList<>();
            for (Sheet s : libro) {
                if (HOJAS_PREFERIDAS.contains(s.getSheetName().trim().toLowerCase(Locale.ROOT))) {
                    hojas.add(s);
                }
            }
            if (hojas.isEmpty()) {
                for (Sheet s : libro) {
                    if (encabezados(s) != null) {
                        hojas.add(s);
                        break;
                    }
                }
            }
            for (Sheet s : hojas) {
                crudos.addAll(leerHoja(s));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae && iae.getMessage() != null
                    && iae.getMessage().startsWith("La hoja")) {
                throw iae;
            }
            throw new IllegalArgumentException("No se pudo leer el archivo. Debe ser un Excel (.xlsx) y no estar "
                    + "dañado. Si está abierto en Excel, ciérralo e intenta de nuevo.");
        }
        if (crudos.isEmpty()) {
            throw new IllegalArgumentException("El archivo no tiene productos para importar. Revisa que tenga la columna "
                    + "\"Producto\" y que las filas tengan precio o cantidad.");
        }
        try (Connection c = Database.conectar()) {
            return new Vista(revisar(c, crudos));
        }
    }

    /**
     * Da de alta los productos NUEVO de la vista, todos en una transacción.
     *
     * @return cuántos se importaron
     */
    public int importar(Vista vista) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        List<Fila> nuevos = vista.filas().stream().filter(Fila::seImporta).toList();
        if (nuevos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos nuevos para importar.");
        }
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(c -> {
            int n = 0;
            for (Fila f : nuevos) {
                if (productoDao.existeCodigo(c, f.codigo(), -1)) {
                    throw new IllegalArgumentException("El código " + f.codigo() + " ya existe (¿se importó otra vez?). "
                            + "Vuelve a abrir el archivo para revisarlo.");
                }
                Producto p = aProducto(f);
                p.setCategoriaId(f.categoria() == null || f.categoria().isBlank() ? null
                        : categoriaDao.buscarOCrear(c, f.categoria().trim()));
                p.setActivo(true);
                productoDao.insertar(c, p);
                if (p.getStock() != 0) {
                    movimientoDao.registrar(c, p.getId(), MovimientoInventarioDao.Tipo.AJUSTE, p.getStock(),
                            p.getStock(), null, usuarioId, MOTIVO_KARDEX);
                }
                n++;
            }
            bitacoraDao.registrar(c, usuarioId, "IMPORTAR_PRODUCTOS", n + " productos importados de Excel · valor a "
                    + "costo " + Dinero.formatear(vista.valorCosto()));
            return n;
        });
    }

    // ------------------------------------------------------------------ lectura

    /** Fila tal como viene del Excel (textos). */
    private record Crudo(String hoja, int numero, String codigo, String nombre, String categoria, String tipo,
                         String unidad, String precio, String costo, String cantidad, String minimo) {
    }

    /** Columnas reconocidas → índice. */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("codigo", "codigo"), Map.entry("clave", "codigo"),
            Map.entry("producto", "nombre"), Map.entry("nombre", "nombre"), Map.entry("descripcion", "nombre"),
            Map.entry("categoria", "categoria"),
            Map.entry("se vende por", "tipo"), Map.entry("tipo", "tipo"),
            Map.entry("unidad", "unidad"),
            Map.entry("precio venta", "precio"), Map.entry("precio", "precio"), Map.entry("precio de venta", "precio"),
            Map.entry("costo", "costo"),
            Map.entry("cantidad contada", "cantidad"), Map.entry("cantidad", "cantidad"), Map.entry("stock", "cantidad"),
            Map.entry("existencia", "cantidad"),
            Map.entry("stock minimo", "minimo"), Map.entry("minimo", "minimo"));

    /** Busca la fila de encabezados en las primeras 10 filas. */
    private static Map<String, Integer> encabezados(Sheet s) {
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        for (int r = s.getFirstRowNum(); r <= Math.min(s.getLastRowNum(), s.getFirstRowNum() + 9); r++) {
            Row fila = s.getRow(r);
            if (fila == null) {
                continue;
            }
            Map<String, Integer> m = new HashMap<>();
            for (Cell celda : fila) {
                String t = Texto.normalizar(fmt.formatCellValue(celda)).trim();
                String clave = ALIAS.get(t);
                if (clave != null && !m.containsKey(clave)) {
                    m.put(clave, celda.getColumnIndex());
                }
            }
            if (m.containsKey("nombre")) {
                m.put("_fila", r);
                return m;
            }
        }
        return null;
    }

    private static List<Crudo> leerHoja(Sheet s) {
        Map<String, Integer> col = encabezados(s);
        if (col == null) {
            throw new IllegalArgumentException("La hoja \"" + s.getSheetName() + "\" no tiene la columna \"Producto\".");
        }
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        List<Crudo> lista = new ArrayList<>();
        for (int r = col.get("_fila") + 1; r <= s.getLastRowNum(); r++) {
            Row fila = s.getRow(r);
            if (fila == null) {
                continue;
            }
            String nombre = texto(fila, col.get("nombre"), fmt);
            String codigo = texto(fila, col.get("codigo"), fmt);
            if (nombre.isEmpty() || nombre.toUpperCase(Locale.ROOT).startsWith("EJEMPLO")
                    || "EJ01".equalsIgnoreCase(codigo)) {
                continue; // fila vacía, la fila de "Total" o la de ejemplo
            }
            Crudo k = new Crudo(s.getSheetName(), r + 1, codigo, nombre, texto(fila, col.get("categoria"), fmt),
                    texto(fila, col.get("tipo"), fmt), texto(fila, col.get("unidad"), fmt),
                    numero(fila, col.get("precio"), fmt), numero(fila, col.get("costo"), fmt),
                    numero(fila, col.get("cantidad"), fmt), numero(fila, col.get("minimo"), fmt));
            if (k.precio().isEmpty() && k.cantidad().isEmpty()) {
                continue; // producto de la lista que la tienda no vende
            }
            lista.add(k);
        }
        return lista;
    }

    private static String texto(Row fila, Integer col, DataFormatter fmt) {
        if (col == null) {
            return "";
        }
        Cell c = fila.getCell(col);
        if (c == null) {
            return "";
        }
        if (c.getCellType() == CellType.FORMULA) {
            return "";
        }
        return fmt.formatCellValue(c).trim();
    }

    /** Número como texto plano ("18.5"), sin formato de moneda; "" si está vacío. */
    private static String numero(Row fila, Integer col, DataFormatter fmt) {
        if (col == null) {
            return "";
        }
        Cell c = fila.getCell(col);
        if (c == null) {
            return "";
        }
        if (c.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (c.getCellType() == CellType.STRING) {
            return c.getStringCellValue().trim().replace("$", "").replace(",", "").trim();
        }
        return "";
    }

    // ------------------------------------------------------------------ revisión

    private List<Fila> revisar(Connection c, List<Crudo> crudos) throws SQLException {
        List<Fila> filas = new ArrayList<>();
        Set<String> codigosArchivo = new HashSet<>();
        int consecutivo = 1;
        for (Crudo k : crudos) {
            String codigo = k.codigo();
            if (codigo.isEmpty()) {
                // Sin código (productos nuevos escritos a mano). Si ya hay un producto con ese nombre (p. ej. se
                // importó antes este mismo archivo) se usa su código para no duplicarlo; si no, se genera uno corto.
                java.util.Optional<Producto> mismo = productoDao.buscarOtroConNombre(c, k.nombre(), -1);
                if (mismo.isPresent()) {
                    codigo = mismo.get().getCodigo();
                } else {
                    do {
                        codigo = String.format("NV%03d", consecutivo++);
                    } while (codigosArchivo.contains(codigo.toUpperCase(Locale.ROOT))
                            || productoDao.existeCodigo(c, codigo, -1));
                }
            }
            TipoVenta tipo = tipo(k.tipo());
            String unidad = k.unidad().isEmpty() ? (tipo == null ? "pza" : tipo.unidadDefault()) : k.unidad();
            String error = null;
            long precio = 0;
            long costo = 0;
            long cantidad = 0;
            long minimo = 0;
            try {
                if (tipo == null) {
                    throw new IllegalArgumentException("\"Se vende por\" debe ser Pieza, Peso o Fracción.");
                }
                if (k.precio().isEmpty()) {
                    throw new IllegalArgumentException("Falta el precio de venta.");
                }
                precio = dinero(k.precio(), "El precio");
                costo = k.costo().isEmpty() ? 0 : dinero(k.costo(), "El costo");
                cantidad = k.cantidad().isEmpty() ? 0 : cantidad(k.cantidad(), tipo, "La cantidad");
                minimo = k.minimo().isEmpty() ? 0 : cantidad(k.minimo(), tipo, "El stock mínimo");
                Producto p = new Producto();
                p.setCodigo(codigo);
                p.setNombre(k.nombre());
                p.setTipoVenta(tipo);
                p.setUnidad(unidad);
                p.setPrecioVenta(precio);
                p.setCosto(costo);
                p.setStockMinimo(minimo);
                InventarioService.normalizarYValidar(p);
                InventarioService.validarCantidadSegunTipo(cantidad, tipo, "La cantidad");
                if (!codigosArchivo.add(codigo.toUpperCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("El código " + codigo + " está repetido en el archivo.");
                }
            } catch (IllegalArgumentException e) {
                error = e.getMessage().replace("\n", " ");
            }
            Estado estado;
            String mensaje;
            if (error != null) {
                estado = Estado.ERROR;
                mensaje = error;
            } else if (productoDao.existeCodigo(c, codigo, -1)) {
                estado = Estado.YA_EXISTE;
                mensaje = "Ya existe un producto con este código: se omite (no se cambia).";
            } else {
                estado = Estado.NUEVO;
                String aviso = Cantidad.avisoCantidadInusual(cantidad, tipo, unidad) != null
                        ? "Cantidad poco común: revísala." : Avisos.precio(precio, 0, costo) != null
                        ? "El precio es menor que el costo." : null;
                mensaje = aviso == null ? "" : "⚠ " + aviso;
            }
            filas.add(new Fila(k.hoja(), k.numero(), codigo, k.nombre(), k.categoria(), tipo, unidad, precio, costo,
                    cantidad, minimo, estado, mensaje));
        }
        return filas;
    }

    private static TipoVenta tipo(String texto) {
        String t = Texto.normalizar(texto).trim();
        if (t.isEmpty() || t.startsWith("pieza") || t.equals("pza")) {
            return TipoVenta.PIEZA;
        }
        if (t.startsWith("peso") || t.equals("kg") || t.equals("granel")) {
            return TipoVenta.PESO;
        }
        if (t.startsWith("fraccion")) {
            return TipoVenta.FRACCION;
        }
        return null;
    }

    /** Precio desde Excel: se acepta con más de 2 decimales y se redondea a centavos (HALF_UP). */
    private static long dinero(String texto, String campo) {
        try {
            BigDecimal v = new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
            if (v.signum() < 0) {
                throw new IllegalArgumentException(campo + " no puede ser negativo.");
            }
            return Dinero.parsear(v.toPlainString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " no es un número: " + texto);
        }
    }

    private static long cantidad(String texto, TipoVenta tipo, String campo) {
        try {
            BigDecimal v = new BigDecimal(texto);
            if (v.signum() < 0) {
                throw new IllegalArgumentException(campo + " no puede ser negativa.");
            }
            if (v.scale() > 3) {
                v = v.setScale(3, RoundingMode.HALF_UP); // Excel guarda 12.499999999 como decimal largo
            }
            return Cantidad.parsear(v.toPlainString(), tipo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " no es un número: " + texto);
        }
    }

    private static Producto aProducto(Fila f) {
        Producto p = new Producto();
        p.setCodigo(f.codigo());
        p.setNombre(f.nombre());
        p.setTipoVenta(f.tipo());
        p.setUnidad(f.unidad());
        p.setPrecioVenta(f.precio());
        p.setCosto(f.costo());
        p.setStock(f.cantidad());
        p.setStockMinimo(f.minimo());
        return InventarioService.normalizarYValidar(p); // la copia conserva el stock
    }
}
