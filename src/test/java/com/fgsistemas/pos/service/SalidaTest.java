package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.SalidaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salida sin cobro: baja stock, no toca la caja, queda en kardex y reporte. */
class SalidaTest {

    @TempDir
    Path tmp;

    private final SalidaService salidas = new SalidaService();
    private Producto cafe; // $25.00, costo $10.00, 20 pza

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        new AuthService(Clock.systemUTC()).login("ana", "secreta1");
        new TurnoService().abrir(50_000);
        Producto p = new Producto();
        p.setCodigo("CAFE1");
        p.setNombre("Café en lata");
        p.setTipoVenta(TipoVenta.PIEZA);
        p.setUnidad("pza");
        p.setPrecioVenta(2_500);
        p.setCosto(1_000);
        p.setStock(20_000);
        p.setStockMinimo(1_000);
        cafe = new InventarioService().crear(p, null);
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    private long stock() throws Exception {
        return new InventarioService().buscarActivoPorCodigo("CAFE1").orElseThrow().getStock();
    }

    private static int contar(String sql) throws Exception {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void sacarSinCobrarBajaStockYNoTocaLaCaja() throws Exception {
        long efectivoAntes = new CajaService().efectivoDisponible();
        Carrito c = new Carrito();
        c.agregar(cafe, 2_000);
        c.agregar(cafe, 1_000); // mismo producto en dos renglones: se suma

        SalidaService.SalidaRegistrada s = salidas.registrar(c, "  Uso interno  ");

        assertEquals(17_000, stock());
        assertEquals(1, s.productos());
        assertEquals(3_000, s.costo());
        assertEquals(7_500, s.valorVenta());
        assertEquals(efectivoAntes, new CajaService().efectivoDisponible(), "no entra ni sale dinero");
        assertEquals(0, contar("SELECT COUNT(*) FROM ventas"), "no es una venta");

        List<MovimientoInventario> kardex = new InventarioService().kardex(cafe.getId());
        MovimientoInventario m = kardex.get(0);
        assertEquals("SALIDA", m.tipo());
        assertEquals(-3_000, m.cantidad());
        assertEquals(17_000, m.stockResultante());
        assertEquals("Uso interno", m.motivo());
        assertEquals(1, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'SALIDA_SIN_COBRO'"));

        List<SalidaDao.Renglon> reporte = salidas.listar(Fechas.ahora().minusDays(1),
                Fechas.ahora().plusDays(1));
        assertEquals(1, reporte.size());
        assertEquals("Ana Admin", reporte.get(0).usuario());
        assertEquals(3_000, reporte.get(0).cantidad());
        assertEquals(1_000, reporte.get(0).costoUnitario());
    }

    @Test
    void pideMotivoYNoDejaSacarMasDeLoQueHay() throws Exception {
        Carrito c = new Carrito();
        c.agregar(cafe, 1_000);
        assertThrows(IllegalArgumentException.class, () -> salidas.registrar(c, "  "));
        assertThrows(IllegalArgumentException.class, () -> salidas.registrar(new Carrito(), "Uso interno"));

        Carrito mucho = new Carrito();
        mucho.agregar(cafe, 21_000);
        assertThrows(IllegalArgumentException.class, () -> salidas.registrar(mucho, "Uso interno"));
        assertEquals(20_000, stock(), "nada cambió");
        assertEquals(0, contar("SELECT COUNT(*) FROM salidas"));
    }

    @Test
    void elCajeroPuedeSacarPeroNoVeElReporte() {
        assertTrue(Permisos.deRol(Rol.CAJERO).contains(Permiso.SALIDA_SIN_COBRO));
        assertFalse(Permisos.deRol(Rol.CAJERO).contains(Permiso.EDITAR_INVENTARIO), "el reporte es del admin");
    }
}
