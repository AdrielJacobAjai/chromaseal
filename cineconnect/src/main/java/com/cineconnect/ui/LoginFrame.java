package com.cineconnect.ui;

import com.cineconnect.AppContext;
import com.cineconnect.model.Role;
import com.cineconnect.model.User;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class LoginFrame extends JFrame {
    private final AppContext ctx;
    private final JTextField username = new JTextField(16);
    private final JPasswordField password = new JPasswordField(16);

    public LoginFrame(AppContext ctx) {
        super("CineConnect - Login");
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        JLabel title = new JLabel("CineConnect", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        form.add(title, g);
        g.gridy++;
        form.add(new JLabel("Movie ticket booking", JLabel.CENTER), g);

        g.gridwidth = 1;
        g.gridy++;
        form.add(new JLabel("Username"), g);
        g.gridx = 1;
        form.add(username, g);
        g.gridx = 0;
        g.gridy++;
        form.add(new JLabel("Password"), g);
        g.gridx = 1;
        form.add(password, g);

        JButton login = new JButton("Login");
        JButton register = new JButton("Create account");
        JPanel buttons = new JPanel();
        buttons.add(login);
        buttons.add(register);
        g.gridx = 0;
        g.gridy++;
        g.gridwidth = 2;
        form.add(buttons, g);
        g.gridy++;
        form.add(new JLabel("<html><small>Demo: admin / admin123, customer / customer123</small></html>", JLabel.CENTER), g);

        login.addActionListener(e -> doLogin());
        password.addActionListener(e -> doLogin());
        register.addActionListener(e -> doRegister());
        getRootPane().setDefaultButton(login);

        setContentPane(form);
        pack();
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        try {
            User user = ctx.getAuthService().login(username.getText(), new String(password.getPassword()));
            JFrame next = user.getRole() == Role.ADMIN ? new AdminFrame(ctx, user) : new CustomerFrame(ctx, user);
            next.setVisible(true);
            dispose();
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
        }
    }

    private void doRegister() {
        JTextField fullName = new JTextField(16);
        JTextField newUser = new JTextField(16);
        JPasswordField newPass = new JPasswordField(16);
        JPanel p = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Full name"));
        p.add(fullName);
        p.add(new JLabel("Username"));
        p.add(newUser);
        p.add(new JLabel("Password (min 4)"));
        p.add(newPass);
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, p, "Create account",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        try {
            ctx.getAuthService().register(newUser.getText(), new String(newPass.getPassword()), fullName.getText());
            username.setText(newUser.getText().trim());
            password.setText("");
            Dialogs.info(this, "Account created. You can log in now.");
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
        }
    }
}
