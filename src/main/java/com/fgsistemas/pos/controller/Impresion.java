package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.config.Hardware;
import com.fgsistemas.pos.service.EscPos;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.TicketService;
import com.fgsistemas.pos.util.Alertas;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Impresión de tickets desde las pantallas: al cobrar, reimprimir y vista previa. */
final class Impresion {

    private static final Logger LOG = Logger.getLogger(Impresion.class.getName());

    private Impresion() {
    }

    /**
     * Después de cobrar: imprime según la configuración (siempre / preguntar / nunca) y abre el cajón si se
     * pagó en efectivo y está activado. Sin impresora configurada no hace nada.
     */
    static void despuesDeCobrar(long ventaId, boolean huboEfectivo) {
        Hardware hw = Hardware.cargar();
        if (!hw.hayImpresora()) {
            // DECISION: sin impresora y en "Imprimir siempre" (el valor de fábrica) no se molesta en cada venta.
            // Si eligieron "Preguntar", se pregunta igual y el ticket se muestra en pantalla con el aviso.
            if (hw.getModo() == Hardware.ModoTicket.PREGUNTAR
                    && Alertas.confirmar("¿Imprimir el ticket de la venta?")) {
                mostrarSinImpresora(ventaId, hw);
            }
            return;
        }
        boolean cajon = hw.isAbrirCajon() && huboEfectivo;
        boolean imprimir = switch (hw.getModo()) {
            case AUTOMATICO -> true;
            case PREGUNTAR -> Alertas.confirmar("¿Imprimir el ticket de la venta?");
            case NUNCA -> false;
        };
        if (imprimir) {
            imprimirVenta(ventaId, hw, false, cajon);
        } else if (cajon) {
            avisarSiFalla(TicketService.abrirCajon(hw), "No se pudo abrir el cajón");
        }
    }

    /** Sin impresora elegida: muestra el ticket en pantalla y explica cómo configurarla. */
    private static void mostrarSinImpresora(long ventaId, Hardware hw) {
        try {
            vistaPrevia("Ticket (no hay impresora elegida)",
                    new TicketService().ticketVenta(ventaId, hw, false, false),
                    "⚠ No hay impresora de tickets elegida, por eso el ticket se muestra aquí. "
                            + "Elígela en Configuración > Ticket y lector > Impresora de tickets y oprime Guardar.");
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        }
    }

    /** Reimprime el ticket de una venta ya registrada (marcado como REIMPRESIÓN). */
    static void reimprimir(long ventaId) {
        Hardware hw = Hardware.cargar();
        if (!hw.hayImpresora()) {
            Alertas.advertencia("No hay impresora de tickets elegida.\n\n"
                    + "Un administrador puede elegirla en Configuración > Ticket y lector > Impresora de tickets.");
            return;
        }
        if (imprimirVenta(ventaId, hw, true, false)) {
            Alertas.toast("Reimprimiendo ticket…");
        }
    }

    private static boolean imprimirVenta(long ventaId, Hardware hw, boolean reimpresion, boolean cajon) {
        try {
            avisarSiFalla(new TicketService().imprimirVenta(ventaId, hw, reimpresion, cajon),
                    "No se pudo imprimir el ticket");
            return true;
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (IllegalArgumentException | PermisoDenegadoException e) {
            Alertas.advertencia(e.getMessage());
        }
        return false;
    }

    /** Si la impresión falla (en segundo plano), avisa en pantalla sin detener la venta. */
    static void avisarSiFalla(CompletableFuture<Void> trabajo, String titulo) {
        trabajo.whenComplete((ok, error) -> {
            if (error == null) {
                return;
            }
            Throwable causa = error.getCause() != null ? error.getCause() : error;
            LOG.log(Level.WARNING, titulo, causa);
            Platform.runLater(() -> Alertas.advertencia(titulo + ".\n\n" + causa.getMessage()
                    + "\n\nRevisa que la impresora esté encendida, con papel y conectada. "
                    + "La venta sí quedó registrada; puedes reimprimir el ticket con Ctrl+P en Vender."));
        });
    }

    /** Muestra cómo saldrá un ticket (texto monoespaciado del ancho del papel). */
    static void vistaPrevia(String titulo, EscPos ticket) {
        vistaPrevia(titulo, ticket, null);
    }

    static void vistaPrevia(String titulo, EscPos ticket, String aviso) {
        TextArea texto = new TextArea(ticket.vistaPrevia());
        texto.setEditable(false);
        texto.setWrapText(false);
        texto.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 14px;");
        texto.setPrefColumnCount(ticket.columnas() + 2);
        texto.setPrefRowCount(28);
        Label nota = new Label("Así se acomoda el texto en el papel (" + ticket.columnas()
                + " caracteres por renglón). En la impresora, el nombre del negocio y el TOTAL salen más grandes.");
        nota.setWrapText(true);
        nota.setMaxWidth(ticket.columnas() * 10 + 40);
        nota.getStyleClass().add("texto-secundario");

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titulo);
        d.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar (Esc)", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox contenido = new VBox(10, nota, texto);
        if (aviso != null) {
            Label lblAviso = new Label(aviso);
            lblAviso.setWrapText(true);
            lblAviso.setMaxWidth(ticket.columnas() * 10 + 40);
            lblAviso.getStyleClass().add("texto-error");
            contenido.getChildren().add(0, lblAviso);
        }
        d.getDialogPane().setContent(contenido);
        Alertas.estilizar(d);
        d.showAndWait();
    }
}
