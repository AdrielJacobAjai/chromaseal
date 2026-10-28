package com.cineconnect.db;

import com.cineconnect.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Singleton pattern: one place that knows how to reach the database.
 * Each call to {@link #getConnection()} returns a fresh JDBC connection, so concurrent
 * requests never share a connection.
 */
public final class Database {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/cineconnect?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";

    private static Database instance;

    private final String url;
    private final String user;
    private final String password;

    private Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = fromConfig();
        }
        return instance;
    }

    /** Replaces the shared instance; used by tests to point at an in-memory database. */
    public static synchronized Database configure(String url, String user, String password) {
        instance = new Database(url, user, password);
        return instance;
    }

    private static Database fromConfig() {
        Properties props = new Properties();
        Path file = Path.of("config", "db.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                throw new DataAccessException("Cannot read " + file, e);
            }
        }
        return new Database(
                pick("CINECONNECT_DB_URL", "db.url", props, DEFAULT_URL),
                pick("CINECONNECT_DB_USER", "db.user", props, "root"),
                pick("CINECONNECT_DB_PASSWORD", "db.password", props, ""));
    }

    private static String pick(String envName, String propName, Properties props, String fallback) {
        String env = System.getenv(envName);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return props.getProperty(propName, fallback);
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Creates the tables if they do not exist yet. */
    public void initSchema() {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new DataAccessException("schema.sql not found on classpath", null);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read schema.sql", e);
        }
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot create schema: " + e.getMessage(), e);
        }
    }
}
