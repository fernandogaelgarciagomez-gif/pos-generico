package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.AppConfig;
import com.fgsistemas.pos.config.Hardware;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tickets en impresora térmica genérica (USB, serie o de red) instalada en Windows (14-vender.md, paso 5).
 * <p>
 * DECISION: se manda ESC/POS "crudo" a la impresora de Windows elegida en Configuración (javax.print).
 * Funciona con el controlador del fabricante o con "Genérico / Solo texto". No se abre el puerto COM/USB
 * directo para no depender de librerías nativas extra.
 */
public class TicketService {

    /** Un solo hilo: los tickets salen en el orden en que se cobraron y la pantalla no se congela. */
    private static final ExecutorService COLA = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "impresion-tickets");
        t.setDaemon(true);
        return t;
    });

    private final VentaService ventas = new VentaService();
    private final NegocioService negocio = new NegocioService();

    // ------------------------------------------------------------------ impresoras

    /** Nombres de las impresoras instaladas en Windows, en orden alfabético. */
    public static List<String> impresorasInstaladas() {
        PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(servicios).map(PrintService::getName).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static Optional<PrintService> buscar(String nombre) {
        PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(servicios).filter(s -> s.getName().equalsIgnoreCase(nombre.trim())).findFirst();
    }

    /** Manda los bytes a la impresora (en segundo plano). El futuro falla con un mensaje para el usuario. */
    public static CompletableFuture<Void> enviar(String impresora, byte[] datos, String trabajo) {
        return CompletableFuture.runAsync(() -> {
            if (impresora == null || impresora.isBlank()) {
                throw new IllegalStateException("No hay impresora de tickets configurada "
                        + "(Configuración > Ticket y lector).");
            }
            PrintService ps = buscar(impresora).orElseThrow(() -> new IllegalStateException(
                    "No se encontró la impresora \"" + impresora + "\" en Windows. Revisa que esté conectada "
                            + "y encendida, o elige otra en Configuración > Ticket y lector."));
            DocPrintJob job = ps.createPrintJob();
            HashPrintRequestAttributeSet atributos = new HashPrintRequestAttributeSet();
            atributos.add(new JobName(trabajo, Locale.ROOT));
            try {
                job.print(new SimpleDoc(datos, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), atributos);
            } catch (PrintException e) {
                throw new IllegalStateException("La impresora \"" + impresora + "\" no aceptó el ticket: "
                        + e.getMessage(), e);
            }
        }, COLA);
    }

    // ------------------------------------------------------------------ tickets

    /** Ticket de una venta registrada. */
    public EscPos ticketVenta(long ventaId, Hardware hw, boolean reimpresion, boolean abrirCajon)
            throws SQLException {
        Negocio n = negocio.obtener().orElseGet(Negocio::new);
        Venta.Resumen v = ventas.resumen(ventaId);
        return armar(n, v, ventas.renglones(ventaId), ventas.pagos(ventaId), hw, reimpresion, abrirCajon);
    }

    /** Imprime el ticket de una venta (en segundo plano). */
    public CompletableFuture<Void> imprimirVenta(long ventaId, Hardware hw, boolean reimpresion, boolean abrirCajon)
            throws SQLException {
        EscPos t = ticketVenta(ventaId, hw, reimpresion, abrirCajon);
        return enviar(hw.getImpresora(), t.bytes(), "Ticket venta " + ventaId);
    }

    /** Solo abre el cajón (venta en efectivo cuando no se imprime ticket, o botón de prueba). */
    public static CompletableFuture<Void> abrirCajon(Hardware hw) {
        EscPos e = new EscPos(hw.columnas(), hw.isSinAcentos()).abrirCajon();
        return enviar(hw.getImpresora(), e.bytes(), "Abrir cajon");
    }

    /** Ticket de prueba: ancho del papel, acentos, negritas y corte. */
    public EscPos ticketPrueba(Hardware hw) throws SQLException {
        Negocio n = negocio.obtener().orElseGet(Negocio::new);
        EscPos t = new EscPos(hw.columnas(), hw.isSinAcentos());
        t.titulo(vacio(n.getNombre()) ? AppConfig.NOMBRE_APP : n.getNombre());
        t.centradoNegrita("TICKET DE PRUEBA");
        t.centrado(Fechas.FECHA_HORA.format(Fechas.ahora()));
        t.separador();
        t.izquierda("Papel de " + hw.getAnchoPapelMm() + " mm: " + hw.columnas() + " caracteres por renglón.");
        t.izquierda("La regla de abajo debe caber en UN renglón:");
        StringBuilder regla = new StringBuilder();
        for (int i = 1; i <= hw.columnas(); i++) {
            regla.append(i % 10);
        }
        t.izquierda(regla.toString());
        t.separador();
        t.izquierda("Acentos: á é í ó ú Á É Í Ó Ú ñ Ñ ü ¿? ¡!");
        t.izquierda("Si ves símbolos raros, activa \"Imprimir sin acentos\".");
        t.dosColumnas("Producto de ejemplo", Dinero.formatear(12_350));
        t.dosColumnasGrande("TOTAL", Dinero.formatear(12_350));
        t.separador();
        t.centrado("Impresora: " + hw.getImpresora());
        terminar(t, hw, false);
        return t;
    }

    /** Arma el ticket (sin BD): encabezado del negocio, productos, totales, pagos y mensaje. */
    static EscPos armar(Negocio n, Venta.Resumen v, List<Venta.Renglon> renglones, List<Venta.Pago> pagos,
                        Hardware hw, boolean reimpresion, boolean abrirCajon) {
        EscPos t = new EscPos(hw.columnas(), hw.isSinAcentos());
        if (abrirCajon) {
            t.abrirCajon(); // primero, para que el cajón abra mientras sale el papel
        }
        t.titulo(vacio(n.getNombre()) ? AppConfig.NOMBRE_APP : n.getNombre());
        if (!vacio(n.getDireccion())) {
            t.centrado(n.getDireccion());
        }
        if (!vacio(n.getTelefono())) {
            t.centrado("Tel. " + n.getTelefono());
        }
        if (!vacio(n.getRfc())) {
            t.centrado("RFC: " + n.getRfc());
        }
        t.separador();
        t.dosColumnas("Folio #" + v.folio(), Fechas.FECHA_HORA.format(v.fecha()));
        t.izquierda("Atendió: " + v.usuarioNombre());
        if (!vacio(v.clienteNombre())) {
            t.izquierda("Cliente: " + v.clienteNombre());
        }
        if (reimpresion) {
            t.centradoNegrita("*** REIMPRESIÓN ***");
        }
        if (v.cancelada()) {
            t.centradoNegrita("*** VENTA CANCELADA ***");
        }
        t.separador();

        long subtotal = 0;
        long articulos = 0; // piezas; un producto a granel cuenta como 1
        for (Venta.Renglon r : renglones) {
            subtotal += r.importe();
            articulos += r.tipoVenta().aceptaDecimales() ? 1 : r.cantidad() / Cantidad.UNO;
            t.izquierda(r.nombre());
            t.dosColumnas("  " + r.cantidadTexto() + " x " + Dinero.formatear(r.precioUnitario()),
                    Dinero.formatear(r.importe()));
        }
        t.separador();
        long descuento = subtotal - v.total();
        if (descuento > 0) {
            t.dosColumnas("Subtotal", Dinero.formatear(subtotal));
            t.dosColumnas("Descuento", "-" + Dinero.formatear(descuento));
        }
        t.dosColumnasGrande("TOTAL", Dinero.formatear(v.total()));
        for (Venta.Pago p : pagos) {
            if (p.metodo() == MetodoPago.EFECTIVO && p.recibido() != null) {
                t.dosColumnas("Efectivo recibido", Dinero.formatear(p.recibido()));
                if (p.cambio() != null && p.cambio() > 0) {
                    t.dosColumnas("Su cambio", Dinero.formatear(p.cambio()));
                }
            } else {
                t.dosColumnas(p.metodo().etiqueta() + (vacio(p.referencia()) ? "" : " ref. " + p.referencia()),
                        Dinero.formatear(p.monto()));
            }
        }
        t.izquierda(articulos == 1 ? "1 artículo" : articulos + " artículos");
        t.separador();
        if (!vacio(n.getMensajeTicket())) {
            t.centrado(n.getMensajeTicket());
        }
        terminar(t, hw, true);
        return t;
    }

    private static void terminar(EscPos t, Hardware hw, boolean leyendaFiscal) {
        if (leyendaFiscal) {
            t.centrado("Este ticket no es un comprobante fiscal");
        }
        if (hw.isCortarPapel()) {
            t.cortar();
        } else {
            t.avanzar();
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
