package com.cineconnect;

import com.cineconnect.db.Database;
import com.cineconnect.exception.CineConnectException;
import com.cineconnect.ui.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.time.Clock;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        AppContext ctx;
        try {
            Database db = Database.getInstance();
            db.initSchema();
            ctx = new AppContext(db, Clock.systemUTC(), AppContext.HOLD_SECONDS);
            SeedData.seedIfEmpty(ctx.getAuthService(), ctx.getCatalogService());
        } catch (CineConnectException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to MySQL:\n" + e.getMessage()
                            + "\n\nCheck config/db.properties (see config/db.properties.example).",
                    "CineConnect", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ctx.getHoldManager().start();
        AppContext app = ctx;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            new LoginFrame(app).setVisible(true);
        });
    }
}
