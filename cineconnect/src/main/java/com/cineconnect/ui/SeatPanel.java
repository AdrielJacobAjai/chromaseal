package com.cineconnect.ui;

import com.cineconnect.model.Seat;
import com.cineconnect.model.SeatStatus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Draws the hall as a grid of coloured seat buttons under a "SCREEN" bar. */
class SeatPanel extends JPanel {
    static final Color AVAILABLE = new Color(0xA5D6A7);
    static final Color PREMIUM = new Color(0xFFE082);
    static final Color SELECTED = new Color(0x42A5F5);
    static final Color MINE = new Color(0x7E57C2);
    static final Color HELD = new Color(0xFFB74D);
    static final Color BOOKED = new Color(0xEF9A9A);

    private final JPanel grid = new JPanel();
    private Consumer<String> onSeatClicked = label -> { };

    SeatPanel() {
        super(new BorderLayout(0, 10));
        JLabel screen = new JLabel("SCREEN", JLabel.CENTER);
        screen.setOpaque(true);
        screen.setBackground(Color.DARK_GRAY);
        screen.setForeground(Color.WHITE);
        screen.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        add(screen, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(legend(), BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    void setOnSeatClicked(Consumer<String> handler) { this.onSeatClicked = handler; }

    void render(List<Seat> seats, int currentUserId, Set<String> selected) {
        grid.removeAll();
        if (seats.isEmpty()) {
            grid.revalidate();
            grid.repaint();
            return;
        }
        int rows = 0;
        int cols = 0;
        for (Seat s : seats) {
            rows = Math.max(rows, s.getRow() + 1);
            cols = Math.max(cols, s.getCol() + 1);
        }
        grid.setLayout(new GridLayout(rows, cols, 4, 4));
        for (Seat s : seats) {
            JButton b = new JButton(s.getLabel());
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.setPreferredSize(new Dimension(48, 30));
            b.setFocusPainted(false);
            b.setOpaque(true);
            b.setBackground(colourFor(s, currentUserId, selected));
            b.setToolTipText(s.getType() + " - " + Fmt.money(s.getPrice()));
            boolean clickable = s.getStatus() == SeatStatus.AVAILABLE;
            b.setEnabled(clickable);
            if (clickable) {
                b.addActionListener(e -> onSeatClicked.accept(s.getLabel()));
            }
            grid.add(b);
        }
        grid.revalidate();
        grid.repaint();
    }

    private static Color colourFor(Seat s, int currentUserId, Set<String> selected) {
        if (s.getStatus() == SeatStatus.BOOKED) {
            return BOOKED;
        }
        if (s.getStatus() == SeatStatus.HELD) {
            return s.getHeldBy() != null && s.getHeldBy() == currentUserId ? MINE : HELD;
        }
        if (selected.contains(s.getLabel())) {
            return SELECTED;
        }
        return "PREMIUM".equals(s.getType()) ? PREMIUM : AVAILABLE;
    }

    private static JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        p.add(chip(AVAILABLE, "Regular"));
        p.add(chip(PREMIUM, "Premium"));
        p.add(chip(SELECTED, "Selected"));
        p.add(chip(MINE, "Your hold"));
        p.add(chip(HELD, "Held by others"));
        p.add(chip(BOOKED, "Booked"));
        return p;
    }

    private static JLabel chip(Color c, String text) {
        JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(c);
        return l;
    }
}
