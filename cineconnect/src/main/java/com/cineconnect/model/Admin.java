package com.cineconnect.model;

public class Admin extends User {
    public Admin(int id, String username, String fullName) {
        super(id, username, fullName);
    }

    @Override
    public Role getRole() { return Role.ADMIN; }

    @Override
    public String getDashboardTitle() { return "CineConnect - Admin Dashboard"; }

    @Override
    public String getWelcomeMessage() {
        return "Administrator: " + getFullName();
    }
}
