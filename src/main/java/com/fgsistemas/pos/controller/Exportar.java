package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.App;
import com.fgsistemas.pos.service.ExportService;
import com.fgsistemas.pos.util.Alertas;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Exporta a Excel desde cualquier pantalla y ofrece abrir el archivo. */
final class Exportar {

    private static final Logger LOG = Logger.getLogger(Exportar.class.getName());

    private Exportar() {
    }

    static void excel(String nombreBase, String hoja, List<String> encabezados, List<List<Object>> filas) {
        if (filas.isEmpty()) {
            Alertas.advertencia("No hay datos para exportar.");
            return;
        }
        try {
            Path archivo = new ExportService().excel(nombreBase, hoja, encabezados, filas);
            if (Alertas.confirmar("Archivo guardado en:\n" + archivo + "\n\n¿Abrirlo ahora?")) {
                App.abrirArchivo(archivo);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo exportar", e);
            Alertas.error("No se pudo guardar el archivo de Excel.\n\n" + e.getMessage()
                    + "\n\nSi el archivo está abierto en Excel, ciérralo e intenta de nuevo.");
        }
    }
}
