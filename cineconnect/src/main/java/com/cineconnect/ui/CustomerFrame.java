package com.cineconnect.ui;

import com.cineconnect.AppContext;
import com.cineconnect.exception.CineConnectException;
import com.cineconnect.model.Booking;
import com.cineconnect.model.Seat;
import com.cineconnect.model.SeatStatus;
import com.cineconnect.model.Show;
import com.cineconnect.model.User;
import com.cineconnect.observer.SeatEventListener;
import com.cineconnect.service.BookingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CustomerFrame extends JFrame {
    private final AppContext ctx;
    private final User user;
    private final BookingService booking;

    private final DefaultTableModel showModel = readOnlyModel("Movie", "Theatre", "Screen", "Starts", "From");
    private final JTable showTable = new JTable(showModel);
    private final JTextField search = new JTextField(10);
    private final SeatPanel seatPanel = new SeatPanel();
    private final JLabel selectionLabel = new JLabel("Pick a show on the left");
    private final JLabel holdLabel = new JLabel(" ");
    private final JSpinner groupSize = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
    private final JButton holdButton = new JButton("Hold selected seats");
    private final JButton payButton = new JButton("Pay & confirm");
    private final JButton releaseButton = new JButton("Release hold");

    private final DefaultTableModel bookingModel = readOnlyModel("Id", "Movie", "Theatre", "Starts", "Seats", "Total", "Status");
    private final JTable bookingTable = new JTable(bookingModel);

    private List<Show> shows = new ArrayList<>();
    private Show currentShow;
    private final Set<String> selected = new LinkedHashSet<>();
    private List<Seat> currentSeats = new ArrayList<>();
    private long myHoldExpiry;

    private final SeatEventListener listener = showId -> SwingUtilities.invokeLater(() -> {
        if (currentShow != null && currentShow.getId() == showId) {
            refreshSeats();
        }
    });
    private final Timer ticker = new Timer(1000, e -> tick());

    public CustomerFrame(AppContext ctx, User user) {
        super(user.getDashboardTitle());
        this.ctx = ctx;
        this.user = user;
        this.booking = ctx.getBookingService();
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Book tickets", buildBookTab());
        tabs.addTab("My bookings", buildBookingsTab());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                loadBookings();
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        header.add(new JLabel(user.getWelcomeMessage()), BorderLayout.WEST);
        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> logout());
        header.add(logout, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setSize(1250, 660);
        setLocationRelativeTo(null);

        ctx.getEventBus().subscribe(listener);
        ticker.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ctx.getEventBus().unsubscribe(listener);
                ticker.stop();
            }
        });
        loadShows();
    }

    private JPanel buildBookTab() {
        showTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        showTable.setRowHeight(24);
        showTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onShowChosen();
            }
        });

        JButton go = new JButton("Search");
        go.addActionListener(e -> loadShows());
        search.addActionListener(e -> loadShows());
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Movie / genre / theatre:"));
        searchRow.add(search);
        searchRow.add(go);

        JPanel left = new JPanel(new BorderLayout());
        left.add(searchRow, BorderLayout.NORTH);
        left.add(new JScrollPane(showTable), BorderLayout.CENTER);

        JButton recommend = new JButton("Recommend best seats");
        recommend.addActionListener(e -> recommend());
        holdButton.addActionListener(e -> hold());
        payButton.addActionListener(e -> pay());
        releaseButton.addActionListener(e -> release());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Seats together:"));
        controls.add(groupSize);
        controls.add(recommend);
        controls.add(holdButton);
        controls.add(payButton);
        controls.add(releaseButton);

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
        status.add(selectionLabel, BorderLayout.WEST);
        status.add(holdLabel, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.NORTH);
        south.add(controls, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        right.add(new JScrollPane(seatPanel), BorderLayout.CENTER);
        right.add(south, BorderLayout.SOUTH);

        seatPanel.setOnSeatClicked(this::toggleSeat);
        updateButtons();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.0);
        split.setDividerLocation(500);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(split, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildBookingsTab() {
        bookingTable.setRowHeight(24);
        bookingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton cancel = new JButton("Cancel selected booking");
        cancel.addActionListener(e -> cancelSelected());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadBookings());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(cancel);
        bar.add(refresh);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ---------------------------------------------------------------- shows

    private void loadShows() {
        try {
            shows = ctx.getCatalogService().listShows(search.getText());
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
            return;
        }
        showModel.setRowCount(0);
        for (Show s : shows) {
            showModel.addRow(new Object[]{s.getMovieTitle() + " (" + s.getGenre() + ")", s.getTheatreName(),
                    s.getScreen(), Fmt.time(s.getStartTime()), Fmt.money(s.getBasePrice())});
        }
        currentShow = null;
        selected.clear();
        refreshSeats();
    }

    private void onShowChosen() {
        int row = showTable.getSelectedRow();
        currentShow = row >= 0 && row < shows.size() ? shows.get(row) : null;
        selected.clear();
        refreshSeats();
    }

    // ---------------------------------------------------------------- seats

    private void refreshSeats() {
        if (currentShow == null) {
            currentSeats = new ArrayList<>();
            myHoldExpiry = 0;
            seatPanel.render(currentSeats, user.getId(), selected);
            selectionLabel.setText("Pick a show on the left");
            holdLabel.setText(" ");
            updateButtons();
            return;
        }
        try {
            currentSeats = booking.getSeatMap(currentShow.getId());
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
            return;
        }
        Set<String> stillAvailable = new LinkedHashSet<>();
        myHoldExpiry = 0;
        for (Seat s : currentSeats) {
            if (s.isAvailable()) {
                stillAvailable.add(s.getLabel());
            } else if (s.getStatus() == SeatStatus.HELD && s.getHeldBy() != null && s.getHeldBy() == user.getId()) {
                myHoldExpiry = myHoldExpiry == 0 ? s.getHoldExpires() : Math.min(myHoldExpiry, s.getHoldExpires());
            }
        }
        selected.retainAll(stillAvailable);
        seatPanel.render(currentSeats, user.getId(), selected);
        updateSelectionLabel();
        tick();
        updateButtons();
    }

    private void toggleSeat(String label) {
        if (!selected.remove(label)) {
            selected.add(label);
        }
        seatPanel.render(currentSeats, user.getId(), selected);
        updateSelectionLabel();
        updateButtons();
    }

    private void updateSelectionLabel() {
        if (selected.isEmpty()) {
            selectionLabel.setText(currentShow.getMovieTitle() + " - click seats to select");
            return;
        }
        double total = 0;
        for (Seat s : currentSeats) {
            if (selected.contains(s.getLabel())) {
                total += s.getPrice();
            }
        }
        selectionLabel.setText("Selected: " + String.join(", ", selected) + "  =  " + Fmt.money(total));
    }

    private void updateButtons() {
        boolean hasShow = currentShow != null;
        boolean hasHold = myHoldExpiry > 0;
        holdButton.setEnabled(hasShow && !selected.isEmpty());
        payButton.setEnabled(hasShow && hasHold);
        releaseButton.setEnabled(hasShow && hasHold);
    }

    private void tick() {
        if (myHoldExpiry <= 0) {
            holdLabel.setText(" ");
            return;
        }
        long left = (myHoldExpiry - System.currentTimeMillis()) / 1000;
        if (left <= 0) {
            myHoldExpiry = 0;
            holdLabel.setText(" ");
            refreshSeats();
            return;
        }
        holdLabel.setText(String.format("Your hold expires in %d:%02d", left / 60, left % 60));
    }

    // ---------------------------------------------------------------- actions

    private void recommend() {
        if (currentShow == null) {
            Dialogs.info(this, "Pick a show first.");
            return;
        }
        int n = (Integer) groupSize.getValue();
        List<Seat> picks = booking.recommendSeats(currentShow.getId(), n);
        if (picks.isEmpty()) {
            Dialogs.info(this, "Not enough free seats for " + n + " people in this show.");
            return;
        }
        selected.clear();
        for (Seat s : picks) {
            selected.add(s.getLabel());
        }
        seatPanel.render(currentSeats, user.getId(), selected);
        updateSelectionLabel();
        updateButtons();
    }

    private void hold() {
        try {
            booking.holdSeats(user.getId(), currentShow.getId(), new ArrayList<>(selected));
            selected.clear();
        } catch (CineConnectException ex) {
            Dialogs.error(this, ex);
        }
        refreshSeats();
    }

    private void pay() {
        try {
            Booking b = booking.confirmBooking(user.getId(), currentShow.getId());
            Dialogs.info(this, "Booking #" + b.getId() + " confirmed!\n" + b.getMovieTitle() + "\n"
                    + Fmt.time(b.getShowStart()) + "\nSeats: " + String.join(", ", b.getSeats())
                    + "\nTotal: " + Fmt.money(b.getTotal()));
        } catch (CineConnectException ex) {
            Dialogs.error(this, ex);
        }
        refreshSeats();
    }

    private void release() {
        try {
            booking.releaseHolds(user.getId(), currentShow.getId());
        } catch (CineConnectException ex) {
            Dialogs.error(this, ex);
        }
        refreshSeats();
    }

    // ---------------------------------------------------------------- my bookings

    private void loadBookings() {
        try {
            List<Booking> list = booking.getBookingsForUser(user.getId());
            bookingModel.setRowCount(0);
            for (Booking b : list) {
                bookingModel.addRow(new Object[]{b.getId(), b.getMovieTitle(), b.getTheatreName(),
                        Fmt.time(b.getShowStart()), String.join(", ", b.getSeats()), Fmt.money(b.getTotal()), b.getStatus()});
            }
        } catch (RuntimeException ex) {
            Dialogs.error(this, ex);
        }
    }

    private void cancelSelected() {
        int row = bookingTable.getSelectedRow();
        if (row < 0) {
            Dialogs.info(this, "Select a booking first.");
            return;
        }
        int id = (Integer) bookingModel.getValueAt(row, 0);
        try {
            booking.cancelBooking(id, user.getId());
            Dialogs.info(this, "Booking #" + id + " cancelled.");
        } catch (CineConnectException ex) {
            Dialogs.error(this, ex);
        }
        loadBookings();
    }

    private void logout() {
        dispose();
        new LoginFrame(ctx).setVisible(true);
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }
}
