package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.ResumenCaja;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.DevolucionService.Linea;
import com.fgsistemas.pos.service.VentaService.Pago;
import com.fgsistemas.pos.service.VentaService.VentaRegistrada;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Registro;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fase 9: prevención de errores humanos y devolución parcial. */
class Fase9Test {

    @TempDir
    Path tmp;

    private final VentaService ventas = new VentaService();
    private final DevolucionService devoluciones = new DevolucionService();
    private final CorteService cortes = new CorteService();
    private Producto refresco; // $18.00, costo $12.00, 100 pza

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        new AuthService(Clock.systemUTC()).login("ana", "secreta1");
        new TurnoService().abrir(50_000); // fondo $500.00
        refresco = crear("750100", "Refresco 600 ml", 1800, 1200);
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
        Fechas.setReloj(Clock.systemDefaultZone());
    }

    private Producto crear(String codigo, String nombre, long precio, long costo) throws Exception {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setTipoVenta(TipoVenta.PIEZA);
        p.setUnidad("pza");
        p.setPrecioVenta(precio);
        p.setCosto(costo);
        p.setStock(100_000);
        p.setStockMinimo(1_000);
        return new InventarioService().crear(p, null);
    }

    private VentaRegistrada vender(int piezas, long descuento, Pago... pagos) throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, piezas * 1_000L);
        if (descuento > 0) {
            c.setDescuento(descuento, null);
        }
        return ventas.cobrar(c, List.of(pagos));
    }

    private static Pago efectivo(long monto) {
        return new Pago(MetodoPago.EFECTIVO, monto, monto, null);
    }

    private long stock() throws Exception {
        return new InventarioService().buscarActivoPorCodigo("750100").orElseThrow().getStock();
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ------------------------------------------------------------------ avisos

    @Test
    void avisosDeCifrasSospechosas() {
        assertNotNull(Avisos.precio(18_000, 1_800, 1_200), "un cero de más en el precio");
        assertNotNull(Avisos.precio(180, 1_800, 0), "un cero de menos en el precio");
        assertNotNull(Avisos.precio(1_000, 0, 1_200), "precio menor que el costo");
        assertNull(Avisos.precio(2_000, 1_800, 1_200));

        assertNotNull(Avisos.costo(14_400, 1_200, 1_800), "costo de la caja en vez de la unidad");
        assertNotNull(Avisos.costo(2_000, 0, 1_800), "costo mayor que el precio");
        assertNull(Avisos.costo(1_300, 1_200, 1_800));

        assertNotNull(Avisos.fondo(500_000, 50_000L), "fondo con un cero de más");
        assertNull(Avisos.fondo(60_000, 50_000L));
        assertNull(Avisos.fondo(50_000, null));
        assertNotNull(Avisos.fondo(2_000_000, null));

        assertNotNull(Avisos.cambio(1_800, 200_000), "recibido $2,000 por $18");
        assertNull(Avisos.cambio(1_800, 50_000));

        assertNull(Avisos.diferenciaCorte(-1_000, 300_000), "$10 de diferencia no alarma");
        assertNotNull(Avisos.diferenciaCorte(-30_000, 300_000));
        assertNotNull(Avisos.diferenciaCorte(10_000, 100_000), "10 % de lo esperado");
        assertFalse(Avisos.diferenciaCorte(-30_000, 300_000).contains("$"), "no revela cifras del conteo a ciegas");

        assertNull(Cantidad.avisoCantidadInusual(12_000, TipoVenta.PIEZA, "pza"));
        assertNotNull(Cantidad.avisoCantidadInusual(600_000, TipoVenta.PIEZA, "pza"));
        assertNotNull(Cantidad.avisoCantidadInusual(150_000, TipoVenta.PESO, "kg"));
    }

    // ------------------------------------------------------------------ efectivo en caja

    @Test
    void noSePuedeSacarMasEfectivoDelQueHay() throws Exception {
        vender(10, 0, efectivo(18_000)); // caja: $500 + $180 = $680
        CajaService caja = new CajaService();
        assertEquals(68_000, caja.efectivoDisponible());
        assertThrows(IllegalArgumentException.class,
                () -> caja.registrar(MovimientoCajaDao.Tipo.RETIRO, 68_001, "Retiro con un cero de más", null));
        caja.registrar(MovimientoCajaDao.Tipo.RETIRO, 68_000, "Retiro de todo", null);
        assertEquals(0, caja.efectivoDisponible());
    }

    @Test
    void pagarMercanciaDeLaCajaRespetaElEfectivo() throws Exception {
        EntradaService entradas = new EntradaService();
        List<EntradaService.Linea> lineas = List.of(new EntradaService.Linea(refresco.getId(), 100_000, 1_200));
        // $1,200 de mercancía con $500 en caja
        assertThrows(IllegalArgumentException.class, () -> entradas.guardar("Coca", "F1", null, lineas, true));
        assertEquals(100_000, stock(), "no se guardó nada");
        entradas.guardar("Coca", "F1", null, lineas, false);
        assertEquals(200_000, stock());
    }

    // ------------------------------------------------------------------ duplicados, fondo y reloj

    @Test
    void detectaFacturaYProductoDuplicados() throws Exception {
        EntradaService entradas = new EntradaService();
        assertTrue(entradas.facturaYaRegistrada("Coca", "A-100").isEmpty());
        entradas.guardar("Coca", "A-100", null, List.of(new EntradaService.Linea(refresco.getId(), 1_000, 1_200)), false);
        assertTrue(entradas.facturaYaRegistrada("coca ", " a-100").isPresent());
        assertTrue(entradas.facturaYaRegistrada("Pepsi", "A-100").isEmpty(), "otra factura con el mismo número");
        assertTrue(entradas.facturaYaRegistrada(null, "A-100").isPresent());

        InventarioService inv = new InventarioService();
        assertTrue(inv.otroProductoConNombre("  refresco 600 ML ", -1).isPresent());
        assertTrue(inv.otroProductoConNombre("Refresco 600 ml", refresco.getId()).isEmpty(), "él mismo no cuenta");
    }

    @Test
    void recuerdaElUltimoFondoYDetectaElRelojAtrasado() throws Exception {
        TurnoService turnos = new TurnoService();
        assertEquals(50_000L, turnos.ultimoFondo().orElseThrow());
        assertNull(turnos.avisoRelojAtrasado());
        Fechas.setReloj(Clock.fixed(Fechas.ahora().minusDays(3).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
        assertNotNull(turnos.avisoRelojAtrasado());
    }

    @Test
    void ventaRechazaCantidadesImposibles() throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, Cantidad.MAXIMO + 1_000);
        assertThrows(IllegalArgumentException.class, () -> ventas.cobrar(c, List.of(efectivo(1))));
    }

    // ------------------------------------------------------------------ devolución parcial

    @Test
    void devolucionParcialRegresaStockYEfectivo() throws Exception {
        VentaRegistrada v = vender(5, 0, efectivo(9_000)); // $90
        assertEquals(95_000, stock());
        DevolucionService.Resultado r = devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 2_000)),
                "Producto defectuoso", null);
        assertEquals(3_600, r.total());
        assertEquals(97_000, stock());
        ResumenCaja resumen = cortes.resumenTurnoActual();
        assertEquals(50_000 + 9_000 - 3_600, resumen.efectivoEsperado(), "el reembolso sale de la caja");
        // Ganancia: 5 × $6 − 2 × $6 = $18
        assertEquals(1_800L, resumen.ganancia());
        assertEquals(1, contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'DEVOLUCION'"));
    }

    @Test
    void noSeDevuelveMasDeLoVendido() throws Exception {
        VentaRegistrada v = vender(3, 0, efectivo(5_400));
        devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 2_000)), "Defecto", null);
        assertThrows(IllegalArgumentException.class, () -> devoluciones.devolver(v.id(),
                List.of(new Linea(refresco.getId(), 2_000)), "Otra vez", null));
        assertEquals(1_000, devoluciones.devolvibles(v.id()).get(0).disponible());
        assertThrows(IllegalArgumentException.class, () -> devoluciones.devolver(v.id(),
                List.of(new Linea(refresco.getId(), 500)), "Media pieza", null));
    }

    @Test
    void elReembolsoRespetaElDescuentoYNuncaPasaDelTotal() throws Exception {
        VentaRegistrada v = vender(3, 400, efectivo(5_000)); // $54 − $4 = $50
        long uno = devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 1_000)), "A", null).total();
        long dos = devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 1_000)), "B", null).total();
        long tres = devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 1_000)), "C", null).total();
        assertEquals(1_667, uno); // $18 × 50/54
        assertEquals(5_000, uno + dos + tres, "devolver todo regresa exactamente lo cobrado");
    }

    @Test
    void ventasFiadasYCanceladasNoSeDevuelvenPorPartes() throws Exception {
        Cliente juan = new ClienteService().crear("Juan", null, null);
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        c.setCliente(juan);
        VentaRegistrada fiada = ventas.cobrar(c, List.of(new Pago(MetodoPago.FIADO, 1_800, null, null)));
        assertThrows(IllegalArgumentException.class, () -> devoluciones.devolvibles(fiada.id()));

        VentaRegistrada v = vender(2, 0, efectivo(3_600));
        ventas.cancelar(v.id(), "Error de captura", null);
        assertThrows(IllegalArgumentException.class, () -> devoluciones.devolver(v.id(),
                List.of(new Linea(refresco.getId(), 1_000)), "x", null));
    }

    @Test
    void noSeCancelaUnaVentaConDevoluciones() throws Exception {
        VentaRegistrada v = vender(2, 0, efectivo(3_600));
        devoluciones.devolver(v.id(), List.of(new Linea(refresco.getId(), 1_000)), "Defecto", null);
        assertThrows(IllegalArgumentException.class, () -> ventas.cancelar(v.id(), "Error", null));
        assertEquals(99_000, stock(), "el stock no se regresó dos veces");
    }

    @Test
    void devolucionDeUnTurnoYaCortado() throws Exception {
        VentaRegistrada v = vender(2, 0, efectivo(3_600));
        cortes.cerrarTurno(Sesion.turno().getId(), 53_600, null, null);
        new TurnoService().abrir(2_000); // fondo $20
        long id = devoluciones.ventaPorFolio(v.folio());
        devoluciones.devolver(id, List.of(new Linea(refresco.getId(), 1_000)), "Defecto", null);
        assertEquals(2_000 - 1_800, cortes.resumenTurnoActual().efectivoEsperado());
        // Quedan $2 en la caja: no alcanza para reembolsar otros $18
        assertThrows(IllegalArgumentException.class, () -> devoluciones.devolver(id,
                List.of(new Linea(refresco.getId(), 1_000)), "Defecto", null).total());
    }

    // ------------------------------------------------------------------ mensajes de error

    @Test
    void mensajesDeErrorEntendibles() {
        assertTrue(Registro.mensajeBaseDeDatos(new SQLException("[SQLITE_BUSY] The database file is locked"))
                .contains("ocupada"));
        assertTrue(Registro.mensajeBaseDeDatos(new SQLException("[SQLITE_CORRUPT] database disk image is malformed"))
                .contains("respaldo"));
        String generico = Registro.mensajeBaseDeDatos(new SQLException("no such column: xyz"));
        assertFalse(generico.contains("xyz"), "no muestra detalles técnicos");
        assertTrue(Registro.mensajeInesperado(new RuntimeException(new SQLException("SQLITE_FULL")))
                .contains("disco"));
    }

    // ------------------------------------------------------------------ importar de Excel

    private Path excelDePrueba() throws Exception {
        Path archivo = tmp.resolve("inventario.xlsx");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet ins = libro.createSheet("Instrucciones");
            ins.createRow(0).createCell(0).setCellValue("Solo llena las celdas amarillas");
            org.apache.poi.ss.usermodel.Sheet s = libro.createSheet("Inventario");
            Object[][] filas = {
                    {"Código", "Producto", "Categoría", "Se vende por", "Unidad", "Precio venta", "Costo",
                            "Cantidad contada", "Stock mínimo", "Valor a costo", "Notas"},
                    {"EJ01", "EJEMPLO: Coca-Cola 600 ml", "Refrescos", "Pieza", "pza", 18, 12.5, 24, 6, null, null},
                    {"RE02", "Coca-Cola 600 ml", "Refrescos y bebidas", "Pieza", "pza", 18, 12.5, 48, 12, null, null},
                    {"AB01", "Azúcar estándar", "Abarrotes básicos", "Peso", "kg", 32, 26.4, 12.5, 5, null, null},
                    {"AB02", "Arroz", "Abarrotes básicos", "Peso", "kg", null, null, null, null, null, null},
                    {"LA01", "Leche entera 1 L", "Lácteos", "Pieza", "pza", 28, 23, 6.5, null, null, null},
                    {"750100", "Refresco ya dado de alta", "Refrescos", "Pieza", "pza", 18, 12, 5, null, null, null},
                    {"RE03", "Agua 1 L", "Refrescos y bebidas", "Pieza", "pza", 12, null, null, null, null, null},
                    {null, null, null, null, null, null, null, null, "Total", null, null},
            };
            for (int r = 0; r < filas.length; r++) {
                org.apache.poi.ss.usermodel.Row row = s.createRow(r);
                for (int c = 0; c < filas[r].length; c++) {
                    Object v = filas[r][c];
                    if (v instanceof String t) {
                        row.createCell(c).setCellValue(t);
                    } else if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    }
                }
                if (r > 0) {
                    row.createCell(9).setCellFormula("IF(AND(ISNUMBER(G" + (r + 1) + "),ISNUMBER(H" + (r + 1) + ")),G"
                            + (r + 1) + "*H" + (r + 1) + ",\"\")");
                }
            }
            org.apache.poi.ss.usermodel.Sheet pn = libro.createSheet("Productos nuevos");
            org.apache.poi.ss.usermodel.Row h = pn.createRow(0);
            String[] enc = {"Código", "Producto", "Categoría", "Se vende por", "Unidad", "Precio venta", "Costo",
                    "Cantidad contada"};
            for (int c = 0; c < enc.length; c++) {
                h.createCell(c).setCellValue(enc[c]);
            }
            org.apache.poi.ss.usermodel.Row n = pn.createRow(1);
            n.createCell(1).setCellValue("Tamales de la señora");
            n.createCell(3).setCellValue("Pieza");
            n.createCell(5).setCellValue(15);
            n.createCell(7).setCellValue(20);
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(archivo)) {
                libro.write(out);
            }
        }
        return archivo;
    }

    @Test
    void importarDeExcelRevisaYLuegoDaDeAlta() throws Exception {
        ImportacionService imp = new ImportacionService();
        ImportacionService.Vista vista = imp.leer(excelDePrueba());

        // Ignora el ejemplo, el total y el arroz (sin precio ni cantidad).
        assertEquals(6, vista.filas().size());
        assertEquals(4, vista.nuevos(), "Coca, azúcar, agua y tamales");
        assertEquals(1, vista.existentes(), "750100 ya existía");
        assertEquals(1, vista.errores(), "leche por pieza con 6.5");
        // Valor a costo: 48 × 12.50 + 12.5 kg × 26.40 = 600 + 330
        assertEquals(93_000, vista.valorCosto());
        assertEquals(0, contar("SELECT COUNT(*) FROM productos WHERE codigo = 'RE02'"), "la vista previa no guarda");

        assertEquals(4, imp.importar(vista));
        Producto azucar = new InventarioService().buscarActivoPorCodigo("AB01").orElseThrow();
        assertEquals(12_500, azucar.getStock());
        assertEquals(TipoVenta.PESO, azucar.getTipoVenta());
        assertEquals(2_640, azucar.getCosto());
        assertEquals("Abarrotes básicos", azucar.getCategoriaNombre());
        assertTrue(new InventarioService().buscarActivoPorCodigo("NV001").isPresent(), "código generado");
        assertEquals(100_000, stock(), "el producto existente no cambia");
        assertEquals(3, contar("SELECT COUNT(*) FROM movimientos_inventario WHERE motivo = 'Inventario inicial (Excel)'"));

        // Importar otra vez el mismo archivo: todo ya existe, nada se duplica.
        ImportacionService.Vista otra = imp.leer(excelDePrueba());
        assertEquals(0, otra.nuevos());
        assertThrows(IllegalArgumentException.class, () -> imp.importar(otra));
    }

    @Test
    void importarRechazaArchivosQueNoSonExcel() throws Exception {
        Path basura = tmp.resolve("no-es-excel.xlsx");
        java.nio.file.Files.writeString(basura, "hola");
        assertThrows(IllegalArgumentException.class, () -> new ImportacionService().leer(basura));
    }
}
