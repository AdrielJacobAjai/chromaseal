package com.cineconnect.ui;

import com.cineconnect.AppContext;
import com.cineconnect.model.Movie;
import com.cineconnect.model.Theatre;
import com.cineconnect.model.User;
import com.cineconnect.service.DashboardService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class AdminFrame extends JFrame {
    private final AppContext ctx;

    private final JLabel revenueLabel = bigLabel();
    private final JLabel occupancyLabel = bigLabel();
    private final JLabel busiestLabel = bigLabel();
    private final DefaultTableModel statsModel = CustomerFrame.readOnlyModel("Movie", "Theatre", "Starts", "Booked", "Seats", "Occupancy", "Revenue");
    private final DefaultTableModel movieRevenueModel = CustomerFrame.readOnlyModel("Movie", "Revenue");
    private final DefaultTableModel slotModel = CustomerFrame.readOnlyModel("Start hour", "Seats booked");
    private final Timer autoRefresh = new Timer(10_000, e -> loadDashboard());

    private final JComboBox<Movie> movieBox = new JComboBox<>();
    private final JComboBox<Theatre> theatreBox = new JComboBox<>();

    public AdminFrame(AppContext ctx, User user) {
        super(user.getDashboardTitle());
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", buildDashboard());
        tabs.addTab("Manage catalogue", buildCatalogue());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        header.add(new JLabel(user.getWelcomeMessage()), BorderLayout.WEST);
        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> {
            autoRefresh.stop();
            dispose();
            new LoginFrame(ctx).setVisible(true);
        });
        header.add(logout, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setSize(1100, 640);
        setLocationRelativeTo(null);
        loadDashboard();
        loadChoices();
        autoRefresh.start();
    }

    private static JLabel bigLabel() {
        JLabel l = new JLabel("-", JLabel.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 22f));
        return l;
    }

    private static JPanel card(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildDashboard() {
        JPanel cards = new JPanel(new GridLayout(1, 3, 10, 0));
        cards.add(card("Total revenue", revenueLabel));
        cards.add(card("Overall occupancy", occupancyLabel));
        cards.add(card("Busiest show time", busiestLabel));

        JTable stats = new JTable(statsModel);
        stats.setRowHeight(24);
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Occupancy and revenue per show"));
        statsPanel.add(new JScrollPane(stats), BorderLayout.CENTER);

        JPanel side = new JPanel(new GridLayout(2, 1, 0, 10));
        JPanel slots = new JPanel(new BorderLayout());
        slots.setBorder(BorderFactory.createTitledBorder("Busiest show timings"));
        slots.add(new JScrollPane(new JTable(slotModel)), BorderLayout.CENTER);
        JPanel byMovie = new JPanel(new BorderLayout());
        byMovie.setBorder(BorderFactory.createTitledBorder("Revenue by movie"));
        byMovie.add(new JScrollPane(new JTable(movieRevenueModel)), BorderLayout.CENTER);
        side.add(slots);
        side.add(byMovie);
        side.setPreferredSize(new java.awt.Dimension(300, 100));

        JButton refresh = new JButton("Refresh now");
        refresh.addActionListener(e -> loadDashboard());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(refresh);
        bottom.add(new JLabel("(updates itself every 10 seconds)"));

        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(cards, BorderLayout.NORTH);
        p.add(statsPanel, BorderLayout.CENTER);
        p.add(side, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void loadDashboard() {
        try {
            DashboardService d = ctx.getDashboardService();
            revenueLabel.setText(Fmt.money(d.getTotalRevenue()));
            occupancyLabel.setText(String.format("%.1f%%", d.getOverallOccupancy()));
            List<DashboardService.TimeSlotStats> slots = d.getBusiestTimeSlots();
            busiestLabel.setText(slots.isEmpty() || slots.get(0).bookedSeats() == 0
                    ? "No bookings yet" : String.format("%02d:00 (%d seats)", slots.get(0).hour(), slots.get(0).bookedSeats()));

            statsModel.setRowCount(0);
            for (DashboardService.ShowStats s : d.getShowStats()) {
                statsModel.addRow(new Object[]{s.movieTitle(), s.theatreName(), Fmt.time(s.startTime()),
                        s.bookedSeats(), s.totalSeats(), String.format("%.0f%%", s.occupancyPercent()), Fmt.money(s.revenue())});
            }
            slotModel.setRowCount(0);
            for (DashboardService.TimeSlotStats s : slots) {
                slotModel.addRow(new Object[]{String.format("%02d:00", s.hour()), s.bookedSeats()});
            }
            movieRevenueModel.setRowCount(0);
            for (DashboardService.MovieRevenue m : d.getRevenueByMovie()) {
                movieRevenueModel.addRow(new Object[]{m.movieTitle(), Fmt.money(m.revenue())});
            }
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
        }
    }

    // ---------------------------------------------------------------- catalogue

    private JPanel buildCatalogue() {
        JTextField title = new JTextField(14);
        JTextField genre = new JTextField(10);
        JTextField duration = new JTextField("120", 5);
        JTextField rating = new JTextField("U/A", 5);
        JButton addMovie = new JButton("Add movie");
        addMovie.addActionListener(e -> {
            try {
                ctx.getCatalogService().addMovie(title.getText(), genre.getText(),
                        Integer.parseInt(duration.getText().trim()), rating.getText());
                title.setText("");
                loadChoices();
                Dialogs.info(this, "Movie added.");
            } catch (NumberFormatException ex) {
                Dialogs.info(this, "Duration must be a number of minutes.");
            } catch (RuntimeException ex) {
                Dialogs.error(this, ex);
            }
        });
        JPanel moviePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        moviePanel.setBorder(BorderFactory.createTitledBorder("New movie"));
        moviePanel.add(new JLabel("Title"));
        moviePanel.add(title);
        moviePanel.add(new JLabel("Genre"));
        moviePanel.add(genre);
        moviePanel.add(new JLabel("Minutes"));
        moviePanel.add(duration);
        moviePanel.add(new JLabel("Rating"));
        moviePanel.add(rating);
        moviePanel.add(addMovie);

        JTextField theatreName = new JTextField(16);
        JTextField city = new JTextField(12);
        JButton addTheatre = new JButton("Add theatre");
        addTheatre.addActionListener(e -> {
            try {
                ctx.getCatalogService().addTheatre(theatreName.getText(), city.getText());
                theatreName.setText("");
                loadChoices();
                Dialogs.info(this, "Theatre added.");
            } catch (RuntimeException ex) {
                Dialogs.error(this, ex);
            }
        });
        JPanel theatrePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        theatrePanel.setBorder(BorderFactory.createTitledBorder("New theatre"));
        theatrePanel.add(new JLabel("Name"));
        theatrePanel.add(theatreName);
        theatrePanel.add(new JLabel("City"));
        theatrePanel.add(city);
        theatrePanel.add(addTheatre);

        JTextField screen = new JTextField("Screen 1", 8);
        JTextField when = new JTextField(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 13);
        JTextField rows = new JTextField("8", 3);
        JTextField cols = new JTextField("12", 3);
        JTextField price = new JTextField("180", 5);
        JButton addShow = new JButton("Add show");
        addShow.addActionListener(e -> {
            try {
                Movie m = (Movie) movieBox.getSelectedItem();
                Theatre t = (Theatre) theatreBox.getSelectedItem();
                if (m == null || t == null) {
                    Dialogs.info(this, "Add a movie and a theatre first.");
                    return;
                }
                long start = LocalDateTime.parse(when.getText().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                ctx.getCatalogService().addShow(m.getId(), t.getId(), screen.getText().trim(), start,
                        Integer.parseInt(rows.getText().trim()), Integer.parseInt(cols.getText().trim()),
                        Double.parseDouble(price.getText().trim()));
                loadDashboard();
                Dialogs.info(this, "Show added with all its seats.");
            } catch (DateTimeParseException ex) {
                Dialogs.info(this, "Date and time must look like 2030-01-31 18:30");
            } catch (NumberFormatException ex) {
                Dialogs.info(this, "Rows, columns and price must be numbers.");
            } catch (RuntimeException ex) {
                Dialogs.error(this, ex);
            }
        });
        JPanel showPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        showPanel.setBorder(BorderFactory.createTitledBorder("New show (last third of rows are premium seats, weekends cost 20% more)"));
        showPanel.add(movieBox);
        showPanel.add(theatreBox);
        showPanel.add(new JLabel("Screen"));
        showPanel.add(screen);
        showPanel.add(new JLabel("Start"));
        showPanel.add(when);
        showPanel.add(new JLabel("Rows"));
        showPanel.add(rows);
        showPanel.add(new JLabel("Cols"));
        showPanel.add(cols);
        showPanel.add(new JLabel("Base price"));
        showPanel.add(price);
        showPanel.add(addShow);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(moviePanel);
        p.add(theatrePanel);
        p.add(showPanel);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        return wrap;
    }

    private void loadChoices() {
        movieBox.removeAllItems();
        for (Movie m : ctx.getCatalogService().listMovies()) {
            movieBox.addItem(m);
        }
        theatreBox.removeAllItems();
        for (Theatre t : ctx.getCatalogService().listTheatres()) {
            theatreBox.addItem(t);
        }
    }
}
