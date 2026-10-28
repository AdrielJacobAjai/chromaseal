package com.cineconnect.model;

/** Base class for everyone who can log in. Role-specific behaviour is supplied by subclasses. */
public abstract class User {
    private final int id;
    private final String username;
    private final String fullName;

    protected User(int id, String username, String fullName) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }

    public abstract Role getRole();

    public abstract String getDashboardTitle();

    public String getWelcomeMessage() {
        return "Welcome, " + fullName;
    }

    @Override
    public String toString() {
        return fullName + " (" + getRole() + ")";
    }
}
