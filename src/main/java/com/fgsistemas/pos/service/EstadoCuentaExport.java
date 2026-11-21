package com.fgsistemas.pos.service;

import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.EstadoCuenta;
import com.fgsistemas.pos.model.Negocio;
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
 * Exporta el estado de cuenta de un cliente a PDF (OpenPDF) o Excel, para mandarlo por WhatsApp.
 * Se guarda en la carpeta de exportaciones con el nombre del cliente y la fecha.
 */
public class EstadoCuentaExport {

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final Color AZUL_MARINO = new Color(0x0B, 0x1A, 0x33);
    private static final Color GRIS = new Color(0x55, 0x5F, 0x6D);

    public static final List<String> ENCABEZADOS = List.of("Fecha", "Concepto", "Cargo", "Abono", "Saldo");

    /** Nombre de archivo sin extensión: estado_cuenta_Juan_Perez_2026-11-21_143000. */
    public static String nombreBase(Cliente cliente) {
        String limpio = cliente.getNombre().trim().replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_|_$", "");
        return "estado_cuenta_" + (limpio.isEmpty() ? cliente.getId() : limpio);
    }

    /** Filas para Excel (montos como números con formato de dinero). */
    public static List<List<Object>> filasExcel(EstadoCuenta ec) {
        List<List<Object>> filas = new ArrayList<>();
        for (EstadoCuenta.Movimiento m : ec.movimientos()) {
            List<Object> f = new ArrayList<>();
            f.add(Fechas.FECHA_HORA.format(m.fecha()));
            f.add(m.concepto());
            f.add(m.cargo() > 0 ? new Monto(m.cargo()) : null);
            f.add(m.abono() > 0 ? new Monto(m.abono()) : null);
            f.add(new Monto(m.saldo()));
            filas.add(f);
        }
        return filas;
    }

    /** Crea el PDF en la carpeta de exportaciones y regresa su ruta. */
    public Path pdf(EstadoCuenta ec, Negocio negocio) throws IOException {
        Path carpeta = ExportService.carpeta();
        Files.createDirectories(carpeta);
        Path archivo = carpeta.resolve(nombreBase(ec.cliente()) + "_" + SELLO.format(Fechas.ahora()) + ".pdf");
        return pdf(ec, negocio, archivo);
    }

    public Path pdf(EstadoCuenta ec, Negocio negocio, Path archivo) throws IOException {
        Font titulo = new Font(Font.HELVETICA, 16, Font.BOLD, AZUL_MARINO);
        Font subtitulo = new Font(Font.HELVETICA, 12, Font.BOLD, AZUL_MARINO);
        Font normal = new Font(Font.HELVETICA, 10);
        Font secundario = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);
        Font encabezado = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Font tachado = new Font(Font.HELVETICA, 10, Font.STRIKETHRU, GRIS);
        Font total = new Font(Font.HELVETICA, 13, Font.BOLD, AZUL_MARINO);

        Document doc = new Document(PageSize.LETTER, 40, 40, 40, 40);
        try (OutputStream out = Files.newOutputStream(archivo)) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            String nombreNegocio = negocio == null || negocio.getNombre() == null ? "" : negocio.getNombre();
            doc.add(new Paragraph(nombreNegocio, titulo));
            if (negocio != null) {
                StringBuilder datos = new StringBuilder();
                if (negocio.getDireccion() != null && !negocio.getDireccion().isBlank()) {
                    datos.append(negocio.getDireccion());
                }
                if (negocio.getTelefono() != null && !negocio.getTelefono().isBlank()) {
                    datos.append(datos.length() == 0 ? "" : " · ").append("Tel. ").append(negocio.getTelefono());
                }
                if (datos.length() > 0) {
                    doc.add(new Paragraph(datos.toString(), secundario));
                }
            }
            Paragraph p = new Paragraph("Estado de cuenta", subtitulo);
            p.setSpacingBefore(14);
            doc.add(p);
            Cliente c = ec.cliente();
            doc.add(new Paragraph("Cliente: " + c.getNombre()
                    + (c.getTelefono() == null ? "" : "   ·   Tel. " + c.getTelefono()), normal));
            doc.add(new Paragraph("Fecha: " + Fechas.FECHA_HORA.format(Fechas.ahora()), normal));

            PdfPTable tabla = new PdfPTable(new float[]{2.2f, 3.6f, 1.6f, 1.6f, 1.8f});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(14);
            tabla.setHeaderRows(1);
            for (int i = 0; i < ENCABEZADOS.size(); i++) {
                PdfPCell celda = new PdfPCell(new Phrase(ENCABEZADOS.get(i), encabezado));
                celda.setBackgroundColor(AZUL_MARINO);
                celda.setPadding(5);
                celda.setHorizontalAlignment(i >= 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                tabla.addCell(celda);
            }
            if (ec.movimientos().isEmpty()) {
                PdfPCell vacio = new PdfPCell(new Phrase("Sin movimientos", secundario));
                vacio.setColspan(5);
                vacio.setPadding(5);
                tabla.addCell(vacio);
            }
            for (EstadoCuenta.Movimiento m : ec.movimientos()) {
                Font f = m.cancelada() ? tachado : normal;
                tabla.addCell(celda(Fechas.FECHA_HORA.format(m.fecha()), f, false));
                tabla.addCell(celda(m.concepto(), f, false));
                tabla.addCell(celda(m.cargo() > 0 ? Dinero.formatear(m.cargo()) : "", f, true));
                tabla.addCell(celda(m.abono() > 0 ? Dinero.formatear(m.abono()) : "", f, true));
                tabla.addCell(celda(Dinero.formatear(m.saldo()), normal, true));
            }
            doc.add(tabla);

            Paragraph saldo = new Paragraph((ec.saldoFinal() < 0 ? "Saldo a favor: " + Dinero.formatear(-ec.saldoFinal())
                    : "Saldo actual: " + Dinero.formatear(ec.saldoFinal())), total);
            saldo.setAlignment(Element.ALIGN_RIGHT);
            saldo.setSpacingBefore(12);
            doc.add(saldo);
            if (ec.movimientos().stream().anyMatch(EstadoCuenta.Movimiento::cancelada)) {
                Paragraph nota = new Paragraph("Las ventas tachadas fueron canceladas y no suman al saldo.", secundario);
                nota.setSpacingBefore(6);
                doc.add(nota);
            }
            doc.close(); // antes de cerrar el flujo, para que se escriba completo
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
        c.setPadding(4);
        c.setHorizontalAlignment(derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return c;
    }
}
