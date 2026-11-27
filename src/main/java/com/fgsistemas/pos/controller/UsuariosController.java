package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.service.UsuarioService;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Teclado;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import com.fgsistemas.pos.util.Acciones;

import java.sql.SQLException;
import java.util.List;

/** Administración de usuarios (21-usuarios-y-permisos.md). */
public class UsuariosController {

    @FXML private BorderPane raiz;
    @FXML private FlowPane barraAcciones;
    @FXML private BarraSuperiorController barraSuperiorController;
    @FXML private TableView<Usuario> tabla;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colActivo;
    @FXML private TableColumn<Usuario, String> colCreado;

    private final UsuarioService servicio = new UsuarioService();

    @FXML
    private void initialize() {
        colNombre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getNombre()));
        colUsuario.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUsuario()));
        colRol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRol().etiqueta()));
        colActivo.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().isActivo() ? "Sí" : "No"));
        colCreado.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getCreadoEn() == null ? "" : Fechas.FECHA_HORA.format(c.getValue().getCreadoEn())));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                getStyleClass().remove("inactivo");
                if (!vacio && u != null && !u.isActivo()) {
                    getStyleClass().add("inactivo");
                }
            }
        });

        // Acciones: botón visible + atajo
        Acciones.agregar(barraAcciones, raiz, "Nuevo", "Ins/Ctrl+N", null, false, this::nuevo);
        Teclado.atajoNuevo(raiz, this::nuevo);
        barraAcciones.getChildren().add(Acciones.boton("Editar", "Enter", this::editar));
        Acciones.agregar(barraAcciones, raiz, "Restablecer contraseña", "R",
                new KeyCodeCombination(KeyCode.R), true, this::restablecer);
        Acciones.agregar(barraAcciones, raiz, "Desactivar", "Supr",
                new KeyCodeCombination(KeyCode.DELETE), true, this::desactivar);
        Acciones.agregar(barraAcciones, raiz, "Cambiar mi contraseña", "Ctrl+P",
                new KeyCodeCombination(KeyCode.P, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), false,
                this::cambiarMiPassword);
        Acciones.espaciador(barraAcciones);
        Acciones.regresar(barraAcciones, raiz, "Regresar al menú", Flujo::irAlMenu);
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                editar();
            }
        });
        tabla.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                editar();
            }
        });

        // El menú solo abre esta pantalla con permiso USUARIOS; el servicio lo vuelve a validar.
        cargar(null);
        Teclado.enfocar(tabla);
    }

    private void cargar(Long seleccionarId) {
        try {
            List<Usuario> lista = servicio.listar();
            tabla.setItems(FXCollections.observableArrayList(lista));
            int indice = 0;
            if (seleccionarId != null) {
                for (int i = 0; i < lista.size(); i++) {
                    if (lista.get(i).getId() == seleccionarId) {
                        indice = i;
                        break;
                    }
                }
            }
            if (!lista.isEmpty()) {
                tabla.getSelectionModel().select(indice);
                tabla.scrollTo(indice);
            }
        } catch (SQLException e) {
            Flujo.errorDatos(e);
        } catch (PermisoDenegadoException e) {
            Alertas.error(e.getMessage());
        }
    }

    private void nuevo() {
        TextField nombre = new TextField();
        TextField usuario = new TextField();
        usuario.setPromptText("minúsculas, sin espacios");
        PasswordField password = new PasswordField();
        PasswordField confirmar = new PasswordField();
        ComboBox<Rol> rol = comboRol(Rol.CAJERO);

        Usuario[] creado = new Usuario[1];
        boolean ok = new Formulario("Nuevo usuario", "Crear")
                .campo("Nombre completo", nombre)
                .campo("Usuario", usuario)
                .campo("Contraseña", password)
                .campo("Confirmar", confirmar)
                .campo("Rol", rol)
                .nota("Usuario: 3 a 20 caracteres, minúsculas, sin espacios. Contraseña: mínimo 6. "
                        + "Rol: ↑ ↓ para cambiar.")
                .mostrar(() -> intentar(() -> creado[0] = servicio.crear(nombre.getText(), usuario.getText(),
                        password.getText(), confirmar.getText(), rol.getValue())));
        if (ok) {
            Alertas.toast("Usuario \"" + creado[0].getUsuario() + "\" creado");
            cargar(creado[0].getId());
        }
        tabla.requestFocus();
    }

    private void editar() {
        Usuario sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            return;
        }
        TextField nombre = new TextField(sel.getNombre());
        ComboBox<Rol> rol = comboRol(sel.getRol());
        CheckBox activo = new CheckBox("Usuario activo");
        activo.setSelected(sel.isActivo());

        boolean ok = new Formulario("Editar usuario: " + sel.getUsuario(), "Guardar")
                .campo("Nombre completo", nombre)
                .campo("Rol", rol)
                .campo("Estado", activo)
                .nota("Rol: ↑ ↓ para cambiar. Estado: Espacio para marcar/desmarcar.")
                .mostrar(() -> intentar(() -> servicio.editar(sel.getId(), nombre.getText(), rol.getValue(),
                        activo.isSelected())));
        if (ok) {
            Alertas.toast("Cambios guardados");
            if (barraSuperiorController != null) {
                barraSuperiorController.refrescar();
            }
            cargar(sel.getId());
        }
        tabla.requestFocus();
    }

    private void restablecer() {
        Usuario sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null) {
            return;
        }
        PasswordField password = new PasswordField();
        PasswordField confirmar = new PasswordField();
        boolean ok = new Formulario("Restablecer contraseña: " + sel.getUsuario(), "Guardar")
                .campo("Nueva contraseña", password)
                .campo("Confirmar", confirmar)
                .nota("Mínimo 6 caracteres. Entrégasela al usuario; podrá cambiarla con Ctrl+P.")
                .mostrar(() -> intentar(() -> servicio.restablecerPassword(sel.getId(), password.getText(),
                        confirmar.getText())));
        if (ok) {
            Alertas.toast("Contraseña de \"" + sel.getUsuario() + "\" restablecida");
        }
        tabla.requestFocus();
    }

    private void desactivar() {
        Usuario sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null || !sel.isActivo()) {
            return;
        }
        if (!Alertas.confirmarPeligro("¿Desactivar a " + sel.getNombre() + " (" + sel.getUsuario() + ")?\n"
                + "Ya no podrá iniciar sesión. Puedes reactivarlo después con Enter.")) {
            tabla.requestFocus();
            return;
        }
        String error = intentar(() -> servicio.desactivar(sel.getId()));
        if (error != null) {
            Alertas.advertencia(error);
        } else {
            Alertas.toast("Usuario desactivado");
            cargar(sel.getId());
        }
        tabla.requestFocus();
    }

    /** El usuario en sesión cambia su propia contraseña (pide la actual). */
    private void cambiarMiPassword() {
        PasswordField actual = new PasswordField();
        PasswordField nueva = new PasswordField();
        PasswordField confirmar = new PasswordField();
        boolean ok = new Formulario("Cambiar mi contraseña", "Guardar (Enter)")
                .campo("Contraseña actual", actual)
                .campo("Nueva contraseña", nueva)
                .campo("Confirmar nueva", confirmar)
                .nota("Mínimo 6 caracteres.")
                .mostrar(() -> intentar(() -> servicio.cambiarPropiaPassword(actual.getText(), nueva.getText(),
                        confirmar.getText())));
        if (ok) {
            Alertas.toast("Tu contraseña se actualizó");
        }
        tabla.requestFocus();
    }

    private static ComboBox<Rol> comboRol(Rol inicial) {
        ComboBox<Rol> combo = new ComboBox<>(FXCollections.observableArrayList(Rol.values()));
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Rol r) {
                return r == null ? "" : r.etiqueta();
            }

            @Override
            public Rol fromString(String s) {
                return null;
            }
        });
        combo.setValue(inicial);
        combo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(combo);
        return combo;
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws SQLException;
    }

    /** Ejecuta la acción del servicio y regresa el mensaje de error para el usuario, o null si salió bien. */
    private static String intentar(Accion accion) {
        try {
            accion.ejecutar();
            return null;
        } catch (IllegalArgumentException | IllegalStateException | PermisoDenegadoException e) {
            return e.getMessage();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return "No se pudo guardar.";
        }
    }
}
