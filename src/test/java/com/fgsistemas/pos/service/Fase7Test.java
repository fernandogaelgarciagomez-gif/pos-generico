package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.ResumenCaja;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.model.Turno;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 7: el corte cuadra fondo + efectivo + abonos + entradas − retiros contra lo contado. */
class Fase7Test {

    @TempDir
    Path tmp;

    private final VentaService ventas = new VentaService();
    private final CorteService cortes = new CorteService();
    private final ClienteService clientes = new ClienteService();
    private final CajaService caja = new CajaService();
    private Producto refresco; // precio $18.00, costo $12.00

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
        new TurnoService().abrir(50000); // fondo $500.00
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

    private VentaRegistrada vender(int piezas, Cliente cliente, Pago... pagos) throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, piezas * 1_000L);
        c.setCliente(cliente);
        return ventas.cobrar(c, List.of(pagos));
    }

    /**
     * Turno de prueba con todo lo que mueve la caja:
     * efectivo con cambio, mixto (efectivo + tarjeta + fiado), transferencia, una cancelada,
     * abonos en efectivo y tarjeta, una entrada y un retiro.
     */
    private void turnoCompleto() throws Exception {
        Cliente juan = clientes.crearRapido("Juan", null);
        vender(1, null, new Pago(MetodoPago.EFECTIVO, 1800, 2000L, null));            // $18 (cambio $2)
        vender(5, juan, new Pago(MetodoPago.EFECTIVO, 5000, 5000L, null),             // $90 mixto
                new Pago(MetodoPago.TARJETA, 1000, null, "1234"),
                new Pago(MetodoPago.FIADO, 3000, null, null));
        vender(2, null, new Pago(MetodoPago.TRANSFERENCIA, 3600, null, "ref"));       // $36
        VentaRegistrada cancelada = vender(3, null, new Pago(MetodoPago.EFECTIVO, 5400, null, null)); // $54
        ventas.cancelar(cancelada.id(), "Error de captura", null);
        clientes.abonar(juan.getId(), 1000, MetodoPago.EFECTIVO, null);
        clientes.abonar(juan.getId(), 500, MetodoPago.TARJETA, null);
        caja.registrar(MovimientoCajaDao.Tipo.ENTRADA, 2000, "Cambio del banco", null);
        caja.registrar(MovimientoCajaDao.Tipo.RETIRO, 4500, "Pago a proveedor", null);
    }

    private static final long ESPERADO = 50000 + 1800 + 5000 + 1000 + 2000 - 4500; // $553.00

    // ---------------------------------------------------------------- fórmula

    @Test
    void elResumenCalculaLaFormulaExactaAlCentavo() throws Exception {
        turnoCompleto();
        ResumenCaja r = cortes.resumenTurnoActual();
        assertEquals(50000, r.fondo());
        assertEquals(6800, r.ventas(MetodoPago.EFECTIVO), "Efectivo = monto exacto, sin el cambio ni la cancelada");
        assertEquals(1000, r.ventas(MetodoPago.TARJETA));
        assertEquals(3600, r.ventas(MetodoPago.TRANSFERENCIA));
        assertEquals(3000, r.ventas(MetodoPago.FIADO));
        assertEquals(2, r.numPorMetodo(MetodoPago.EFECTIVO));
        assertEquals(1000, r.abonos(MetodoPago.EFECTIVO));
        assertEquals(500, r.abonosOtros());
        assertEquals(2000, r.entradas());
        assertEquals(4500, r.retiros());
        assertEquals(ESPERADO, r.efectivoEsperado());
        assertEquals(3, r.numVentas(), "Las canceladas no cuentan");
        assertEquals(14400, r.totalVendido());
        assertEquals(4800, r.ticketPromedio());
        assertEquals(1, r.numCanceladas());
        assertEquals(5400, r.montoCanceladas());
        assertEquals(8 * 600, r.ganancia(), "Ganancia = (18 − 12) × 8 piezas vendidas");
        assertEquals("750100", r.top().get(0).codigo());
        assertEquals(8_000, r.top().get(0).cantidad());
        assertEquals(14400, java.util.Arrays.stream(r.porHora()).sum());
    }

    @Test
    void laGananciaRestaLosDescuentos() throws Exception {
        Carrito c = new Carrito();
        c.agregar(refresco, 10_000); // $180, ganancia bruta $60
        c.setDescuento(1000, null);  // descuento $10 (el admin no necesita autorización)
        ventas.cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 17000, null, null)));
        assertEquals(5000, cortes.resumenTurnoActual().ganancia());
    }

    // ---------------------------------------------------------------- corte

    @Test
    void elCorteCuadraYCierraElTurno() throws Exception {
        turnoCompleto();
        long turnoId = Sesion.turno().getId();
        ResumenCaja antes = cortes.resumenTurnoActual();

        Corte previa = cortes.vistaPrevia(turnoId, 55000, null);
        assertEquals(ESPERADO, previa.efectivoEsperado());
        assertEquals(-300, previa.diferencia(), "Faltan $3.00");
        assertEquals(0, previa.id(), "La vista previa no guarda nada");

        assertThrows(IllegalArgumentException.class, () -> cortes.cerrarTurno(turnoId, 55000, " ", null),
                "Con diferencia, la nota es obligatoria");
        Corte k = cortes.cerrarTurno(turnoId, 55000, "Faltó cambio", null);

        assertTrue(k.id() > 0);
        assertEquals(antes.efectivoEsperado(), k.efectivoEsperado(), "El resumen coincide con el corte");
        assertEquals(antes.totalVendido(), k.totalVendido());
        assertEquals(antes.ganancia(), k.ganancia());
        assertEquals(6800, k.ventasEfectivo());
        assertEquals(1000, k.ventasTarjeta());
        assertEquals(3600, k.ventasTransferencia());
        assertEquals(3000, k.ventasFiado());
        assertEquals(1000, k.abonosEfectivo());
        assertEquals(500, k.abonosOtros());
        assertEquals(55000, k.efectivoContado());
        assertEquals(-300, k.diferencia());
        assertEquals(3, k.numVentas());
        assertEquals(1, k.numCanceladas());
        assertEquals(5400, k.montoCanceladas());
        assertEquals("Faltó cambio", k.notas());

        assertNull(Sesion.turno(), "Ya no hay turno en la sesión");
        assertTrue(new TurnoService().turnoAbierto().isEmpty(), "El siguiente login pide fondo nuevo");
        assertThrows(IllegalStateException.class, () -> cortes.cerrarTurno(turnoId, 55000, "otra vez", null));
        assertEquals(k, cortes.obtener(k.id()), "El detalle coincide con lo que se vio al hacer el corte");
        // Total con todo y fondo = en caja + tarjeta + transferencia + abonos con tarjeta (sin fiado)
        assertEquals(ESPERADO + 1000 + 3600 + 500, k.totalConFondo());
    }

    @Test
    void losGastosEIngresosEntranAlCorte() throws Exception {
        caja.registrar(MovimientoCajaDao.Tipo.RETIRO, 15000, "Servicios (luz, agua, internet): recibo de luz", null);
        caja.registrar(MovimientoCajaDao.Tipo.ENTRADA, 5000, "Cambio o morralla", null);
        assertEquals(2, caja.movimientosTurno().size());
        ResumenCaja r = cortes.resumenTurnoActual();
        assertEquals(15000, r.retiros());
        assertEquals(5000, r.entradas());
        assertEquals(50000 - 15000 + 5000, r.efectivoEsperado());
    }

    @Test
    void sinDiferenciaNoPideNota() throws Exception {
        turnoCompleto();
        Corte k = cortes.cerrarTurno(Sesion.turno().getId(), ESPERADO, null, null);
        assertEquals(0, k.diferencia());
        assertEquals("Cuadra", CorteExport.textoDiferencia(k.diferencia()));
        assertEquals("Sobran $1.50", CorteExport.textoDiferencia(150));
        assertEquals("Faltan $3.00", CorteExport.textoDiferencia(-300));
    }

    @Test
    void contadoNegativoSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> cortes.vistaPrevia(Sesion.turno().getId(), -1, null));
    }

    // ---------------------------------------------------------------- permisos

    @Test
    void cajeroVeSuTurnoSinGananciaYNoVeElDiaNiCortesPasados() throws Exception {
        turnoCompleto();
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");
        new TurnoService().continuar(new TurnoService().turnoAbierto().orElseThrow());

        ResumenCaja r = cortes.resumenTurnoActual();
        assertNull(r.ganancia(), "El cajero no recibe la ganancia");
        assertEquals(ESPERADO, r.efectivoEsperado());
        assertThrows(PermisoDenegadoException.class, () -> cortes.resumenDia(LocalDate.now()));
        assertThrows(PermisoDenegadoException.class,
                () -> cortes.listar(LocalDate.now(), LocalDate.now(), null));

        Corte k = cortes.cerrarTurno(Sesion.turno().getId(), ESPERADO, null, null);
        assertNull(k.ganancia());
    }

    @Test
    void corteDeTurnoAjenoPideAutorizacionAlCajero() throws Exception {
        turnoCompleto();
        Turno deAna = new TurnoService().turnoAbierto().orElseThrow();
        new UsuarioService().crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1"); // no continúa el turno: es ajeno

        assertThrows(PermisoDenegadoException.class, () -> cortes.vistaPrevia(deAna.getId(), ESPERADO, null));
        assertThrows(PermisoDenegadoException.class,
                () -> cortes.cerrarTurno(deAna.getId(), ESPERADO, null, null));
        Autorizacion auth = AuthService.instancia().autorizarAdmin("ana", "secreta1", "Corte de turno ajeno");
        Corte k = cortes.cerrarTurno(deAna.getId(), ESPERADO, null, auth);
        assertEquals("Luis", k.usuarioNombre());
        assertEquals("Ana Admin", k.turnoUsuarioNombre());

        new TurnoService().abrir(30000); // y abre su propio turno
        assertEquals(30000, cortes.resumenTurnoActual().fondo());
        assertEquals(0, cortes.resumenTurnoActual().numVentas(), "El turno nuevo empieza en cero");
    }

    // ---------------------------------------------------------------- todo el día y cortes pasados

    @Test
    void todoElDiaSumaLosTurnosDeHoyYCortesPasadosFiltra() throws Exception {
        turnoCompleto();
        cortes.cerrarTurno(Sesion.turno().getId(), ESPERADO, null, null);
        new TurnoService().abrir(20000);
        vender(1, null, new Pago(MetodoPago.EFECTIVO, 1800, null, null));

        LocalDate hoy = Fechas.ahora().toLocalDate();
        ResumenCaja dia = cortes.resumenDia(hoy);
        assertEquals(4, dia.numVentas());
        assertEquals(14400 + 1800, dia.totalVendido());
        assertEquals(70000, dia.fondo(), "Fondos de los dos turnos");

        List<Corte> lista = cortes.listar(hoy, hoy, null);
        assertEquals(1, lista.size());
        assertEquals(ESPERADO, lista.get(0).efectivoEsperado());
        assertTrue(cortes.listar(hoy.minusDays(10), hoy.minusDays(1), null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> cortes.listar(hoy, hoy.minusDays(1), null));

        CorteService.DetalleTurno d = cortes.detalle(lista.get(0).turnoId());
        assertEquals(4, d.ventas().size(), "Incluye la cancelada");
        assertEquals(2, d.movimientos().size());
        assertEquals(2, d.abonos().size());
        assertEquals("Juan", d.abonos().get(0).clienteNombre());
    }

    @Test
    void exportaElCorteAPdfYExcel() throws Exception {
        turnoCompleto();
        long turnoId = Sesion.turno().getId();
        Corte k = cortes.cerrarTurno(turnoId, 55000, "Faltó cambio", null);
        Negocio n = new Negocio();
        n.setNombre("Mi Tienda");
        Path pdf = new CorteExport().pdf(k, n, tmp.resolve("corte.pdf"));
        byte[] bytes = Files.readAllBytes(pdf);
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        Path xlsx = new ExportService().excel(tmp.resolve("corte.xlsx"),
                CorteExport.hojas(k, cortes.detalle(turnoId).ventas()));
        assertTrue(Files.size(xlsx) > 0);
        assertTrue(CorteExport.lineas(k).stream().anyMatch(l -> l.concepto().contains("EFECTIVO ESPERADO")
                && l.monto() == ESPERADO));
    }
}
