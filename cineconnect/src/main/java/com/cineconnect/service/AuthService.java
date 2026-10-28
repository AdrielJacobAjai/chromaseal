package com.cineconnect.service;

import com.cineconnect.db.Database;
import com.cineconnect.exception.AuthenticationException;
import com.cineconnect.exception.DataAccessException;
import com.cineconnect.model.Admin;
import com.cineconnect.model.Customer;
import com.cineconnect.model.Role;
import com.cineconnect.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthService {
    private final Database db;

    public AuthService(Database db) {
        this.db = db;
    }

    public User register(String username, String password, String fullName) {
        return create(username, password, fullName, Role.CUSTOMER);
    }

    public User createAdmin(String username, String password, String fullName) {
        return create(username, password, fullName, Role.ADMIN);
    }

    private User create(String username, String password, String fullName, Role role) {
        if (username == null || username.isBlank() || password == null || password.length() < 4
                || fullName == null || fullName.isBlank()) {
            throw new AuthenticationException("Username, full name and a password of at least 4 characters are required");
        }
        String name = username.trim();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
                check.setString(1, name);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        throw new AuthenticationException("Username '" + name + "' is already taken");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(username,password_hash,full_name,role) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, PasswordHasher.hash(password));
                ps.setString(3, fullName.trim());
                ps.setString(4, role.name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return build(keys.getInt(1), name, fullName.trim(), role);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not register user: " + e.getMessage(), e);
        }
    }

    public User login(String username, String password) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,password_hash,full_name,role FROM users WHERE username=?")) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && password != null && PasswordHasher.matches(password, rs.getString("password_hash"))) {
                    return build(rs.getInt("id"), username.trim(), rs.getString("full_name"),
                            Role.valueOf(rs.getString("role")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not log in: " + e.getMessage(), e);
        }
        throw new AuthenticationException("Invalid username or password");
    }

    public boolean hasUsers() {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    private static User build(int id, String username, String fullName, Role role) {
        return role == Role.ADMIN ? new Admin(id, username, fullName) : new Customer(id, username, fullName);
    }
}
