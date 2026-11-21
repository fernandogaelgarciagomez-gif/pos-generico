package com.fgsistemas.pos.service;

import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.service.ExportService.Hoja;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renglones del corte (Paso 2 de 18-corte-de-caja.md) y su exportación a PDF y Excel.
 * La pantalla, el PDF y el Excel usan los mismos renglones, así siempre dicen lo mismo.
 */
public class CorteExport {

    /** Estilo de un renglón. */
    public enum Estilo { NORMAL, TOTAL, DIFERENCIA, SECCION, NOTA }

    /** Renglón: concepto y monto (null en títulos de sección y notas). */
    public record Linea(String concepto, Long monto, Estilo estilo) {
    }

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final Color AZUL_MARINO = new Color(0x0B, 0x1A, 0x33);
    private static final Color GRIS = new Color(0x55, 0x5F, 0x6D);
    private static final Color ROJO = new Color(0xC6, 0x28, 0x28);
    private static final Color VERDE = new Color(0x15, 0x80, 0x3D);

    public static List<Linea> lineas(Corte k) {
        List<Linea> l = new ArrayList<>();
        l.add(new Linea("Fondo inicial", k.fondoInicial(), Estilo.NORMAL));
        l.add(new Linea("+ Ventas en efectivo", k.ventasEfectivo(), Estilo.NORMAL));
        l.add(new Linea("+ Abonos en efectivo", k.abonosEfectivo(), Estilo.NORMAL));
        l.add(new Linea("+ Entradas de caja", k.entradasCaja(), Estilo.NORMAL));
        l.add(new Linea("− Retiros de caja", k.retirosCaja(), Estilo.NORMAL));
        l.add(new Linea("= EFECTIVO ESPERADO", k.efectivoEsperado(), Estilo.TOTAL));
        l.add(new Linea("Efectivo contado", k.efectivoContado(), Estilo.NORMAL));
        l.add(new Linea("DIFERENCIA", k.diferencia(), Estilo.DIFERENCIA));
        l.add(new Linea("Otros (no están en caja)", null, Estilo.SECCION));
        l.add(new Linea("Tarjeta", k.ventasTarjeta(), Estilo.NORMAL));
        l.add(new Linea("Transferencia", k.ventasTransferencia(), Estilo.NORMAL));
        l.add(new Linea("Vendido a fiado", k.ventasFiado(), Estilo.NORMAL));
        l.add(new Linea("Abonos tarjeta/transf.", k.abonosOtros(), Estilo.NORMAL));
        l.add(new Linea("Total vendido", k.totalVendido(), Estilo.TOTAL));
        l.add(new Linea("TOTAL CON TODO Y FONDO (sin fiado)", k.totalConFondo(), Estilo.TOTAL));
        l.add(new Linea("Ventas: " + k.numVentas() + " · Canceladas: " + k.numCanceladas() + " ("
                + Dinero.formatear(k.montoCanceladas()) + ")", null, Estilo.NOTA));
        if (k.ganancia() != null) {
            l.add(new Linea("Ganancia estimada", k.ganancia(), Estilo.NORMAL));
        }
        return l;
    }

    /** Texto de la diferencia: "Cuadra", "Sobran $X" o "Faltan $X". */
    public static String textoDiferencia(long diferencia) {
        if (diferencia == 0) {
            return "Cuadra";
        }
        return (diferencia > 0 ? "Sobran " : "Faltan ") + Dinero.formatear(Math.abs(diferencia));
    }

    public static String encabezado(Corte k) {
        return "Turno de " + k.turnoUsuarioNombre() + " · abierto " + Fechas.FECHA_HORA.format(k.abiertoEn())
                + (k.fecha() == null ? "" : " · corte " + Fechas.FECHA_HORA.format(k.fecha()))
                + (k.usuarioNombre() == null || k.usuarioNombre().equals(k.turnoUsuarioNombre())
                ? "" : " por " + k.usuarioNombre());
    }

    public static String nombreBase(Corte k) {
        return "corte_" + (k.id() > 0 ? k.id() + "_" : "") + "turno_" + k.turnoId();
    }

    // ---------------------------------------------------------------- Excel

