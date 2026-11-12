package com.ewaste.client;

import javax.swing.*;

/** Desktop client. Usage: ClientMain [serverUrl]   (default http://localhost:8080) */
public class ClientMain {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : System.getProperty("ewaste.server", "http://localhost:8080");
        SwingUtilities.invokeLater(() -> {
            Ui.installLookAndFeel();
            new LoginFrame(url).setVisible(true);
        });
    }
}
