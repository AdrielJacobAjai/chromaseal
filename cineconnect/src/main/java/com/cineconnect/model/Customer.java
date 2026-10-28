package com.cineconnect.model;

public class Customer extends User {
    public Customer(int id, String username, String fullName) {
        super(id, username, fullName);
    }

    @Override
    public Role getRole() { return Role.CUSTOMER; }

    @Override
    public String getDashboardTitle() { return "CineConnect - Book Tickets"; }
}
