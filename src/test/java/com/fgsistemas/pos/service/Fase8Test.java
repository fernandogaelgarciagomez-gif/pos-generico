package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.CategoriaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.service.ExportService.Hoja;
import com.fgsistemas.pos.service.VentaService.Pago;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 8: configuración del negocio, categorías y respaldos. */
class Fase8Test {

    @TempDir
    Path tmp;

    private final NegocioService negocio = new NegocioService();
    private final RespaldoService respaldos = new RespaldoService();
    private final InventarioService inventario = new InventarioService();

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
        Fechas.setReloj(Clock.systemDefaultZone());
    }

    private static void login(String u, String pw) throws Exception {
        new AuthService(Clock.systemUTC()).login(u, pw);
    }

    private Negocio actual() throws Exception {
        return negocio.obtener().orElseThrow();
    }

    private Producto crearProducto(String codigo, String categoria) throws Exception {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre("Producto " + codigo);
        p.setTipoVenta(TipoVenta.PIEZA);
        p.setUnidad("pza");
        p.setPrecioVenta(1000);
        p.setCosto(600);
        p.setStock(10_000);
        p.setStockMinimo(1_000);
        return inventario.crear(p, categoria);
    }

    private static int contar(String sql) throws Exception {
        try (Connection c = Database.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ------------------------------------------------------------------ configuración

    @Test
    void guardarConfiguracionSeReflejaEnTodo() throws Exception {
        Negocio n = actual();
        n.setNombre("  Papelería La Estrella ");
        n.setRfc("xaxx010101000");
        n.setStockMinimoDefault(2_500);
        n.setMetodoTarjeta(false);
        n.setMetodoFiado(false);
        n.setDescuentoMaximoPct(10);
        n.setDiasConservarRespaldos(15);
        negocio.guardar(n);

        Negocio guardado = actual();
        assertEquals("Papelería La Estrella", guardado.getNombre());
        assertEquals("XAXX010101000", guardado.getRfc());
        assertEquals(2_500, inventario.stockMinimoDefault());
        assertEquals(15, guardado.getDiasConservarRespaldos());
        // Deshabilitar un método de pago lo quita del cobro.
        assertFalse(new VentaService().metodosHabilitados().contains(MetodoPago.TARJETA));
        assertFalse(new VentaService().metodosHabilitados().contains(MetodoPago.FIADO));
        assertTrue(new VentaService().metodosHabilitados().contains(MetodoPago.EFECTIVO));
        assertEquals(1, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'CONFIGURACION'"));
    }

    @Test
    void guardarSinCambiosNoEnsuciaLaBitacora() throws Exception {
        negocio.guardar(actual());
        assertEquals(0, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'CONFIGURACION'"));
    }

    @Test
    void validaLosDatosDeConfiguracion() throws Exception {
        Negocio sinNombre = actual();
        sinNombre.setNombre(" ");
        assertThrows(IllegalArgumentException.class, () -> negocio.guardar(sinNombre));
        Negocio descuento = actual();
        descuento.setDescuentoMaximoPct(101);
        assertThrows(IllegalArgumentException.class, () -> negocio.guardar(descuento));
        Negocio dias = actual();
        dias.setDiasConservarRespaldos(0);
        assertThrows(IllegalArgumentException.class, () -> negocio.guardar(dias));
        assertEquals("Mi Tienda", actual().getNombre());
    }

    @Test
    void elCajeroNoPuedeCambiarLaConfiguracion() throws Exception {
        new UsuarioService().crear("Carlos Cajero", "carlos", "secreta2", "secreta2", Rol.CAJERO);
        Sesion.cerrar();
        login("carlos", "secreta2");
        Negocio n = actual();
        n.setNombre("Otro");
        assertThrows(PermisoDenegadoException.class, () -> negocio.guardar(n));
        assertThrows(PermisoDenegadoException.class, respaldos::respaldarAhora);
    }

    @Test
    void logoSeCopiaALaCarpetaDeDatosYSePuedeQuitar() throws Exception {
        Path imagen = tmp.resolve("mi logo.PNG");
        Files.write(imagen, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        negocio.cambiarLogo(imagen);
        Optional<Path> logo = NegocioService.archivoLogo(actual());
        assertTrue(logo.isPresent());
        assertEquals(tmp.resolve("img").resolve("logo.png"), logo.get());

        assertThrows(IllegalArgumentException.class, () -> negocio.cambiarLogo(tmp.resolve("pos.db")));

        negocio.quitarLogo();
        assertTrue(NegocioService.archivoLogo(actual()).isEmpty());
        assertFalse(Files.exists(tmp.resolve("img").resolve("logo.png")));
    }

    // ------------------------------------------------------------------ categorías

    @Test
    void categoriasSeRenombranYSoloSeQuitanVacias() throws Exception {
        crearProducto("A1", "Bebidas");
        inventario.crearCategoria("Dulces");
        List<CategoriaDao.ConConteo> lista = inventario.categoriasConConteo();
        CategoriaDao.ConConteo bebidas = lista.stream().filter(c -> c.nombre().equals("Bebidas")).findFirst().orElseThrow();
        CategoriaDao.ConConteo dulces = lista.stream().filter(c -> c.nombre().equals("Dulces")).findFirst().orElseThrow();
        assertEquals(1, bebidas.productos());

        assertThrows(IllegalArgumentException.class, () -> inventario.renombrarCategoria(dulces.id(), "bebidas"));
        inventario.renombrarCategoria(bebidas.id(), "Refrescos");
        assertTrue(inventario.categorias().contains("Refrescos"));
        assertEquals("Refrescos", inventario.buscarActivoPorCodigo("A1").orElseThrow().getCategoriaNombre());

        assertThrows(IllegalStateException.class, () -> inventario.quitarCategoria(bebidas.id()));
        inventario.quitarCategoria(dulces.id());
        assertFalse(inventario.categorias().contains("Dulces"));
    }

    // ------------------------------------------------------------------ respaldos

    @Test
    void respaldoAutomaticoUnaVezAlDia() throws Exception {
        assertTrue(respaldos.automaticoAlAbrir().isPresent());
        assertTrue(respaldos.automaticoAlAbrir().isEmpty(), "ya hay respaldo de hoy");
        assertEquals(1, respaldos.listar().size());
        assertTrue(Files.isDirectory(tmp.resolve("respaldos")));
    }

    @Test
    void respaldoAutomaticoApagadoNoRespalda() throws Exception {
        Negocio n = actual();
        n.setRespaldoAutomatico(false);
        negocio.guardar(n);
        assertTrue(respaldos.automaticoAlAbrir().isEmpty());
        assertTrue(respaldos.automaticoDespuesDeCorte().isEmpty());
    }

    @Test
    void elCorteHaceRespaldo() throws Exception {
        new TurnoService().abrir(10_000);
        new CorteService().cerrarTurno(Sesion.turno().getId(), 10_000, null, null);
        assertTrue(respaldos.listar().stream().anyMatch(r -> r.nombre().contains("_corte")));
    }

    @Test
    void seConservanSoloLosUltimosNDias() throws Exception {
        Path carpeta = Files.createDirectories(tmp.resolve("respaldos"));
        Files.writeString(carpeta.resolve("pos_2020-01-01_0900.db"), "viejo");
        Files.writeString(carpeta.resolve("pos_2020-01-02_0900.db"), "viejo");
        Files.writeString(carpeta.resolve("notas.db"), "no es un respaldo");
        LocalDate hoy = LocalDate.of(2026, 11, 22);
        Fechas.setReloj(Clock.fixed(hoy.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
        Files.writeString(carpeta.resolve("pos_2026-11-10_0900.db"), "reciente");

        respaldos.respaldarAhora(); // hoy; luego limpia con 30 días

        List<RespaldoService.Respaldo> quedan = respaldos.listar();
        assertEquals(2, quedan.size());
        assertTrue(quedan.get(0).nombre().startsWith("pos_2026-11-22_1000"));
        assertEquals("pos_2026-11-10_0900.db", quedan.get(1).nombre());
        assertTrue(Files.exists(carpeta.resolve("notas.db")), "no toca archivos ajenos");
    }

    @Test
    void nuncaBorraElUnicoRespaldo() throws Exception {
        Path carpeta = Files.createDirectories(tmp.resolve("respaldos"));
        Files.writeString(carpeta.resolve("pos_2020-01-01_0900.db"), "viejo");
        assertEquals(0, respaldos.limpiar(30));
        assertTrue(Files.exists(carpeta.resolve("pos_2020-01-01_0900.db")));
    }

    @Test
    void restaurarDejaElSistemaExactamenteEnEseEstado() throws Exception {
        crearProducto("A1", null);
        Path respaldo = respaldos.respaldarAhora();
        crearProducto("B2", null);
        Negocio n = actual();
        n.setNombre("Nombre después del respaldo");
        negocio.guardar(n);

        assertThrows(IllegalArgumentException.class, () -> respaldos.restaurar(respaldo, "restaura"));
        assertEquals(2, contar("SELECT COUNT(*) FROM productos"));

        Path seguridad = respaldos.restaurar(respaldo, "RESTAURAR");

        assertEquals(1, contar("SELECT COUNT(*) FROM productos"));
        assertEquals("Mi Tienda", actual().getNombre());
        assertTrue(Files.exists(seguridad), "respalda el estado anterior antes de restaurar");
        assertTrue(seguridad.getFileName().toString().contains("antes-de-restaurar"));
        assertEquals(1, contar("SELECT COUNT(*) FROM bitacora WHERE accion = 'RESTAURAR_RESPALDO'"));

        // El respaldo de seguridad trae el estado de antes (2 productos).
        Sesion.cerrar();
        login("ana", "secreta1");
        respaldos.restaurar(seguridad, "restaurar");
        assertEquals(2, contar("SELECT COUNT(*) FROM productos"));
    }

    @Test
    void noRestauraArchivosQueNoSonRespaldos() throws Exception {
        Path basura = tmp.resolve("pos_2026-01-01_0000.db");
        Files.writeString(basura, "esto no es una base de datos");
        crearProducto("A1", null);
        assertThrows(IllegalArgumentException.class, () -> respaldos.restaurar(basura, "RESTAURAR"));
        assertEquals(1, contar("SELECT COUNT(*) FROM productos"));
        assertFalse(Files.exists(tmp.resolve("pos.db.restaurando")));
    }

    @Test
    void respaldarEnOtraUbicacion() throws Exception {
        Path usb = Files.createDirectories(tmp.resolve("usb"));
        Path archivo = respaldos.respaldarEn(usb);
        assertEquals(usb, archivo.getParent());
        assertTrue(Files.size(archivo) > 0);
    }

    @Test
    void exportarTodoTraeLasCuatroHojas() throws Exception {
        Producto p = crearProducto("A1", null);
        new TurnoService().abrir(10_000);
        Carrito c = new Carrito();
        c.agregar(p, 2_000);
        new VentaService().cobrar(c, List.of(new Pago(MetodoPago.EFECTIVO, 2_000, 2_000L, null)));

        List<Hoja> hojas = respaldos.hojasExportarTodo(Fechas.ahora().toLocalDate());
        assertEquals(List.of("Productos", "Clientes", "Ventas del mes", "Cortes del mes"),
                hojas.stream().map(Hoja::nombre).toList());
        assertEquals(1, hojas.get(0).filas().size());
        assertEquals(1, hojas.get(2).filas().size());
    }
}
