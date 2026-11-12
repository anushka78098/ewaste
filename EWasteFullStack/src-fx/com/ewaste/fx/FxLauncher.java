package com.ewaste.fx;

/**
 * Entry point. It deliberately does NOT extend Application: launching an Application subclass directly from the
 * classpath fails with "JavaFX runtime components are missing" on some JDKs, a plain main class avoids that.
 */
public final class FxLauncher {
    private FxLauncher() {}

    public static void main(String[] args) {
        javafx.application.Application.launch(FxMain.class, args);
    }
}
