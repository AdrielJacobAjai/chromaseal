package com.cineconnect.ui;

import com.cineconnect.exception.CineConnectException;

import javax.swing.JOptionPane;
import java.awt.Component;

final class Dialogs {
    private Dialogs() { }

    static void error(Component parent, Throwable t) {
        String msg = t instanceof CineConnectException ? t.getMessage() : "Unexpected error: " + t;
        JOptionPane.showMessageDialog(parent, msg, "CineConnect", JOptionPane.WARNING_MESSAGE);
    }

    static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "CineConnect", JOptionPane.INFORMATION_MESSAGE);
    }
}
