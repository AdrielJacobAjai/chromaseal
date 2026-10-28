package com.cineconnect.model;

public abstract class Seat {
    private final String label;
    private final int row;
    private final int col;
    private double price;
    private SeatStatus status = SeatStatus.AVAILABLE;
    private Integer heldBy;
    private long holdExpires;

    protected Seat(String label, int row, int col, double price) {
        this.label = label;
        this.row = row;
        this.col = col;
        this.price = price;
    }

    public abstract String getType();

    public abstract double getPriceMultiplier();

    public String getLabel() { return label; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public Integer getHeldBy() { return heldBy; }
    public void setHeldBy(Integer heldBy) { this.heldBy = heldBy; }
    public long getHoldExpires() { return holdExpires; }
    public void setHoldExpires(long holdExpires) { this.holdExpires = holdExpires; }

    public boolean isAvailable() { return status == SeatStatus.AVAILABLE; }

    @Override
    public String toString() { return label; }
}
