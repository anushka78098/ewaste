package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/** Main window: green header bar (user, points, unread) and one tab per page. */
final class MainView {
    private MainView() {}

    static void show(Stage stage, App app, Runnable onLogout) {
        List<FxPage> pages = app.isAdmin() ? AdminPages.create(app)
                : app.isRecycler() ? RecyclerPages.create(app) : UserPages.create(app);

        Label who = new Label(), stats = new Label();
        who.setFont(Font.font(null, FontWeight.BOLD, 15));
        who.setStyle("-fx-text-fill:white;");
        stats.setStyle("-fx-text-fill:#E8F5E9;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Timeline poll = new Timeline();
        Button logout = new Button("Log out");
        logout.setOnAction(e -> {
            poll.stop();
            app.api.logout();
            onLogout.run();
        });
        HBox header = new HBox(10, new VBox(2, who, stats), spacer, logout);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setStyle("-fx-background-color:" + Fx.GREEN + ";");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (FxPage p : pages) tabs.getTabs().add(new Tab(p.title, p));
        for (int i = 0; i < pages.size(); i++) {
            FxPage p = pages.get(i);
            tabs.getTabs().get(i).setOnSelectionChanged(e -> {
                if (((Tab) e.getSource()).isSelected()) { p.refresh(); refreshHeader(app, who, stats); }
            });
        }

        app.refreshHeader = () -> refreshHeader(app, who, stats);
        app.goTo = title -> {
            for (int i = 0; i < pages.size(); i++) if (pages.get(i).title.equals(title)) { tabs.getSelectionModel().select(i); return; }
        };
        for (FxPage p : pages) if (p instanceof FxMarketPages.Messages) {
            FxMarketPages.Messages messages = (FxMarketPages.Messages) p;
            app.openConversation = messages::openConversation;
        }
        poll.getKeyFrames().add(new KeyFrame(Duration.seconds(30), e -> refreshHeader(app, who, stats)));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);
        stage.setTitle("E-Waste Manager - " + (app.isAdmin() ? "Admin" : app.isRecycler() ? "Recycler" : "User"));
        stage.setScene(new Scene(root, 1100, 700));
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        who.setText(Ui.s(app.user.get("fullName")) + "  (" + Ui.s(app.user.get("username")) + ")");
        refreshHeader(app, who, stats);
        pages.get(0).refresh();
    }

    /** Silent: a background poll must not pop up an error every 30 seconds if the server is down. */
    private static void refreshHeader(App app, Label who, Label stats) {
        Task<Map<String, Object>> t = new Task<Map<String, Object>>() {
            @Override protected Map<String, Object> call() { return ApiClient.obj(app.api.get("/api/me")); }
        };
        t.setOnSucceeded(e -> {
            Map<String, Object> me = t.getValue();
            app.user = me;
            who.setText(Ui.s(me.get("fullName")) + "  (" + Ui.s(me.get("username")) + ")");
            if (app.isAdmin()) stats.setText("Administrator");
            else {
                int unread = Ui.i(me.get("unread"));
                stats.setText((app.isRecycler() ? "Recycler   |   " : "")
                        + Ui.i(me.get("points")) + " reward points   |   "
                        + (unread == 0 ? "no new notifications" : unread + " unread notification" + (unread > 1 ? "s" : "")));
            }
        });
        t.setOnFailed(e -> stats.setText("Server not reachable"));
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }
}
