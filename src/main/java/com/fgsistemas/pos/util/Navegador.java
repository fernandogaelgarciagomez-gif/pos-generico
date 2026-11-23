package com.fgsistemas.pos.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Una sola Stage maximizada; cambiar de pantalla = reemplazar la raíz de la Scene.
 * Los atajos de cada pantalla se registran sobre su raíz (no sobre la Scene), así se van
 * con ella al navegar.
 */
public final class Navegador {

    public static final String HOJA_ESTILOS = "/css/tema-azul-marino.css";

    private static Stage stage;
    private static Scene scene;
    /** Lo que la pantalla actual perdería si se cierra la ventana (null = nada). Se borra al navegar. */
    private static java.util.function.Supplier<String> cambiosPendientes;

    private Navegador() {
    }

    public static void init(Stage ventana) {
        stage = ventana;
        StackPane vacio = new StackPane();
        vacio.getStyleClass().add("raiz");
        scene = new Scene(vacio, 1280, 800);
        scene.getStylesheets().add(hojaEstilos());
        stage.setScene(scene);
    }

    /** Carga {@code /fxml/<vista>.fxml}, la pone como raíz y regresa su controlador. */
    public static <T> T ir(String vista) {
        URL recurso = Navegador.class.getResource("/fxml/" + vista + ".fxml");
        if (recurso == null) {
            throw new IllegalArgumentException("No existe la vista: " + vista);
        }
        FXMLLoader loader = new FXMLLoader(recurso);
        try {
            cambiosPendientes = null;
            Parent raiz = loader.load();
            scene.setRoot(raiz);
            return loader.getController();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + vista, e);
        }
    }

    /**
     * La pantalla actual avisa qué se perdería al cerrar la ventana (ej. "Hay una venta con 3 productos").
     * El proveedor regresa null cuando no hay nada pendiente.
     */
    public static void alCerrarAvisar(java.util.function.Supplier<String> pendiente) {
        cambiosPendientes = pendiente;
    }

    /** Texto de lo que se perdería al cerrar, o null. */
    public static String cambiosPendientes() {
        try {
            return cambiosPendientes == null ? null : cambiosPendientes.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Stage stage() {
        return stage;
    }

    public static Scene scene() {
        return scene;
    }

    public static String hojaEstilos() {
        URL css = Navegador.class.getResource(HOJA_ESTILOS);
        if (css == null) {
            throw new IllegalStateException("No se encontró " + HOJA_ESTILOS);
        }
        return css.toExternalForm();
    }
}
