package com.fgsistemas.pos.controller;

import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.service.ExportService.Cant;
import com.fgsistemas.pos.service.ExportService.Monto;
import com.fgsistemas.pos.service.Avisos;
import com.fgsistemas.pos.service.InventarioService;
import com.fgsistemas.pos.service.InventarioService.TipoAjuste;
import com.fgsistemas.pos.service.PermisoDenegadoException;
import com.fgsistemas.pos.util.Acciones;
import com.fgsistemas.pos.util.Alertas;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Teclado;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Diálogos de inventario: alta/edición, ajuste de stock, kardex y lista de compras. */
final class ProductoDialogos {

    private static final Map<TipoVenta, List<String>> UNIDADES = Map.of(
            TipoVenta.PIEZA, List.of("pza", "caja", "paq", "bolsa", "par", "docena"),
            TipoVenta.PESO, List.of("kg", "g"),
            TipoVenta.FRACCION, List.of("m", "cm", "l", "ml"));

    private static final List<String> MOTIVOS_AJUSTE =
            List.of("Conteo físico", "Merma", "Producto dañado", "Caducado", "Robo o extravío", "Otro");

    private ProductoDialogos() {
    }

    // ------------------------------------------------------------------ alta / edición

    /**
     * Formulario de producto. {@code existente} null = alta (con {@code codigoInicial} opcional).
     *
     * @return el producto guardado
     */
    static Optional<Producto> formulario(Producto existente, String codigoInicial) {
        InventarioService servicio = new InventarioService();
        boolean nuevo = existente == null;

        List<String> categorias;
        long minimoDefault;
        try {
            categorias = servicio.categorias();
            minimoDefault = servicio.stockMinimoDefault();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return Optional.empty();
        }

        TextField txtCodigo = new TextField(nuevo ? (codigoInicial == null ? "" : codigoInicial) : existente.getCodigo());
        TextField txtNombre = new TextField(nuevo ? "" : existente.getNombre());
        ComboBox<String> cmbCategoria = new ComboBox<>(FXCollections.observableArrayList(categorias));
        cmbCategoria.setEditable(true);
        cmbCategoria.setPromptText("Elige con Alt+↓ o escribe una nueva (opcional)");
        cmbCategoria.getEditor().setText(nuevo || existente.getCategoriaNombre() == null ? "" : existente.getCategoriaNombre());
        cmbCategoria.setMaxWidth(Double.MAX_VALUE);
        cmbCategoria.setPlaceholder(new Label("Aún no hay categorías. Crea una con \"+ Nueva\" (Ctrl+G)."));
        Runnable nuevaCategoria = () -> crearCategoria(servicio, cmbCategoria);
        Button btnNuevaCategoria = Acciones.boton("+ Nueva", "Ctrl+G", nuevaCategoria);

        ComboBox<TipoVenta> cmbTipo = new ComboBox<>(FXCollections.observableArrayList(TipoVenta.values()));
        cmbTipo.setConverter(convertidor(TipoVenta::etiqueta));
        cmbTipo.setValue(nuevo ? TipoVenta.PIEZA : existente.getTipoVenta());
        cmbTipo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbTipo);

        ComboBox<String> cmbUnidad = new ComboBox<>();
        cmbUnidad.setEditable(true);
        cmbUnidad.setMaxWidth(Double.MAX_VALUE);

        TextField txtPrecio = new TextField(nuevo ? "" : Dinero.paraCampo(existente.getPrecioVenta()));
        TextField txtCosto = new TextField(nuevo ? "0" : Dinero.paraCampo(existente.getCosto()));
        Teclado.campoDinero(txtPrecio);
        Teclado.campoDinero(txtCosto);
        TextField txtStock = new TextField("0");
        TextField txtMinimo = new TextField();
        Label lblPrecioUnidad = new Label();
        lblPrecioUnidad.getStyleClass().add("texto-pequeno");

