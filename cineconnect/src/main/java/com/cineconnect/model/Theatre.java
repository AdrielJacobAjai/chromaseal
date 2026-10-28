package com.cineconnect.model;

public class Theatre {
    private final int id;
    private final String name;
    private final String city;

    public Theatre(int id, String name, String city) {
        this.id = id;
        this.name = name;
        this.city = city;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }

    @Override
    public String toString() { return name + ", " + city; }
}
