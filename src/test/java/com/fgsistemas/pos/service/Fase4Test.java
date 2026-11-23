package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.EntradaService.Linea;
import com.fgsistemas.pos.service.EntradaService.Resultado;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.service.InventarioService.TipoAjuste;
import com.fgsistemas.pos.util.Sesion;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 4: alta de productos, entrada suma stock, alertas de stock bajo. */
class Fase4Test {

    @TempDir
    Path tmp;

    private final InventarioService inventario = new InventarioService();
    private final EntradaService entradas = new EntradaService();

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
        new TurnoService().abrir(50000);
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    private static void login(String u, String p) throws Exception {
        new AuthService(Clock.systemUTC()).login(u, p);
    }

    private static Producto producto(String codigo, String nombre, TipoVenta tipo, String unidad,
                                     long precio, long costo, long stock, long minimo) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setTipoVenta(tipo);
        p.setUnidad(unidad);
        p.setPrecioVenta(precio);
        p.setCosto(costo);
        p.setStock(stock);
        p.setStockMinimo(minimo);
        return p;
    }

    private Producto refresco() throws Exception {
        return inventario.crear(producto("750100", "Refresco 600 ml", TipoVenta.PIEZA, "pza",
                1800, 1200, 10_000, 5_000), "Bebidas");
    }

    private Producto jamon() throws Exception {
        return inventario.crear(producto("JAM01", "Jamón de pavo", TipoVenta.PESO, "kg",
                18990, 12000, 2_500, 1_000), "Salchichonería");
    }

    // ---------------------------------------------------------------- alta / edición

    @Test
    void altaDeProductoConStockInicialEnKardex() throws Exception {
        Producto p = refresco();
        assertTrue(p.getId() > 0);
        List<MovimientoInventario> kardex = inventario.kardex(p.getId());
        assertEquals(1, kardex.size());
        assertEquals("AJUSTE", kardex.get(0).tipo());
        assertEquals("Stock inicial", kardex.get(0).motivo());
        assertEquals(10_000, kardex.get(0).stockResultante());
        assertEquals(List.of("Bebidas"), inventario.categorias());
    }

    @Test
    void crearCategoriaSinRepetir() throws Exception {
        assertEquals("Papelería", inventario.crearCategoria("  Papelería "));
        assertEquals("Papelería", inventario.crearCategoria("papelería"), "No duplica por mayúsculas");
        assertEquals(List.of("Papelería"), inventario.categorias());
        assertThrows(IllegalArgumentException.class, () -> inventario.crearCategoria(" "));
    }

    @Test
    void codigoDeBarrasConYSinCeroInicialEsElMismoProducto() throws Exception {
        Producto upc = inventario.crear(producto("036000291452", "Pañuelos", TipoVenta.PIEZA, "pza",
                2500, 1500, 0, 0), null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> inventario.crear(
                producto("0036000291452", "Pañuelos otra vez", TipoVenta.PIEZA, "pza", 2500, 1500, 0, 0), null));
        assertTrue(e.getMessage().contains("0 inicial"), e.getMessage());
        // El lector lo lee con 13 dígitos y aun así se encuentra.
        assertEquals(upc.getId(), inventario.buscarActivoPorCodigo("0036000291452").orElseThrow().getId());
    }

    @Test
    void codigoUnicoYValidaciones() throws Exception {
        refresco();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, this::refresco);
        assertTrue(e.getMessage().contains("Ya existe"));
        assertThrows(IllegalArgumentException.class, () -> inventario.crear(
                producto("X1", "Sin precio", TipoVenta.PIEZA, "pza", 0, 0, 0, 0), null));
        assertThrows(IllegalArgumentException.class, () -> inventario.crear(
                producto("con espacio", "Mal código", TipoVenta.PIEZA, "pza", 100, 0, 0, 0), null));
        assertThrows(IllegalArgumentException.class, () -> inventario.crear(
                producto("X2", "Pieza con fracción", TipoVenta.PIEZA, "pza", 100, 0, 1_500, 0), null));
    }

    @Test
    void editarNoCambiaStockYPrecioQuedaEnBitacora() throws Exception {
        Producto p = refresco();
        Producto cambio = p.copia();
        cambio.setPrecioVenta(2000);
        cambio.setStock(999_000); // debe ignorarse
        inventario.editar(cambio, "Bebidas");
        Producto leido = inventario.buscarActivoPorCodigo("750100").orElseThrow();
        assertEquals(2000, leido.getPrecioVenta());
        assertEquals(10_000, leido.getStock());
        assertEquals(1, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'PRECIO_CAMBIADO'"));
    }

    // ---------------------------------------------------------------- ajustes

    @Test
    void ajustesDeStock() throws Exception {
        Producto p = refresco();
        assertEquals(7_000, inventario.ajustarStock(p.getId(), TipoAjuste.FIJAR, 7_000, "Conteo físico"));
        assertEquals(9_000, inventario.ajustarStock(p.getId(), TipoAjuste.SUMAR, 2_000, "Devolución proveedor"));
        assertEquals(8_000, inventario.ajustarStock(p.getId(), TipoAjuste.RESTAR, 1_000, "Merma"));
        List<MovimientoInventario> k = inventario.kardex(p.getId());
        assertEquals(-1_000, k.get(0).cantidad());
        assertEquals("Merma", k.get(0).motivo());

        assertThrows(IllegalArgumentException.class,
                () -> inventario.ajustarStock(p.getId(), TipoAjuste.SUMAR, 1_000, " "));
        assertThrows(IllegalArgumentException.class,
                () -> inventario.ajustarStock(p.getId(), TipoAjuste.RESTAR, 9_000, "Merma"));
        assertThrows(IllegalArgumentException.class,
                () -> inventario.ajustarStock(p.getId(), TipoAjuste.SUMAR, 500, "Medio refresco"));
        assertThrows(IllegalArgumentException.class,
                () -> inventario.ajustarStock(p.getId(), TipoAjuste.FIJAR, 8_000, "Igual"));
    }

    @Test
    void ajusteConDecimalesEnProductoAGranel() throws Exception {
        Producto j = jamon();
        assertEquals(2_125, inventario.ajustarStock(j.getId(), TipoAjuste.RESTAR, 375, "Merma"));
    }

    // ---------------------------------------------------------------- stock bajo

    @Test
    void alertasDeStockBajoYListaDeCompras() throws Exception {
        refresco(); // 10 ≥ 5: bien
        Producto j = jamon(); // 2.5 kg, mínimo 1 kg: bien
        assertEquals(0, inventario.contarStockBajo());

        inventario.ajustarStock(j.getId(), TipoAjuste.FIJAR, 800, "Conteo físico"); // 0.8 ≤ 1
        assertEquals(1, inventario.contarStockBajo());
        List<Producto> compras = inventario.listaDeCompras();
        assertEquals(1, compras.size());
        assertEquals(1_200, InventarioService.cantidadSugerida(compras.get(0))); // 1×2 − 0.8 = 1.2 kg

        // En piezas se redondea hacia arriba.
        Producto pz = producto("P", "X", TipoVenta.PIEZA, "pza", 100, 50, 3_000, 5_000);
        assertEquals(7_000, InventarioService.cantidadSugerida(pz));
    }

    // ---------------------------------------------------------------- cajero

    @Test
    void cajeroConsultaSinCostosYNoEdita() throws Exception {
        refresco();
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");

        List<Producto> lista = inventario.listar(true);
        assertEquals(1, lista.size());
        assertEquals(0, lista.get(0).getCosto(), "El cajero no recibe costos");
        assertEquals(0, inventario.buscarActivoPorCodigo("750100").orElseThrow().getCosto());

        Producto p = lista.get(0);
        assertThrows(PermisoDenegadoException.class, () -> inventario.editar(p, null));
        assertThrows(PermisoDenegadoException.class,
                () -> inventario.ajustarStock(p.getId(), TipoAjuste.SUMAR, 1_000, "x"));
        assertThrows(PermisoDenegadoException.class, () -> inventario.kardex(p.getId()));
        assertThrows(PermisoDenegadoException.class,
                () -> entradas.guardar(null, null, null, List.of(new Linea(p.getId(), 1_000, 100)), false));
    }

    // ---------------------------------------------------------------- entradas

    @Test
    void entradaSumaStockKardexYActualizaCosto() throws Exception {
        Producto r = refresco();
        Producto j = jamon();
        Resultado res = entradas.guardar("Coca-Cola FEMSA", "F-123", null, List.of(
                new Linea(r.getId(), 24_000, 1250),      // costo cambia 12.00 → 12.50
                new Linea(j.getId(), 3_250, 12000)), false); // costo igual

        assertEquals(24 * 1250 + 39000, res.totalCosto()); // 3.25 kg × $120 = $390
        assertEquals(1, res.cambiosDeCosto().size());
        assertEquals(1200, res.cambiosDeCosto().get(0).costoAnterior());

        Producto r2 = inventario.buscarActivoPorCodigo("750100").orElseThrow();
        assertEquals(34_000, r2.getStock());
        assertEquals(1250, r2.getCosto());
        assertEquals(5_750, inventario.buscarActivoPorCodigo("JAM01").orElseThrow().getStock());

        MovimientoInventario ultimo = inventario.kardex(r.getId()).get(0);
        assertEquals("ENTRADA", ultimo.tipo());
        assertEquals(res.entradaId(), ultimo.referenciaId());
        assertEquals(List.of("Coca-Cola FEMSA"), entradas.proveedores());
        assertEquals(1, entradas.historial(LocalDate.now().minusDays(1), LocalDate.now(), "coca").size());
        assertEquals(2, entradas.detalle(res.entradaId()).size());
        assertEquals(0, contar("SELECT COUNT(*) FROM movimientos_caja"));
    }

    @Test
    void entradaPagadaDeCajaRegistraRetiroEnElTurno() throws Exception {
        Producto r = refresco();
        Resultado res = entradas.guardar("Proveedor", null, null, List.of(new Linea(r.getId(), 12_000, 1200)), true);
        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT tipo, monto, turno_id FROM movimientos_caja")) {
            assertTrue(rs.next());
            assertEquals("RETIRO", rs.getString(1));
            assertEquals(res.totalCosto(), rs.getLong(2));
            assertEquals(Sesion.turno().getId(), rs.getLong(3));
        }
    }

    @Test
    void productoRepetidoEnLaEntradaSeSuma() throws Exception {
        Producto r = refresco();
        entradas.guardar(null, null, null, List.of(
                new Linea(r.getId(), 2_000, 1200), new Linea(r.getId(), 3_000, 1300)), false);
        Producto r2 = inventario.buscarActivoPorCodigo("750100").orElseThrow();
        assertEquals(15_000, r2.getStock());
        assertEquals(1300, r2.getCosto());
        assertEquals(1, contar("SELECT COUNT(*) FROM entrada_detalle"));
    }

    @Test
    void siFallaAMitadNoSeGuardaNada() throws Exception {
        Producto r = refresco();
        assertThrows(IllegalArgumentException.class, () -> entradas.guardar(null, null, null, List.of(
                new Linea(r.getId(), 5_000, 1200), new Linea(99_999, 1_000, 100)), false));
        assertEquals(10_000, inventario.buscarActivoPorCodigo("750100").orElseThrow().getStock());
        assertEquals(0, contar("SELECT COUNT(*) FROM entradas_mercancia"));
        assertThrows(IllegalArgumentException.class, () -> entradas.guardar(null, null, null,
                List.of(new Linea(r.getId(), 1_500, 1200)), false), "Pieza no acepta fracción");
    }

    // ---------------------------------------------------------------- otros

    @Test
    void contadorDelMenuEmpiezaEnCero() throws Exception {
        assertEquals(0, new ResumenService().vendidoTurno().total());
        assertEquals(0, new ResumenService().vendidoHoy().ventas());
    }

    @Test
    void exportaExcel() throws Exception {
        Path archivo = new ExportService().excel(tmp.resolve("prueba.xlsx"), "Hoja",
                List.of("Código", "Precio", "Stock"),
                List.of(List.of("750100", new Monto(1850), new Cant(2_500))));
        assertTrue(Files.size(archivo) > 0);
        try (InputStream in = Files.newInputStream(archivo); XSSFWorkbook libro = new XSSFWorkbook(in)) {
            assertEquals("Código", libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(18.5, libro.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue(), 0.0001);
            assertEquals(2.5, libro.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue(), 0.0001);
        }
        assertFalse(ExportService.carpeta().toString().isBlank());
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