    /** Hoja "Corte" (concepto / monto) y, si se dan, hoja "Ventas" del turno. */
    public static List<Hoja> hojas(Corte k, List<Venta.Resumen> ventas) {
        List<List<Object>> filas = new ArrayList<>();
        filas.add(List.of(encabezado(k), ""));
        for (Linea l : lineas(k)) {
            List<Object> f = new ArrayList<>();
            f.add(l.concepto());
            f.add(l.monto() == null ? null : new Monto(l.monto()));
            filas.add(f);
        }
        if (k.notas() != null) {
            filas.add(List.of("Notas: " + k.notas(), ""));
        }
        List<Hoja> hojas = new ArrayList<>();
        hojas.add(new Hoja("Corte", List.of("Concepto", "Monto"), filas));
        if (ventas != null) {
            List<List<Object>> fv = new ArrayList<>();
            for (Venta.Resumen v : ventas) {
                fv.add(List.of(v.folio(), Fechas.FECHA_HORA.format(v.fecha()), v.usuarioNombre(),
                        v.clienteNombre() == null ? "" : v.clienteNombre(), v.metodos(), new Monto(v.total()),
                        v.cancelada() ? "Cancelada" : "Completada"));
            }
            hojas.add(new Hoja("Ventas", List.of("Folio", "Fecha", "Vendió", "Cliente", "Pago", "Total", "Estado"), fv));
        }
        return hojas;
    }

    // ---------------------------------------------------------------- PDF

    public Path pdf(Corte k, Negocio negocio) throws IOException {
        Path carpeta = ExportService.carpeta();
        Files.createDirectories(carpeta);
        return pdf(k, negocio, carpeta.resolve(nombreBase(k) + "_" + SELLO.format(Fechas.ahora()) + ".pdf"));
    }

    public Path pdf(Corte k, Negocio negocio, Path archivo) throws IOException {
        Font titulo = new Font(Font.HELVETICA, 16, Font.BOLD, AZUL_MARINO);
        Font subtitulo = new Font(Font.HELVETICA, 12, Font.BOLD, AZUL_MARINO);
        Font normal = new Font(Font.HELVETICA, 11);
        Font negrita = new Font(Font.HELVETICA, 11, Font.BOLD, AZUL_MARINO);
        Font secundario = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);

        Document doc = new Document(PageSize.LETTER, 50, 50, 40, 40);
        try (OutputStream out = Files.newOutputStream(archivo)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(negocio == null || negocio.getNombre() == null ? "" : negocio.getNombre(), titulo));
            Paragraph p = new Paragraph("Corte de caja" + (k.id() > 0 ? " #" + k.id() : ""), subtitulo);
            p.setSpacingBefore(8);
            doc.add(p);
            doc.add(new Paragraph(encabezado(k), secundario));
            Font grande = new Font(Font.HELVETICA, 18, Font.BOLD, AZUL_MARINO);
            Paragraph enCaja = new Paragraph("Debe haber en caja (con fondo): " + Dinero.formatear(k.efectivoEsperado()),
                    grande);
            enCaja.setSpacingBefore(12);
            doc.add(enCaja);
            doc.add(new Paragraph("Total con todo y fondo: " + Dinero.formatear(k.totalConFondo()), grande));
            doc.add(new Paragraph("Efectivo + tarjeta + transferencia + fondo + entradas − retiros (el fiado no cuenta)",
                    secundario));

            PdfPTable tabla = new PdfPTable(new float[]{3.2f, 1.6f});
            tabla.setWidthPercentage(70);
            tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
            tabla.setSpacingBefore(14);
            for (Linea l : lineas(k)) {
                Font f = switch (l.estilo()) {
                    case TOTAL, SECCION -> negrita;
                    case DIFERENCIA -> new Font(Font.HELVETICA, 11, Font.BOLD,
                            l.monto() < 0 ? ROJO : (l.monto() > 0 ? VERDE : AZUL_MARINO));
                    case NOTA -> secundario;
                    default -> normal;
                };
                PdfPCell concepto = celda(l.concepto(), f, false);
                if (l.monto() == null) {
                    concepto.setColspan(2);
                    if (l.estilo() == Estilo.SECCION) {
                        concepto.setPaddingTop(12);
                    }
                    tabla.addCell(concepto);
                } else {
                    tabla.addCell(concepto);
                    String monto = Dinero.formatear(l.monto())
                            + (l.estilo() == Estilo.DIFERENCIA ? "  (" + textoDiferencia(l.monto()) + ")" : "");
                    PdfPCell c = celda(monto, f, true);
                    if (l.estilo() == Estilo.TOTAL) {
                        concepto.setBorder(Rectangle.TOP);
                        c.setBorder(Rectangle.TOP);
                    }
                    tabla.addCell(c);
                }
            }
            doc.add(tabla);
            if (k.notas() != null) {
                Paragraph n = new Paragraph("Notas: " + k.notas(), normal);
                n.setSpacingBefore(12);
                doc.add(n);
            }
            Paragraph firma = new Paragraph("\n\n______________________________\nFirma de quien entrega", secundario);
            firma.setSpacingBefore(30);
            doc.add(firma);
            doc.close();
        } catch (DocumentException e) {
            throw new IOException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return archivo;
    }

    private static PdfPCell celda(String texto, Font fuente, boolean derecha) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(3);
        c.setHorizontalAlignment(derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return c;
    }
}
