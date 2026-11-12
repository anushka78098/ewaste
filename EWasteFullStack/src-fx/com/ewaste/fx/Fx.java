package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.Ui;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small JavaFX helpers shared by all screens (the counterpart of the Swing Ui class). */
public final class Fx {
    private Fx() {}

    public static final String GREEN = "#2E7D32", GREEN_DARK = "#1B5E20", GREEN_LIGHT = "#E8F5E9", GREY = "#607D8B";

    // ---- background calls ----------------------------------------------------------------------
    /** Runs the job off the UI thread, then calls ok on the UI thread. Errors are shown in a dialog. */
    public static <T> void run(Callable<T> job, Consumer<T> ok) { run(job, ok, null); }

    public static <T> void run(Callable<T> job, Consumer<T> ok, Runnable onFail) {
        Task<T> t = new Task<T>() {
            @Override protected T call() throws Exception { return job.call(); }
        };
        t.setOnSucceeded(e -> { if (ok != null) ok.accept(t.getValue()); });
        t.setOnFailed(e -> {
            error(t.getException());
            if (onFail != null) onFail.run();
        });
        Thread th = new Thread(t, "api-call");
        th.setDaemon(true);
        th.start();
    }

    // ---- dialogs ---------------------------------------------------------------------------------
    public static void error(Throwable t) {
        String msg = t == null ? "Unknown error" : t.getMessage() == null ? t.toString() : t.getMessage();
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setTitle("Something went wrong");
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle("E-Waste Manager");
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        a.setTitle("Please confirm");
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Returns the text the user typed, or null if they cancelled. */
    public static String ask(String title, String prompt, String initial) {
        TextInputDialog d = new TextInputDialog(initial == null ? "" : initial);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(prompt);
        Optional<String> r = d.showAndWait();
        return r.orElse(null);
    }

    /** Shows the content in OK / Cancel dialog; true if OK was pressed. */
    public static boolean dialog(String title, Node content) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return d.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    public static void text(String title, String body) {
        TextArea a = readOnlyArea(14);
        a.setText(body);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setContent(a);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    // ---- widgets -----------------------------------------------------------------------------------
    public static final class Opt {
        public final String name, label;
        Opt(String name, String label) { this.name = name; this.label = label; }
        @Override public String toString() { return label; }
    }

    public static ComboBox<Opt> combo(List<Map<String, Object>> opts, String allLabel) {
        ComboBox<Opt> cb = new ComboBox<>();
        if (allLabel != null) cb.getItems().add(new Opt("", allLabel));
        for (Map<String, Object> o : opts) cb.getItems().add(new Opt(Ui.s(o.get("name")), Ui.s(o.get("label"))));
        cb.getSelectionModel().selectFirst();
        return cb;
    }

    public static String sel(ComboBox<Opt> cb) {
        Opt o = cb.getValue();
        return o == null ? "" : o.name;
    }

    public static void select(ComboBox<Opt> cb, String name) {
        for (Opt o : cb.getItems()) if (o.name.equals(name)) cb.getSelectionModel().select(o);
    }

    public static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Button primary(String text, Runnable action) {
        Button b = button(text, action);
        b.setStyle("-fx-background-color:" + GREEN + ";-fx-text-fill:white;-fx-font-weight:bold;");
        return b;
    }

    public static Label heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + GREEN_DARK + ";");
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + GREY + ";");
        l.setWrapText(true);
        return l;
    }

    public static TextArea readOnlyArea(int rows) {
        TextArea a = new TextArea();
        a.setEditable(false);
        a.setWrapText(true);
        a.setPrefRowCount(rows);
        a.setStyle("-fx-font-family:monospace;");
        return a;
    }

    public static HBox row(Node... nodes) {
        HBox h = new HBox(8, nodes);
        h.setPadding(new Insets(4, 0, 4, 0));
        return h;
    }

    /** Two-column label / field form. */
    public static GridPane form(Object... labelFieldPairs) {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(8));
        for (int i = 0, r = 0; i < labelFieldPairs.length; i += 2, r++) {
            g.add(new Label((String) labelFieldPairs[i]), 0, r);
            Node f = (Node) labelFieldPairs[i + 1];
            g.add(f, 1, r);
            GridPane.setHgrow(f, Priority.ALWAYS);
        }
        return g;
    }

    /** A card with a big number, a caption and an optional change line. */
    public static final class Kpi extends VBox {
        private final Label value = new Label("-"), delta = new Label(" ");

        public Kpi(String caption) {
            super(2);
            setPadding(new Insets(10, 14, 10, 14));
            setStyle("-fx-background-color:" + GREEN_LIGHT + ";-fx-border-color:#A5D6A7;");
            value.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + GREEN_DARK + ";");
            Label c = new Label(caption);
            c.setStyle("-fx-text-fill:" + GREY + ";");
            getChildren().addAll(value, c, delta);
            setMaxWidth(Double.MAX_VALUE);
        }

        public void set(String text) { value.setText(text); delta.setText(" "); }

        public void set(String text, double current, double previous, int days) {
            value.setText(text);
            if (previous <= 0 && current <= 0) { delta.setText("no activity"); delta.setStyle("-fx-text-fill:" + GREY + ";"); return; }
            if (previous <= 0) { delta.setText("new vs previous " + days + " days"); delta.setStyle("-fx-text-fill:#2E7D32;"); return; }
            double pct = (current - previous) / previous * 100.0;
            String arrow = pct > 0.5 ? "▲ " : pct < -0.5 ? "▼ " : "= ";
            delta.setText(String.format("%s%+.0f%% vs previous %d days", arrow, pct, days));
            delta.setStyle("-fx-text-fill:" + (pct > 0.5 ? "#2E7D32" : pct < -0.5 ? "#C62828" : GREY) + ";");
        }
    }

    /** Read-only table of JSON objects; columns are described by functions over the row map. */
    public static final class Grid extends TableView<Map<String, Object>> {
        @SuppressWarnings("deprecation")
        public Grid() {
            setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);
            setPlaceholder(new Label("Nothing to show"));
        }

        public Grid col(String title, Function<Map<String, Object>, Object> f) {
            TableColumn<Map<String, Object>, String> c = new TableColumn<>(title);
            c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Ui.s(f.apply(cd.getValue()))));
            getColumns().add(c);
            return this;
        }

        public Grid key(String title, String key) { return col(title, m -> m.get(key)); }

        public void setRows(List<Map<String, Object>> rows) {
            Map<String, Object> sel = selected();
            Object keepId = sel == null ? null : sel.get("id");
            getItems().setAll(rows);
            if (keepId instanceof Number) {
                for (Map<String, Object> r : rows)
                    if (r.get("id") instanceof Number && ((Number) r.get("id")).intValue() == ((Number) keepId).intValue()) getSelectionModel().select(r);
            }
        }

        public Map<String, Object> selected() { return getSelectionModel().getSelectedItem(); }

        public void onSelect(Consumer<Map<String, Object>> c) {
            getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> c.accept(now));
        }
    }

    public static String enc(String s) { return ApiClient.enc(s); }
}
