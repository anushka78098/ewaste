package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Main workspace with role-aware side navigation and a live account header. */
final class MainView {
    private static final String SIDEBAR = "#082B5B";
    private static final String SIDEBAR_HOVER = "#104B83";
    private static final String SIDEBAR_ACTIVE = "#125BA7";

    private MainView() {}

    static void show(Stage stage, App app, Runnable onLogout) {
        show(stage, app, onLogout, null);
    }

    static void show(Stage stage, App app, Runnable onLogout, String initialPage) {
        List<FxPage> pages = app.isAdmin() ? AdminPages.create(app)
                : app.isRecycler() ? RecyclerPages.create(app) : UserPages.create(app);
        StackPane pageHost = new StackPane();
        pageHost.setStyle("-fx-background-color:#F4F7FB;");
        int[] selectedIndex = {-1};
        Label who = new Label();
        Label stats = new Label();
        Label pageTitle = new Label();
        who.setStyle("-fx-text-fill:#34483B;-fx-font-weight:bold;-fx-font-size:12px;");
        stats.setPadding(new Insets(8, 12, 8, 12));
        stats.setStyle("-fx-background-color:#E8F5E9;-fx-background-radius:10;-fx-text-fill:#1B5E20;-fx-font-weight:bold;");
        pageTitle.setFont(Font.font(null, FontWeight.BOLD, 19));
        pageTitle.setStyle("-fx-text-fill:#20342A;");

        Timeline poll = new Timeline();
        Button logout = Fx.button("Log out", () -> {
            poll.stop();
            app.api.logout();
            onLogout.run();
        });
        HBox account = new HBox(12, who, stats, logout);
        account.setAlignment(Pos.CENTER_RIGHT);
        VBox titleBlock = new VBox(2, Fx.muted("YOUR WORKSPACE"), pageTitle);
        HBox header = new HBox(16, titleBlock, spacer(), account);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 22, 12, 22));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #E2E9E4 transparent;");

        VBox menu = new VBox(4);
        menu.setPadding(new Insets(8));
        menu.setFillWidth(true);
        List<Button> navButtons = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            int index = i;
            Button button = new Button(pages.get(i).title);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(42);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setPadding(new Insets(10, 14, 10, 16));
            button.setFocusTraversable(false);
            button.setOnAction(e -> selectPage(index, pages, navButtons, selectedIndex, pageTitle, pageHost, app, who, stats));
            button.setOnMouseEntered(e -> {
                if (selectedIndex[0] != index) button.setStyle(navStyle(SIDEBAR_HOVER, "#FFFFFF", false));
            });
            button.setOnMouseExited(e -> updateNavigation(navButtons, selectedIndex[0]));
            navButtons.add(button);
            menu.getChildren().add(button);
        }

        ScrollPane menuScroll = new ScrollPane(menu);
        menuScroll.setFitToWidth(true);
        menuScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        menuScroll.setStyle("-fx-background:" + SIDEBAR + ";-fx-background-color:" + SIDEBAR + ";-fx-border-color:transparent;");
        VBox.setVgrow(menuScroll, Priority.ALWAYS);

        Label mark = new Label("EW");
        mark.setMinSize(40, 40);
        mark.setAlignment(Pos.CENTER);
        mark.setStyle("-fx-background-color:#08A5C7;-fx-background-radius:10;-fx-text-fill:white;-fx-font-weight:bold;");
        Label brandName = new Label("E-WASTE");
        brandName.setStyle("-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:15px;");
        Label brandCaption = new Label("CIRCULAR OPERATIONS");
        brandCaption.setStyle("-fx-text-fill:#B9D2EA;-fx-font-size:9px;");
        VBox brandText = new VBox(2, brandName, brandCaption);
        HBox brand = new HBox(10, mark, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(22, 18, 18, 18));
        Label role = new Label(app.isAdmin() ? "ADMIN WORKSPACE"
                : app.isRecycler() ? "RECYCLER WORKSPACE" : "MEMBER WORKSPACE");
        role.setPadding(new Insets(14, 18, 18, 18));
        role.setStyle("-fx-text-fill:#B9D2EA;-fx-font-size:10px;-fx-font-weight:bold;");
        VBox sidebar = new VBox(8, brand, menuScroll, role);
        sidebar.setPrefWidth(238);
        sidebar.setMinWidth(220);
        sidebar.setStyle("-fx-background-color:" + SIDEBAR + ";");

        int initialIndex = 0;
        if (initialPage != null) {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).title.equals(initialPage)) {
                    initialIndex = i;
                    break;
                }
            }
        }

        app.refreshHeader = () -> refreshHeader(app, who, stats);
        app.goTo = title -> {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).title.equals(title)) {
                    selectPage(i, pages, navButtons, selectedIndex, pageTitle, pageHost, app, who, stats);
                    return;
                }
            }
        };
        for (FxPage page : pages) if (page instanceof FxMarketPages.Messages) {
            FxMarketPages.Messages messages = (FxMarketPages.Messages) page;
            app.openConversation = messages::openConversation;
        }
        selectPage(initialIndex, pages, navButtons, selectedIndex, pageTitle, pageHost, app, who, stats);

        poll.getKeyFrames().add(new KeyFrame(Duration.seconds(30), e -> refreshHeader(app, who, stats)));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setLeft(sidebar);
        root.setCenter(pageHost);
        root.setStyle("-fx-background-color:#F4F7FB;");
        stage.setTitle("E-Waste Manager - " + (initialPage != null ? initialPage.replace(" dashboard", "")
                : app.isAdmin() ? "Admin" : app.isRecycler() ? "Recycler" : "User"));
        stage.setScene(new Scene(root, 1280, 820));
        stage.setMinWidth(1040);
        stage.setMinHeight(620);
        who.setText(Ui.s(app.user.get("fullName")) + "  ·  " + Ui.s(app.user.get("username")));
        refreshHeader(app, who, stats);
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static void selectPage(int index, List<FxPage> pages, List<Button> navButtons,
                                   int[] selectedIndex, Label pageTitle, StackPane pageHost, App app,
                                   Label who, Label stats) {
        if (index < 0 || index >= pages.size()) return;
        selectedIndex[0] = index;
        FxPage page = pages.get(index);
        pageTitle.setText(page.title);
        pageHost.getChildren().setAll(page);
        updateNavigation(navButtons, index);
        page.refresh();
        refreshHeader(app, who, stats);
    }

    private static void updateNavigation(List<Button> buttons, int selectedIndex) {
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            boolean selected = i == selectedIndex;
            button.setStyle(navStyle(selected ? SIDEBAR_ACTIVE : SIDEBAR,
                    selected ? "#FFFFFF" : "#D0DED5", selected));
        }
    }

    private static String navStyle(String background, String foreground, boolean selected) {
        return "-fx-background-color:" + background + ";-fx-background-radius:9;"
                + "-fx-text-fill:" + foreground + ";-fx-font-weight:" + (selected ? "bold" : "normal")
                + ";-fx-font-size:12px;-fx-cursor:hand;";
    }

    /** Silent: a background poll must not pop up an error every 30 seconds if the server is down. */
    private static void refreshHeader(App app, Label who, Label stats) {
        Task<Map<String, Object>> task = new Task<Map<String, Object>>() {
            @Override protected Map<String, Object> call() { return ApiClient.obj(app.api.get("/api/me")); }
        };
        task.setOnSucceeded(e -> {
            Map<String, Object> me = task.getValue();
            app.user = me;
            if (who != null) who.setText(Ui.s(me.get("fullName")) + "  ·  " + Ui.s(me.get("username")));
            if (stats != null) {
                if (app.isAdmin()) stats.setText("Administrator");
                else {
                    int unread = Ui.i(me.get("unread"));
                    stats.setText(Ui.i(me.get("points")) + " points  ·  "
                            + (unread == 0 ? "all caught up" : unread + " unread"));
                }
            }
        });
        task.setOnFailed(e -> {
            if (stats != null) stats.setText("Server not reachable");
        });
        Thread thread = new Thread(task, "header-refresh");
        thread.setDaemon(true);
        thread.start();
    }
}