        Runnable aplicarTipo = () -> {
            TipoVenta t = cmbTipo.getValue();
            String actual = cmbUnidad.getEditor().getText();
            cmbUnidad.setItems(FXCollections.observableArrayList(UNIDADES.get(t)));
            if (actual == null || actual.isBlank() || !UNIDADES.get(t).contains(actual)) {
                cmbUnidad.getEditor().setText(t.unidadDefault());
            }
            int decimales = t.aceptaDecimales() ? 3 : 0;
            String stockTxt = txtStock.getText();
            String minTxt = txtMinimo.getText();
            Teclado.soloNumeros(txtStock, decimales);
            Teclado.soloNumeros(txtMinimo, decimales);
            txtStock.setText(recortar(stockTxt, decimales));
            txtMinimo.setText(recortar(minTxt, decimales));
        };
        cmbUnidad.getEditor().setText(nuevo ? TipoVenta.PIEZA.unidadDefault() : existente.getUnidad());
        txtMinimo.setText(Cantidad.paraCampo(nuevo ? minimoDefault : existente.getStockMinimo(),
                cmbTipo.getValue()));
        aplicarTipo.run();
        cmbTipo.valueProperty().addListener((o, a, n) -> aplicarTipo.run());
        Runnable textoUnidad = () -> lblPrecioUnidad.setText("Precio y costo son por "
                + cmbUnidad.getEditor().getText() + ".");
        cmbUnidad.getEditor().textProperty().addListener((o, a, n) -> textoUnidad.run());
        textoUnidad.run();

