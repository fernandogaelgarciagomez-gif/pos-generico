package com.fgsistemas.pos.service;

import com.fgsistemas.pos.util.Fechas;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportación a Excel (.xlsx) con Apache POI.
 * DECISION: se guarda en Documentos\Punto de Venta\Exportaciones con fecha/hora en el nombre
 * (sin diálogo "Guardar como"); la pantalla ofrece abrir el archivo.
 */
public class ExportService {

    /** Celda de dinero (centavos) → número con formato $#,##0.00. */
    public record Monto(long centavos) {
    }

    /** Celda de cantidad (milésimas) → número con hasta 3 decimales. */
    public record Cant(long milesimas) {
    }

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    public static Path carpeta() {
        File documentos = null;
        try {
            documentos = FileSystemView.getFileSystemView().getDefaultDirectory();
        } catch (RuntimeException ignorada) {
            // Sin Swing disponible se usa la carpeta del usuario.
        }
        Path base = documentos != null ? documentos.toPath() : Paths.get(System.getProperty("user.home"), "Documents");
        return base.resolve("Punto de Venta").resolve("Exportaciones");
    }

    /** Crea {@code <nombreBase>_<fecha>.xlsx} con una hoja y regresa su ruta. */
    public Path excel(String nombreBase, String nombreHoja, List<String> encabezados, List<List<Object>> filas)
            throws IOException {
        Path carpeta = carpeta();
        Files.createDirectories(carpeta);
        Path archivo = carpeta.resolve(nombreBase + "_" + SELLO.format(Fechas.ahora()) + ".xlsx");
        return excel(archivo, nombreHoja, encabezados, filas);
    }

    public Path excel(Path archivo, String nombreHoja, List<String> encabezados, List<List<Object>> filas)
            throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            DataFormat formato = libro.createDataFormat();
            CellStyle estiloEncabezado = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            estiloEncabezado.setFont(negrita);
            CellStyle estiloDinero = libro.createCellStyle();
            estiloDinero.setDataFormat(formato.getFormat("$#,##0.00"));
            CellStyle estiloCantidad = libro.createCellStyle();
            estiloCantidad.setDataFormat(formato.getFormat("#,##0.###"));

            Sheet hoja = libro.createSheet(nombreHoja);
            Row fila0 = hoja.createRow(0);
            for (int i = 0; i < encabezados.size(); i++) {
                Cell celda = fila0.createCell(i);
                celda.setCellValue(encabezados.get(i));
                celda.setCellStyle(estiloEncabezado);
            }
            for (int r = 0; r < filas.size(); r++) {
                Row fila = hoja.createRow(r + 1);
                List<Object> valores = filas.get(r);
                for (int i = 0; i < valores.size(); i++) {
                    Object v = valores.get(i);
                    Cell celda = fila.createCell(i);
                    if (v == null) {
                        celda.setBlank();
                    } else if (v instanceof Monto m) {
                        celda.setCellValue(BigDecimal.valueOf(m.centavos(), 2).doubleValue());
                        celda.setCellStyle(estiloDinero);
                    } else if (v instanceof Cant q) {
                        celda.setCellValue(BigDecimal.valueOf(q.milesimas(), 3).doubleValue());
                        celda.setCellStyle(estiloCantidad);
                    } else if (v instanceof Number n) {
                        celda.setCellValue(n.doubleValue());
                    } else {
                        celda.setCellValue(v.toString());
                    }
                }
            }
            hoja.createFreezePane(0, 1);
            for (int i = 0; i < encabezados.size(); i++) {
                hoja.autoSizeColumn(i);
            }
            try (OutputStream out = Files.newOutputStream(archivo)) {
                libro.write(out);
            }
        }
        return archivo;
    }
}
