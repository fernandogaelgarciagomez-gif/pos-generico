package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.VentaService.Pago;
import com.fgsistemas.pos.service.VentaService.VentaRegistrada;
import com.fgsistemas.pos.util.Captura;
import com.fgsistemas.pos.util.Iniciales;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de las Fases 5 (Vender) y cancelaciones. */
class Fase5Test {

    @TempDir
    Path tmp;

    private final InventarioService inventario = new InventarioService();
    private final VentaService ventas = new VentaService();
    private Producto refresco;
    private Producto jamon;

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
        new TurnoService().abrir(50000);
        refresco = inventario.crear(producto("750100", "Refresco 600 ml", TipoVenta.PIEZA, "pza", 1800, 1200, 10_000), "Bebidas");
        jamon = inventario.crear(producto("JAM01", "Jamón de pavo", TipoVenta.PESO, "kg", 18990, 12000, 2_000), null);
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    private static void login(String u, String p) throws Exception {
        new AuthService(Clock.systemUTC()).login(u, p);
    }

    private static Producto producto(String codigo, String nombre, TipoVenta tipo, String unidad, long precio,
                                     long costo, long stock) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setTipoVenta(tipo);
        p.setUnidad(unidad);
        p.setPrecioVenta(precio);
        p.setCosto(costo);
        p.setStock(stock);
        p.setStockMinimo(1_000);
        return p;
    }

    private long stock(String codigo) throws Exception {
        return inventario.buscarActivoPorCodigo(codigo).orElseThrow().getStock();
    }

    private Cliente cliente(String nombre) throws Exception {
        return new ClienteService().crearRapido(nombre, null);
    }

    private long saldo(long clienteId) throws Exception {
        return new ClienteService().listarActivos().stream().filter(c -> c.getId() == clienteId)
                .findFirst().orElseThrow().getSaldo();
    }

    // ---------------------------------------------------------------- utilidades de captura

    @Test
    void capturaDelCampoDeCodigo() {
        assertEquals(new Captura("750100", null, null), Captura.parsear(" 750100 "));
        assertEquals(new Captura("750100", "3", null), Captura.parsear("3*750100"));
        assertEquals(new Captura("JAM01", "0.250", null), Captura.parsear("0.250*JAM01"));
        assertEquals(new Captura("JAM01", null, 5000L), Captura.parsear("$50*JAM01"));
        assertThrows(IllegalArgumentException.class, () -> Captura.parsear("3*"));
        assertThrows(IllegalArgumentException.class, () -> Captura.parsear("$0*JAM01"));
    }

    @Test
    void inicialesDeLosMosaicos() {
        assertEquals("CA", Iniciales.de("Café americano"));
        assertEquals("RE", Iniciales.de("Refresco"));
        assertEquals("CC", Iniciales.de("Coca-Cola 600 ml"));
        assertEquals("JD", Iniciales.de("Jamón de pavo"));
        assertEquals("ÉX", Iniciales.de("éxito"));
        int c = Iniciales.color("Café americano");
        assertTrue(c >= 0 && c < Iniciales.COLORES);
        assertEquals(c, Iniciales.color("cafe AMERICANO"), "El color no depende de acentos ni mayúsculas");
    }

    // ---------------------------------------------------------------- carrito

    @Test
    void carritoSumaPiezasYSeparaGranel() {
        Carrito c = new Carrito();
        c.agregar(refresco, 2_000);
        c.agregar(refresco, 1_000);
        c.agregar(jamon, 375);
        c.agregar(jamon, 250);
        assertEquals(3, c.getRenglones().size());
        assertEquals(3_000, c.cantidadDe(refresco.getId()));
        assertEquals(3 * 1800 + 7121 + 4748, c.subtotal()); // 0.375×189.90=71.21; 0.250×189.90=47.475→47.48
        assertEquals(5, c.articulos()); // 3 piezas + 2 renglones a granel
        assertThrows(IllegalArgumentException.class, () -> c.agregar(refresco, 500));
    }

    // ---------------------------------------------------------------- cobrar

    @Test
    void ventaEnEfectivoRestaStockYRegistraCambio() throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, 3_000);
        c.agregar(jamon, 375);
        long total = 3 * 1800 + 7121; // $125.21
        VentaRegistrada v = ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, total, 20000L, null)));
        assertEquals(1, v.folio());
        assertEquals(total, v.total());
        assertEquals(20000 - total, v.cambio());
        assertEquals(7_000, stock("750100"));
        assertEquals(1_625, stock("JAM01"));

        MovimientoInventario m = inventario.kardex(refresco.getId()).get(0);
        assertEquals("VENTA", m.tipo());
        assertEquals(-3_000, m.cantidad());
        assertEquals(v.id(), m.referenciaId());

        List<Venta.Pago> pagos = ventas.pagos(v.id());
        assertEquals(20000L, pagos.get(0).recibido());
        assertEquals(20000L - total, pagos.get(0).cambio());
        assertEquals(total, new ResumenService().vendidoTurno().total());
    }

    @Test
    void foliosConsecutivos() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Carrito c = new Carrito();
            c.agregar(refresco, 1_000);
            assertEquals(i, ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 1800, null, null))).folio());
        }
    }

    @Test
    void pagoMixtoCuadraAlCentavoYFiadoSumaAlSaldo() throws Exception {
        Cliente juan = cliente("Juan Pérez");
        Carrito c = new Carrito();
        c.agregar(refresco, 2_000);
        c.agregar(jamon, 250); // 47.48
        c.setCliente(juan);
        long total = 3600 + 4748; // $83.48
        assertThrows(IllegalArgumentException.class, () -> ventas.cobrar(c, List.of(
                new Pago(MetodoPago.EFECTIVO, 5000, 5000L, null), new Pago(MetodoPago.FIADO, 3347, null, null))),
                "Un centavo de diferencia no cuadra");
        ventas.cobrar(c, List.of(
                new Pago(MetodoPago.EFECTIVO, 5000, 5000L, null),
                new Pago(MetodoPago.TARJETA, 1000, null, "1234"),
                new Pago(MetodoPago.FIADO, total - 6000, null, null)));
        assertEquals(total - 6000, saldo(juan.getId()));
    }

    @Test
    void fiadoSinClienteNoSePermite() {
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.FIADO, 1800, null, null))));
        assertTrue(e.getMessage().contains("cliente"));
    }

    @Test
    void efectivoRecibidoMenorAlTotalSeRechaza() {
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        assertThrows(IllegalArgumentException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 1800, 1000L, null))));
    }

    @Test
    void sinStockSuficienteNoSeVendeYNoQuedaNada() throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, 4_000);
        c.agregar(jamon, 2_500); // solo hay 2 kg
        long total = c.total();
        assertThrows(IllegalArgumentException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, total, null, null))));
        assertEquals(10_000, stock("750100"));
        assertEquals(0, contar("SELECT COUNT(*) FROM ventas"));
        assertEquals(0, contar("SELECT COUNT(*) FROM venta_detalle"));
    }

    @Test
    void descuentoDeCajeroRequiereAutorizacionYRespetaMaximo() throws Exception {
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");
        new TurnoService().continuar(new TurnoService().turnoAbierto().orElseThrow());

        Carrito c = new Carrito();
        c.agregar(refresco, 5_000); // $90.00
        c.setDescuento(900, null);  // 10% sin autorización
        assertThrows(PermisoDenegadoException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 8100, null, null))));

        Autorizacion auth = AuthService.instancia().autorizarAdmin("ana", "secreta1", "Descuento");
        assertThrows(IllegalArgumentException.class,
                () -> AuthService.instancia().autorizarAdmin("luis", "cajero1", "Descuento"), "Un cajero no autoriza");

        c.setDescuento(2700, auth); // 30% > máximo 20%
        assertThrows(IllegalArgumentException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 6300, null, null))));

        c.setDescuento(900, auth);
        VentaRegistrada v = ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 8100, null, null)));
        assertEquals(8100, v.total());
        assertEquals(1, contar("SELECT COUNT(*) FROM ventas WHERE autorizada_por IS NOT NULL"));
    }

    // ---------------------------------------------------------------- cancelación

    @Test
    void cajeroNoCancelaSinAdminYCanceladaNoSuma() throws Exception {
        Cliente juan = cliente("Juan");
        Carrito c = new Carrito();
        c.agregar(refresco, 2_000);
        c.setCliente(juan);
        VentaRegistrada v = ventas.cobrar(c, List.of(new Pago(MetodoPago.FIADO, 3600, null, null)));
        assertEquals(3600, saldo(juan.getId()));
        assertEquals(8_000, stock("750100"));

        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");
        new TurnoService().continuar(new TurnoService().turnoAbierto().orElseThrow());

        assertThrows(PermisoDenegadoException.class, () -> ventas.cancelar(v.id(), "Error de captura", null));
        Autorizacion auth = AuthService.instancia().autorizarAdmin("ana", "secreta1", "Cancelar venta");
        VentaService.Cancelacion res = ventas.cancelar(v.id(), "Error de captura", auth);
        assertFalse(res.saldoAFavor());

        assertEquals(10_000, stock("750100"), "El stock regresa");
        assertEquals(0, saldo(juan.getId()), "El saldo regresa");
        assertEquals(1, contar("SELECT COUNT(*) FROM movimientos_inventario WHERE tipo = 'CANCELACION'"));
        assertEquals(0, new ResumenService().vendidoTurno().total(), "La cancelada no suma");
        Venta.Resumen r = ventas.ventasDelTurno().get(0);
        assertTrue(r.cancelada(), "Aparece listada como cancelada");
        assertEquals("Error de captura", r.motivoCancelacion());
        assertThrows(IllegalArgumentException.class, () -> ventas.cancelar(v.id(), "Otra vez", auth));
    }

    @Test
    void cancelarFiadaConAbonoDejaSaldoAFavor() throws Exception {
        Cliente juan = cliente("Juan");
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        c.setCliente(juan);
        VentaRegistrada v = ventas.cobrar(c, List.of(new Pago(MetodoPago.FIADO, 1800, null, null)));
        // Simula un abono de $10 (la pantalla de abonos llega en la Fase 6).
        try (Connection con = Database.conectar(); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE clientes SET saldo = saldo - 1000 WHERE id = " + juan.getId());
        }
        VentaService.Cancelacion res = ventas.cancelar(v.id(), "Cliente se arrepintió", null);
        assertTrue(res.saldoAFavor());
        assertEquals(-1000, res.saldoCliente());
    }

    @Test
    void noSeCancelanVentasDeTurnoCerrado() throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        VentaRegistrada v = ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 1800, null, null)));
        try (Connection con = Database.conectar(); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE turnos SET estado = 'CERRADO'");
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ventas.cancelar(v.id(), "Error", null));
        assertTrue(e.getMessage().contains("turno ya cortado"));
    }

    // ---------------------------------------------------------------- caja

    @Test
    void retiroDeCajaDelCajeroRequiereAutorizacion() throws Exception {
        CajaService caja = new CajaService();
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");
        new TurnoService().continuar(new TurnoService().turnoAbierto().orElseThrow());

        caja.registrar(com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.ENTRADA, 10000, "Cambio extra", null);
        assertThrows(PermisoDenegadoException.class, () -> caja.registrar(
                com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.RETIRO, 5000, "Proveedor", null));
        Autorizacion auth = AuthService.instancia().autorizarAdmin("ana", "secreta1", "Retiro");
        caja.registrar(com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.RETIRO, 5000, "Proveedor", auth);
        assertEquals(2, contar("SELECT COUNT(*) FROM movimientos_caja"));
        assertThrows(IllegalArgumentException.class, () -> caja.registrar(
                com.fgsistemas.pos.dao.MovimientoCajaDao.Tipo.ENTRADA, 100, " ", null));
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void sinTurnoNoSeVende() {
        Sesion.setTurno(null);
        Carrito c = new Carrito();
        c.agregar(refresco, 1_000);
        assertThrows(IllegalStateException.class,
                () -> ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 1800, null, null))));
        assertNull(Sesion.turno());
    }
}