        Formulario f = new Formulario(nuevo ? "Nuevo producto" : "Editar producto", "Guardar (Enter)")
                .campo("Código", txtCodigo)
                .campo("Nombre", txtNombre)
                .campo("Categoría", cmbCategoria, btnNuevaCategoria)
                .atajo(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN), nuevaCategoria)
                .campo("Se vende por", cmbTipo)
                .campo("Unidad", cmbUnidad)
                .campo("Precio de venta", txtPrecio)
                .campo("Costo", txtCosto);
        if (nuevo) {
            f.campo("Stock inicial", txtStock);
        }
        if (nuevo && codigoInicial != null && !codigoInicial.isBlank()) {
            f.enfocarAlAbrir(txtNombre); // el código ya viene (escaneado): empezar por el nombre
        }
        f.campo("Stock mínimo", txtMinimo)
                .nota("Se vende por: ↑ ↓ para cambiar (Pieza = enteros; Peso/Fracción = hasta 3 decimales). "
                        + "Precio y costo son por unidad (por kg, por metro…). "
                        + (nuevo ? "" : "El stock se cambia con \"Ajustar stock\"."));

        Producto[] guardado = new Producto[1];
        boolean ok = f.mostrar(() -> {
            Producto p = nuevo ? new Producto() : existente.copia();
            try {
                TipoVenta tipo = cmbTipo.getValue();
                p.setCodigo(txtCodigo.getText());
                p.setNombre(txtNombre.getText());
                p.setTipoVenta(tipo);
                p.setUnidad(cmbUnidad.getEditor().getText());
                p.setPrecioVenta(txtPrecio.getText().isBlank() ? 0 : Dinero.parsear(txtPrecio.getText()));
                p.setCosto(txtCosto.getText().isBlank() ? 0 : Dinero.parsear(txtCosto.getText()));
                p.setStockMinimo(txtMinimo.getText().isBlank() ? 0 : Cantidad.parsear(txtMinimo.getText(), tipo));
                if (nuevo) {
                    p.setStock(txtStock.getText().isBlank() ? 0 : Cantidad.parsear(txtStock.getText(), tipo));
                }
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            // Errores humanos (Fase 9): precio con un cero de más o de menos, precio bajo el costo, costo de la
            // caja en vez de la unidad, stock inicial raro, o el mismo producto dado de alta dos veces.
            if (p.getPrecioVenta() > 0) {
                String avisoPrecio = Avisos.precio(p.getPrecioVenta(), nuevo ? 0 : existente.getPrecioVenta(),
                        p.getCosto());
                if (avisoPrecio != null && !Alertas.confirmarPeligro(avisoPrecio)) {
                    return "Revisa el precio y el costo.";
                }
            }
            if (!nuevo) {
                String avisoCosto = Avisos.costo(p.getCosto(), existente.getCosto(), 0);
                if (avisoCosto != null && !Alertas.confirmarPeligro(avisoCosto)) {
                    return "Revisa el costo.";
                }
            }
            if (nuevo) {
                String avisoStock = Cantidad.avisoCantidadInusual(p.getStock(), p.getTipoVenta(),
                        cmbUnidad.getEditor().getText());
                if (avisoStock != null && !Alertas.confirmarPeligro("Stock inicial\n\n" + avisoStock)) {
                    return "Revisa el stock inicial.";
                }
            }
            try {
                Optional<String> otro = servicio.otroProductoConNombre(p.getNombre(), nuevo ? -1 : existente.getId());
                if (otro.isPresent() && !Alertas.confirmarPeligro("Ya existe un producto con ese nombre:\n"
                        + otro.get() + "\n\n¿Guardar de todos modos?")) {
                    return "Ya existe un producto con ese nombre.";
                }
            } catch (SQLException e) {
                Flujo.errorDatos(e);
                return "No se pudo revisar el nombre.";
            }
            String categoria = cmbCategoria.getEditor().getText();
            try {
                if (nuevo) {
                    guardado[0] = servicio.crear(p, categoria);
                } else {
                    servicio.editar(p, categoria);
                    guardado[0] = p;
                }
                return null;
            } catch (IllegalArgumentException | PermisoDenegadoException e) {
                return e.getMessage();
            } catch (SQLException e) {
                Flujo.errorDatos(e);
                return "No se pudo guardar.";
            }
        });
        return ok ? Optional.ofNullable(guardado[0]) : Optional.empty();
    }

    /** Pide el nombre, crea la categoría en la BD y la deja elegida en el combo. */
    private static void crearCategoria(InventarioService servicio, ComboBox<String> combo) {
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej. Bebidas, Papelería, Limpieza");
        String[] creada = new String[1];
        boolean ok = new Formulario("Nueva categoría", "Crear (Enter)")
                .campo("Nombre", txtNombre)
                .mostrar(() -> {
                    try {
                        creada[0] = servicio.crearCategoria(txtNombre.getText());
                        return null;
                    } catch (IllegalArgumentException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo guardar.";
                    }
                });
        if (ok && creada[0] != null) {
            if (!combo.getItems().contains(creada[0])) {
                combo.getItems().add(creada[0]);
                FXCollections.sort(combo.getItems(), String.CASE_INSENSITIVE_ORDER);
            }
            combo.getEditor().setText(creada[0]);
            combo.setValue(creada[0]);
            Alertas.toast("Categoría \"" + creada[0] + "\" creada");
        }
        combo.requestFocus();
    }

    // ------------------------------------------------------------------ ajuste

    static boolean ajustar(Producto p) {
        InventarioService servicio = new InventarioService();
        Label lblActual = new Label(p.stockTexto());
        lblActual.getStyleClass().add("subtitulo");
        ComboBox<TipoAjuste> cmbTipo = new ComboBox<>(FXCollections.observableArrayList(TipoAjuste.values()));
        cmbTipo.setConverter(convertidor(TipoAjuste::etiqueta));
        cmbTipo.setValue(TipoAjuste.FIJAR);
        cmbTipo.setMaxWidth(Double.MAX_VALUE);
        Teclado.comboConFlechas(cmbTipo);
        TextField txtCantidad = new TextField(Cantidad.paraCampo(Math.max(0, p.getStock()), p.getTipoVenta()));
        Teclado.soloNumeros(txtCantidad, p.getTipoVenta().aceptaDecimales() ? 3 : 0);
        ComboBox<String> cmbMotivo = new ComboBox<>(FXCollections.observableArrayList(MOTIVOS_AJUSTE));
        cmbMotivo.setEditable(true);
        cmbMotivo.setPromptText("Obligatorio");
        cmbMotivo.setMaxWidth(Double.MAX_VALUE);
        Label lblNuevo = new Label();
        lblNuevo.getStyleClass().add("texto-exito");

        Runnable vista = () -> {
            try {
                long q = txtCantidad.getText().isBlank() ? 0 : Cantidad.parsear(txtCantidad.getText(), p.getTipoVenta());
                long nuevo = switch (cmbTipo.getValue()) {
                    case FIJAR -> q;
                    case SUMAR -> p.getStock() + q;
                    case RESTAR -> p.getStock() - q;
                };
                lblNuevo.setText(p.formatear(nuevo));
            } catch (IllegalArgumentException e) {
                lblNuevo.setText("—");
            }
        };
        txtCantidad.textProperty().addListener((o, a, n) -> vista.run());
        cmbTipo.valueProperty().addListener((o, a, n) -> vista.run());
        vista.run();

        return new Formulario("Ajustar stock: " + p.getNombre(), "Ajustar (Enter)")
                .campo("Stock actual", lblActual)
                .campo("Tipo de ajuste", cmbTipo)
                .campo("Cantidad", txtCantidad)
                .campo("Motivo", cmbMotivo)
                .campo("Stock nuevo", lblNuevo)
                .nota("Tipo: ↑ ↓ para cambiar. Motivo: elige con Alt+↓ o escríbelo. Queda registrado en el kardex.")
                .mostrar(() -> {
                    try {
                        long q = txtCantidad.getText().isBlank() ? 0
                                : Cantidad.parsear(txtCantidad.getText(), p.getTipoVenta());
                        String aviso = Cantidad.avisoCantidadInusual(q, p.getTipoVenta(), p.getUnidad());
                        if (aviso != null && !Alertas.confirmarPeligro(p.getNombre() + "\n\n" + aviso)) {
                            return "Revisa la cantidad.";
                        }
                        long resultado = servicio.ajustarStock(p.getId(), cmbTipo.getValue(), q,
                                cmbMotivo.getEditor().getText());
                        Alertas.toast("Stock de " + p.getNombre() + ": " + p.formatear(resultado));
                        return null;
                    } catch (IllegalArgumentException | PermisoDenegadoException e) {
                        return e.getMessage();
                    } catch (SQLException e) {
                        Flujo.errorDatos(e);
                        return "No se pudo guardar.";
                    }
                });
    }

    // ------------------------------------------------------------------ kardex

    static void kardex(Producto p) {
        List<MovimientoInventario> movimientos;
        try {
            movimientos = new InventarioService().kardex(p.getId());
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        TableView<MovimientoInventario> tabla = Tablas.tabla("Sin movimientos");
        tabla.getColumns().add(Tablas.col("Fecha", 150, m -> Fechas.FECHA_HORA.format(m.fecha())));
        tabla.getColumns().add(Tablas.col("Tipo", 120, m -> tipoTexto(m.tipo())));
        tabla.getColumns().add(Tablas.colDerecha("Cantidad", 130, m -> conSigno(p, m.cantidad())));
        tabla.getColumns().add(Tablas.colDerecha("Stock", 130, m -> p.formatear(m.stockResultante())));
        tabla.getColumns().add(Tablas.col("Usuario", 150, MovimientoInventario::usuarioNombre));
        tabla.getColumns().add(Tablas.col("Detalle", 260, m -> detalle(m)));
        tabla.setItems(FXCollections.observableArrayList(movimientos));

        Label encabezado = new Label(p.getCodigo() + " · Stock actual: " + p.stockTexto()
                + "\nHistorial de todo lo que ha entrado y salido de este producto: ventas, cancelaciones, "
                + "entradas de mercancía y ajustes, con el stock que quedó después de cada uno.");
        encabezado.setWrapText(true);
        encabezado.getStyleClass().add("texto-secundario");
        Button exportar = Acciones.boton("Exportar a Excel", null, () -> Exportar.excel(
                "kardex_" + p.getCodigo(), "Kardex",
                List.of("Fecha", "Tipo", "Cantidad", "Stock resultante", "Usuario", "Detalle"),
                movimientos.stream().map(m -> List.<Object>of(Fechas.FECHA_HORA.format(m.fecha()), tipoTexto(m.tipo()),
                        new Cant(m.cantidad()), new Cant(m.stockResultante()), m.usuarioNombre(), detalle(m))).toList()));
        Tablas.dialogo("Movimientos (kardex): " + p.getNombre(), encabezado, tabla, null, 980, 520, List.of(exportar));
    }

    private static String tipoTexto(String tipo) {
        return switch (tipo) {
            case "VENTA" -> "Venta";
            case "CANCELACION" -> "Cancelación";
            case "ENTRADA" -> "Entrada";
            case "AJUSTE" -> "Ajuste";
            case "DEVOLUCION" -> "Devolución";
            default -> tipo;
        };
    }

    private static String detalle(MovimientoInventario m) {
        String ref = m.referenciaId() == null ? "" : switch (m.tipo()) {
            case "VENTA", "CANCELACION" -> "Venta #" + m.referenciaId();
            case "ENTRADA" -> "Entrada #" + m.referenciaId();
            case "DEVOLUCION" -> "Devolución #" + m.referenciaId();
            default -> "#" + m.referenciaId();
        };
        String motivo = m.motivo() == null ? "" : m.motivo();
        return (ref + (ref.isEmpty() || motivo.isEmpty() ? "" : " · ") + motivo).trim();
    }

    private static String conSigno(Producto p, long cantidad) {
        return (cantidad > 0 ? "+" : cantidad < 0 ? "−" : "") + p.formatear(Math.abs(cantidad));
    }

    // ------------------------------------------------------------------ lista de compras

    static void listaDeCompras() {
        List<Producto> productos;
        try {
            productos = new InventarioService().listaDeCompras();
        } catch (SQLException e) {
            Flujo.errorDatos(e);
            return;
        }
        TableView<Producto> tabla = Tablas.tabla("No hay productos con stock bajo 🎉");
        tabla.getColumns().add(Tablas.col("Código", 130, Producto::getCodigo));
        tabla.getColumns().add(Tablas.col("Producto", 300, Producto::getNombre));
        tabla.getColumns().add(Tablas.colDerecha("Stock", 120, Producto::stockTexto));
        tabla.getColumns().add(Tablas.colDerecha("Mínimo", 120, p -> p.formatear(p.getStockMinimo())));
        tabla.getColumns().add(Tablas.colDerecha("Comprar", 120,
                p -> p.formatear(InventarioService.cantidadSugerida(p))));
        tabla.getColumns().add(Tablas.colDerecha("Costo estimado", 140,
                p -> Dinero.formatear(Cantidad.importe(p.getCosto(), InventarioService.cantidadSugerida(p)))));
        tabla.setItems(FXCollections.observableArrayList(productos));

        long total = productos.stream()
                .mapToLong(p -> Cantidad.importe(p.getCosto(), InventarioService.cantidadSugerida(p))).sum();
        Label pie = new Label(productos.size() + " productos · Costo estimado total: " + Dinero.formatear(total)
                + "   (cantidad sugerida = mínimo × 2 − stock)");
        pie.getStyleClass().add("pie-tabla");

        List<List<Object>> filas = new ArrayList<>();
        for (Producto p : productos) {
            long q = InventarioService.cantidadSugerida(p);
            filas.add(List.of(p.getCodigo(), p.getNombre(), p.getUnidad(), new Cant(p.getStock()),
                    new Cant(p.getStockMinimo()), new Cant(q), new Monto(p.getCosto()),
                    new Monto(Cantidad.importe(p.getCosto(), q))));
        }
        Button exportar = Acciones.boton("Exportar a Excel", null, () -> Exportar.excel("lista_de_compras",
                "Lista de compras", List.of("Código", "Producto", "Unidad", "Stock", "Mínimo", "Comprar",
                        "Costo unitario", "Costo estimado"), filas));
        Tablas.dialogo("Lista de compras", null, tabla, pie, 1000, 560, List.of(exportar));
    }

    // ------------------------------------------------------------------ utilidades

    private static <T> StringConverter<T> convertidor(java.util.function.Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : texto.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }

    /** Ajusta un texto numérico a {@code decimales} (al cambiar de Peso a Pieza, "1.500" → "1"). */
    private static String recortar(String texto, int decimales) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        int punto = texto.indexOf('.');
        if (punto < 0) {
            return texto;
        }
        if (decimales == 0) {
            return texto.substring(0, punto);
        }
        return texto.substring(0, Math.min(texto.length(), punto + 1 + decimales));
    }
}
