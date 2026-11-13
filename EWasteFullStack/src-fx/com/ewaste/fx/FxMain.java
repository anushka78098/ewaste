package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.LoginMode;
import com.ewaste.client.Ui;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/** JavaFX client: sign-in window, then the same user / admin screens as the Swing client. */
public class FxMain extends Application {
    private Stage stage;
    private String serverUrl = "http://localhost:8080";

    @Override public void start(Stage primary) {
        this.stage = primary;
        String arg = getParameters().getRaw().isEmpty() ? System.getenv("EWASTE_SERVER") : getParameters().getRaw().get(0);
        if (arg != null && !arg.isBlank()) serverUrl = arg;
        showLogin();
        stage.show();
    }

    void showLogin() {
        stage.setTitle("E-Waste Manager - Sign in");
        TextField server = new TextField(serverUrl);
        TextField user = new TextField();
        PasswordField pass = new PasswordField();
        ComboBox<LoginMode> mode = new ComboBox<>();
        mode.getItems().addAll(LoginMode.values());
        mode.getSelectionModel().select(LoginMode.BUYER);
        Label status = Fx.muted(" ");

        Label t = new Label("E-Waste Manager");
        t.setFont(Font.font(null, FontWeight.BOLD, 24));
        t.setStyle("-fx-text-fill:white;");
        Label sub = new Label("Reuse. Repair. Recycle responsibly.");
        sub.setStyle("-fx-text-fill:#D5E9FF;");
        VBox banner = new VBox(4, t, sub);
        banner.setPadding(new Insets(18, 24, 18, 24));
        banner.setStyle("-fx-background-color:" + Fx.GREEN + ";");

        Button login = Fx.primary("Sign in", () -> doLogin(server.getText(), user.getText().trim(), pass.getText(), pass, status,
                (LoginMode) mode.getValue()));
        login.setDefaultButton(true);
        Button register = Fx.button("Create account...", () -> register(server.getText(), user, status));
        Button track = Fx.button("Track an item...", () -> track(server.getText()));

        VBox body = new VBox(10, Fx.form("Server", server, "Login as", mode, "Username", user, "Password", pass),
                new HBox(8, login, register, track), status);
        body.setPadding(new Insets(16, 24, 16, 24));
        BorderPane root = new BorderPane();
        root.setTop(banner);
        root.setCenter(body);
        stage.setScene(new Scene(root, 460, 340));
        checkServer(server.getText(), status);
    }

    private void checkServer(String url, Label status) {
        ApiClient c = new ApiClient(url);
        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<String>() {
            @Override protected String call() {
                try { return "Server online (" + ApiClient.obj(c.get("/api/health")).get("store") + ")"; }
                catch (RuntimeException e) { return "Server not reachable - start it first (see README)."; }
            }
        };
        task.setOnSucceeded(e -> status.setText(task.getValue()));
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    private void doLogin(String url, String u, String p, PasswordField pass, Label status, LoginMode selectedMode) {
        ApiClient api = new ApiClient(url);
        App app = new App(api);
        serverUrl = url;
        status.setText("Signing in...");
        Fx.run(() -> {
            app.user = api.login(u, p);
            if (selectedMode == null || !selectedMode.matchesRole(Ui.s(app.user.get("role")))) {
                api.logout();
                throw new IllegalArgumentException("The selected sign-in mode does not match this account.");
            }
            app.loadMeta();
            return app;
        }, a -> {
            pass.clear();
            MainView.show(stage, a, this::showLogin, selectedMode.dashboardTitle());
        }, () -> status.setText("Sign-in failed."));
    }

    private void register(String url, TextField userField, Label status) {
        TextField u = new TextField(), name = new TextField(), email = new TextField(), phone = new TextField(), addr = new TextField();
        PasswordField pw = new PasswordField();
        if (!Fx.dialog("Create account", Fx.form("Username *", u, "Password * (min 6)", pw, "Full name *", name, "Email *", email, "Phone", phone, "Address", addr))) return;
        ApiClient api = new ApiClient(url);
        Map<String, Object> body = ApiClient.map("username", u.getText().trim(), "password", pw.getText(), "fullName", name.getText().trim(),
                "email", email.getText().trim(), "phone", phone.getText().trim(), "address", addr.getText().trim());
        Fx.run(() -> api.post("/api/auth/register", body), r -> {
            userField.setText(u.getText().trim());
            status.setText("Account created - sign in above.");
        });
    }

    /** Public tracker: anyone with a code (EW-yyyy-nnnnn) can see progress, no account needed. */
    private void track(String url) {
        String code = Fx.ask("Track an item", "Tracking code (looks like EW-2026-00012):", "");
        if (code == null || code.isBlank()) return;
        ApiClient api = new ApiClient(url);
        Fx.run(() -> ApiClient.obj(api.get("/api/track/" + ApiClient.enc(code.trim()))), r -> {
            StringBuilder sb = new StringBuilder();
            sb.append(Ui.s(r.get("trackingCode"))).append("  -  ").append(Ui.s(r.get("categoryLabel"))).append("\n");
            sb.append("Status: ").append(Ui.s(r.get("statusLabel"))).append("\n");
            if (r.get("pickupDate") != null) sb.append("Pickup date: ").append(Ui.s(r.get("pickupDate"))).append("\n");
            sb.append("\n");
            for (Object o : (List<?>) r.get("steps")) {
                Map<String, Object> st = ApiClient.obj(o);
                sb.append(Boolean.TRUE.equals(st.get("done")) ? "[x] " : "[ ] ").append(Ui.s(st.get("label"))).append("\n");
            }
            Fx.text("Item progress", sb.toString());
        });
    }
}
