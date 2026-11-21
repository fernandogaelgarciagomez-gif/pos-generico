package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.EstadoCuenta;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ClienteService.AbonoRegistrado;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.VentaService.Pago;
import com.fgsistemas.pos.service.VentaService.VentaRegistrada;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 6 (Fiados): venta a fiado, abono parcial, saldo correcto e historial. */
class Fase6Test {

    @TempDir
    Path tmp;

    private final ClienteService clientes = new ClienteService();
    private final VentaService ventas = new VentaService();
    private Producto refresco; // $18.00

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
        new TurnoService().abrir(50000);
        Producto p = new Producto();
        p.setCodigo("750100");
        p.setNombre("Refresco 600 ml");
        p.setTipoVenta(TipoVenta.PIEZA);
        p.setUnidad("pza");
        p.setPrecioVenta(1800);
        p.setCosto(1200);
        p.setStock(100_000);
        p.setStockMinimo(1_000);
        refresco = new InventarioService().crear(p, null);
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    private static void login(String u, String pw) throws Exception {
        new AuthService(Clock.systemUTC()).login(u, pw);
    }

    private VentaRegistrada fiar(Cliente cliente, int piezas, List<Pago> pagos) throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, piezas * 1_000L);
        c.setCliente(cliente);
        return ventas.cobrar(c, pagos);
    }

    private VentaRegistrada fiarTodo(Cliente cliente, int piezas) throws Exception {
        return fiar(cliente, piezas, List.of(new Pago(MetodoPago.FIADO, piezas * 1800L, null, null)));
    }

    private long saldo(long clienteId) throws Exception {
        return clientes.obtener(clienteId).getSaldo();
    }

    /** El criterio principal: saldo guardado = Σ ventas fiadas − Σ abonos. */
    private void verificarSaldo(long clienteId) throws Exception {
        assertEquals(clientes.saldoCalculado(clienteId), saldo(clienteId),
                "Saldo guardado = Σ ventas fiadas − Σ abonos");
        assertEquals(saldo(clienteId), clientes.estadoCuenta(clienteId).saldoFinal(),
                "El estado de cuenta termina en el saldo del cliente");
    }

    private static long contar(String sql) throws Exception {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---------------------------------------------------------------- abonos

    @Test
    void ventaAFiadoAbonoParcialYLiquidacion() throws Exception {
        Cliente juan = clientes.crear("Juan Pérez", "5512345678", "Paga los viernes");
        fiarTodo(juan, 5); // $90.00
        assertEquals(9000, saldo(juan.getId()));
        verificarSaldo(juan.getId());

        AbonoRegistrado a = clientes.abonar(juan.getId(), 4000, MetodoPago.EFECTIVO, null);
        assertEquals(9000, a.saldoAnterior());
        assertEquals(5000, a.saldoNuevo());
        assertEquals(5000, saldo(juan.getId()));
        verificarSaldo(juan.getId());

        clientes.abonar(juan.getId(), 5000, MetodoPago.TARJETA, null); // liquidar todo
        assertEquals(0, saldo(juan.getId()));
        verificarSaldo(juan.getId());
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 100, MetodoPago.EFECTIVO, null), "Sin adeudo no se abona");
    }

    @Test
    void abonosInvalidosSeRechazanSinGuardarNada() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        fiarTodo(juan, 2); // $36.00
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 3601, MetodoPago.EFECTIVO, null), "No más que el saldo");
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 0, MetodoPago.EFECTIVO, null));
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 1000, MetodoPago.FIADO, null), "No se abona a fiado");
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE negocio SET metodo_tarjeta = 0");
        }
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 1000, MetodoPago.TARJETA, null), "Método deshabilitado");
        assertFalse(clientes.metodosAbono().contains(MetodoPago.TARJETA));
        assertEquals(0, contar("SELECT COUNT(*) FROM abonos"));
        assertEquals(3600, saldo(juan.getId()));
    }

    @Test
    void elAbonoQuedaEnElTurnoActualConSaldoAnteriorYNuevo() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        fiarTodo(juan, 3);
        clientes.abonar(juan.getId(), 2000, MetodoPago.EFECTIVO, null);
        long turno = Sesion.turno().getId();
        assertEquals(1, contar("SELECT COUNT(*) FROM abonos WHERE turno_id = " + turno
                + " AND monto = 2000 AND metodo = 'EFECTIVO' AND saldo_anterior = 5400 AND saldo_nuevo = 3400"));
        assertEquals(1, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'ABONO'"));
    }

    @Test
    void cajeroAbonaYCreaClientesPeroNoEditaNiDesactiva() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        fiarTodo(juan, 2);
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");
        new TurnoService().continuar(new TurnoService().turnoAbierto().orElseThrow());

        clientes.abonar(juan.getId(), 1000, MetodoPago.EFECTIVO, null);
        assertEquals(2600, saldo(juan.getId()));
        Cliente nuevo = clientes.crear("María", null, null);
        assertTrue(nuevo.getId() > 0);
        assertThrows(PermisoDenegadoException.class, () -> clientes.editar(juan.getId(), "Otro", null, null));
        assertThrows(PermisoDenegadoException.class, () -> clientes.cambiarActivo(nuevo.getId(), false));
    }

    // ---------------------------------------------------------------- pago mixto, cancelación y saldo a favor

    @Test
    void enPagoMixtoSoloLaParteFiadaEsCargo() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        fiar(juan, 5, List.of(new Pago(MetodoPago.EFECTIVO, 5000, 5000L, null),
                new Pago(MetodoPago.FIADO, 4000, null, null))); // total $90: $50 efectivo + $40 fiado
        assertEquals(4000, saldo(juan.getId()));
        EstadoCuenta ec = clientes.estadoCuenta(juan.getId());
        assertEquals(1, ec.movimientos().size());
        assertEquals(4000, ec.movimientos().get(0).cargo());
        verificarSaldo(juan.getId());
    }

    @Test
    void cancelarVentaYaAbonadaDejaSaldoAFavorYLaSiguienteVentaLoUsa() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        VentaRegistrada v = fiarTodo(juan, 5); // debe $90
        clientes.abonar(juan.getId(), 6000, MetodoPago.EFECTIVO, null); // debe $30
        VentaService.Cancelacion cancel = ventas.cancelar(v.id(), "Cliente se arrepintió", null);
        assertTrue(cancel.saldoAFavor());
        assertEquals(-6000, saldo(juan.getId()), "Le quedan $60 a favor");
        verificarSaldo(juan.getId());

        EstadoCuenta ec = clientes.estadoCuenta(juan.getId());
        assertTrue(ec.movimientos().get(0).cancelada(), "La venta cancelada aparece marcada");
        assertEquals(0, ec.movimientos().get(0).saldo(), "…y no mueve el saldo");
        assertEquals("Cliente: A favor $60.00", "Cliente: " + Cliente.textoSaldo(saldo(juan.getId())));

        assertThrows(IllegalArgumentException.class, () -> clientes.cambiarActivo(juan.getId(), false),
                "Con saldo a favor no se desactiva");
        assertThrows(IllegalArgumentException.class,
                () -> clientes.abonar(juan.getId(), 100, MetodoPago.EFECTIVO, null), "Con saldo a favor no se abona");

        fiarTodo(juan, 2); // $36 a fiado: se descuenta del saldo a favor
        assertEquals(-2400, saldo(juan.getId()));
        fiarTodo(juan, 3); // $54 más: ya debe $30
        assertEquals(3000, saldo(juan.getId()));
        verificarSaldo(juan.getId());
    }

    // ---------------------------------------------------------------- historial

    @Test
    void estadoCuentaMezclaVentasYAbonosConSaldoAcumulado() throws Exception {
        // Las fechas se guardan al segundo: se avanza el reloj para que cada movimiento tenga su hora.
        Instant inicio = Instant.now();
        try {
            Cliente juan = clientes.crearRapido("Juan", null);
            Fechas.setReloj(Clock.fixed(inicio.plusSeconds(60), ZoneId.systemDefault()));
            fiarTodo(juan, 5);                                                   // +90 → 90
            Fechas.setReloj(Clock.fixed(inicio.plusSeconds(120), ZoneId.systemDefault()));
            clientes.abonar(juan.getId(), 5000, MetodoPago.TRANSFERENCIA, null); // −50 → 40
            Fechas.setReloj(Clock.fixed(inicio.plusSeconds(180), ZoneId.systemDefault()));
            fiarTodo(juan, 2);                                                   // +36 → 76
            revisarEstadoCuenta(juan);
        } finally {
            Fechas.setReloj(null);
        }
    }

    private void revisarEstadoCuenta(Cliente juan) throws Exception {
        EstadoCuenta ec = clientes.estadoCuenta(juan.getId());
        List<EstadoCuenta.Movimiento> m = ec.movimientos();
        assertEquals(3, m.size());
        assertEquals(EstadoCuenta.Tipo.VENTA, m.get(0).tipo());
        assertEquals(9000, m.get(0).saldo());
        assertEquals(EstadoCuenta.Tipo.ABONO, m.get(1).tipo());
        assertEquals("Abono (transferencia)", m.get(1).concepto());
        assertEquals(4000, m.get(1).saldo());
        assertEquals(7600, m.get(2).saldo());
        assertTrue(m.get(0).ventaId() != null, "Desde la venta se pueden ver sus productos");
        assertEquals(7600, ec.saldoFinal());
    }

    @Test
    void exportaElEstadoDeCuentaAPdfYExcel() throws Exception {
        Cliente juan = clientes.crear("Juan Pérez", "5512345678", null);
        fiarTodo(juan, 2);
        clientes.abonar(juan.getId(), 1000, MetodoPago.EFECTIVO, null);
        EstadoCuenta ec = clientes.estadoCuenta(juan.getId());

        Negocio n = new Negocio();
        n.setNombre("Mi Tienda");
        Path pdf = new EstadoCuentaExport().pdf(ec, n, tmp.resolve("estado.pdf"));
        byte[] bytes = Files.readAllBytes(pdf);
        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        Path xlsx = new ExportService().excel(tmp.resolve("estado.xlsx"), "Estado de cuenta",
                EstadoCuentaExport.ENCABEZADOS, EstadoCuentaExport.filasExcel(ec));
        assertTrue(Files.size(xlsx) > 0);
        assertEquals("estado_cuenta_Juan_Pérez", EstadoCuentaExport.nombreBase(juan));
    }

    // ---------------------------------------------------------------- administración

    @Test
    void adminEditaYDesactivaSoloConSaldoEnCero() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        fiarTodo(juan, 1);
        clientes.editar(juan.getId(), "Juan Pérez", "5511112222", "Vecino");
        Cliente editado = clientes.obtener(juan.getId());
        assertEquals("Juan Pérez", editado.getNombre());
        assertEquals("5511112222", editado.getTelefono());
        assertEquals(1800, editado.getSaldo(), "Editar no toca el saldo");
        assertThrows(IllegalArgumentException.class, () -> clientes.editar(juan.getId(), " ", null, null));

        assertThrows(IllegalArgumentException.class, () -> clientes.cambiarActivo(juan.getId(), false));
        clientes.abonar(juan.getId(), 1800, MetodoPago.EFECTIVO, null);
        clientes.cambiarActivo(juan.getId(), false);
        assertTrue(clientes.listar(false).stream().noneMatch(c -> c.getId() == juan.getId()));
        assertTrue(clientes.listar(true).stream().anyMatch(c -> c.getId() == juan.getId() && !c.isActivo()));
        assertThrows(IllegalArgumentException.class, () -> fiarTodo(juan, 1), "Inactivo no compra a fiado");
        clientes.cambiarActivo(juan.getId(), true);
        assertTrue(clientes.listar(false).stream().anyMatch(c -> c.getId() == juan.getId()));
    }

    @Test
    void laListaTraeUltimaCompraYUltimoAbono() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        Cliente ana = clientes.crearRapido("Ana", null);
        fiarTodo(juan, 2);
        clientes.abonar(juan.getId(), 1000, MetodoPago.EFECTIVO, null);
        List<Cliente> lista = clientes.listar(false);
        Cliente j = lista.stream().filter(c -> c.getId() == juan.getId()).findFirst().orElseThrow();
        Cliente a = lista.stream().filter(c -> c.getId() == ana.getId()).findFirst().orElseThrow();
        assertTrue(j.getUltimaCompra() != null && j.getUltimoAbono() != null);
        assertTrue(a.getUltimaCompra() == null && a.getUltimoAbono() == null);
        assertEquals("Ana", lista.get(0).getNombre(), "Orden alfabético");
    }
}
