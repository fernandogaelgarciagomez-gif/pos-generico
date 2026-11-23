package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.service.ImportacionService;
import com.fgsistemas.pos.service.ImportacionService.Estado;
import com.fgsistemas.pos.service.ImportacionService.Fila;
import com.fgsistemas.pos.service.ImportacionService.Vista;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Navegador;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inventario → Importar de Excel (Ctrl+I): elegir el archivo, vista previa con lo que se dará de alta,
 * lo que se omite y los errores por fila, y confirmar. Nada se guarda antes de confirmar.
 */
final class ImportarDialogos {

    private static final Logger LOG = Logger.getLogger(ImportarDialogos.class.getName());

    private ImportarDialogos() {
    }

    /** @return true si se importaron productos (para recargar la lista) */
    static boolean importar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir el Excel del inventario");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File archivo = fc.showOpenDialog(Navegador.stage());
        if (archivo == null) {
            return false;
        }
        ImportacionService servicio = new ImportacionService();
        Vista vista;
        try {
            vista = servicio.leer(archivo.toPath());
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
            return false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo leer el Excel " + archivo, e);
            Alertas.error("No se pudo leer el archivo.\n\nSi está abierto en Excel, ciérralo e intenta de nuevo.");
            return false;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return false;
        }

        // Errores primero, luego nuevos con aviso, luego el resto (en el orden del archivo).
        List<Fila> filas = vista.filas().stream()
                .sorted(Comparator.comparingInt((Fila f) -> f.estado() == Estado.ERROR ? 0
                        : f.estado() == Estado.NUEVO && !f.mensaje().isEmpty() ? 1 : 2))
                .toList();
        TableView<Fila> tabla = Tablas.tabla("Sin filas");
        tabla.getColumns().add(Tablas.col("Fila", 60, f -> String.valueOf(f.numero())));
        tabla.getColumns().add(Tablas.col("Estado", 100, f -> switch (f.estado()) {
            case NUEVO -> "Se da de alta";
            case YA_EXISTE -> "Ya existe";
            case ERROR -> "ERROR";
        }));
        tabla.getColumns().add(Tablas.col("Código", 80, Fila::codigo));
        tabla.getColumns().add(Tablas.col("Producto", 260, Fila::nombre));
        tabla.getColumns().add(Tablas.colDerecha("Precio", 90, f -> f.estado() == Estado.ERROR ? ""
                : Dinero.formatear(f.precio())));
        tabla.getColumns().add(Tablas.colDerecha("Costo", 90, f -> f.estado() == Estado.ERROR ? ""
                : Dinero.formatear(f.costo())));
        tabla.getColumns().add(Tablas.colDerecha("Cantidad", 100, f -> f.tipo() == null || f.estado() == Estado.ERROR
                ? "" : Cantidad.formatear(f.cantidad(), f.tipo(), f.unidad())));
        tabla.getColumns().add(Tablas.col("Nota", 320, Fila::mensaje));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Fila f, boolean vacio) {
                super.updateItem(f, vacio);
                getStyleClass().removeAll("cancelada", "inactivo");
                if (!vacio && f != null) {
                    if (f.estado() == Estado.ERROR) {
                        getStyleClass().add("cancelada");
                    } else if (f.estado() == Estado.YA_EXISTE) {
                        getStyleClass().add("inactivo");
                    }
                }
            }
        });
        tabla.setItems(FXCollections.observableArrayList(filas));

        Label resumen = new Label(vista.nuevos() + " productos se darán de alta (valor a costo "
                + Dinero.formatear(vista.valorCosto()) + ")  ·  " + vista.existentes() + " ya existen (se omiten)  ·  "
                + vista.errores() + " con error (se omiten; corrígelos en el Excel y vuelve a importar)");
        resumen.getStyleClass().add("subtitulo");
        resumen.setWrapText(true);
        Label ayuda = new Label("Solo se dan de alta productos nuevos; un producto que ya existe no se cambia. "
                + "El stock contado entra como \"Inventario inicial (Excel)\" en los movimientos.");
        ayuda.getStyleClass().add("texto-secundario");
        ayuda.setWrapText(true);

        boolean[] hecho = new boolean[1];
        Runnable confirmar = () -> {
            if (vista.nuevos() == 0) {
                Alertas.advertencia("No hay productos nuevos para importar.");
                return;
            }
            String extra = vista.errores() > 0 ? "\n\nHay " + vista.errores() + " filas con error que NO se importarán."
                    : "";
            if (!Alertas.confirmar("¿Dar de alta " + vista.nuevos() + " productos con su stock?" + extra)) {
                return;
            }
            try {
                int n = servicio.importar(vista);
                hecho[0] = true;
                Alertas.info("Importación terminada", n + " productos dados de alta."
                        + (vista.errores() > 0 ? "\n\nCorrige las " + vista.errores() + " filas con error en el Excel y "
                        + "vuelve a importarlo: los que ya entraron se omiten solos." : ""));
                tabla.getScene().getWindow().hide();
            } catch (IllegalArgumentException | PermisoDenegadoException e) {
                Alertas.advertencia(e.getMessage());
            } catch (SQLException e) {
                Flujo.errorDatos(e);
            }
        };
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F10) {
                e.consume();
                confirmar.run();
            }
        });
        Button btn = Acciones.boton("Importar " + vista.nuevos() + " productos", "F10", confirmar);
        btn.getStyleClass().add("boton-principal");
        Tablas.dialogo("Importar de Excel: " + archivo.getName(), ayuda, tabla, resumen, 1150, 620, List.of(btn));
        return hecho[0];
    }
}
