package com.ewaste.fx;

import com.ewaste.client.App;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.*;

/** One page of the main window. refresh() reloads its data from the server. */
abstract class FxPage extends BorderPane {
    final App app;
    final String title;

    FxPage(App app, String title) {
        this.app = app;
        this.title = title;
        setPadding(new Insets(12));
        setStyle("-fx-background-color:#F4F7FB;");
    }

    abstract void refresh();

    /** Heading on the left, action buttons on the right. */
    Node header(String heading, Node... actions) {
        HBox row = new HBox(8);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(Fx.heading(heading), spacer);
        row.getChildren().addAll(actions);
        row.setPadding(new Insets(0, 0, 8, 0));
        return row;
    }
}
