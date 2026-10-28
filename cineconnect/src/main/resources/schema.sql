CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS movies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    genre VARCHAR(50) NOT NULL,
    duration_min INT NOT NULL,
    rating VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS theatres (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS shows (
    id INT AUTO_INCREMENT PRIMARY KEY,
    movie_id INT NOT NULL,
    theatre_id INT NOT NULL,
    screen VARCHAR(30) NOT NULL,
    start_time BIGINT NOT NULL,
    row_count INT NOT NULL,
    col_count INT NOT NULL,
    base_price DOUBLE NOT NULL,
    FOREIGN KEY (movie_id) REFERENCES movies(id),
    FOREIGN KEY (theatre_id) REFERENCES theatres(id)
);

CREATE TABLE IF NOT EXISTS bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    show_id INT NOT NULL,
    total DOUBLE NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (show_id) REFERENCES shows(id)
);

CREATE TABLE IF NOT EXISTS show_seats (
    show_id INT NOT NULL,
    seat_label VARCHAR(10) NOT NULL,
    row_idx INT NOT NULL,
    col_idx INT NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    price DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_by INT NULL,
    hold_expires BIGINT NOT NULL DEFAULT 0,
    booking_id INT NULL,
    PRIMARY KEY (show_id, seat_label),
    FOREIGN KEY (show_id) REFERENCES shows(id)
);

CREATE TABLE IF NOT EXISTS booking_seats (
    booking_id INT NOT NULL,
    seat_label VARCHAR(10) NOT NULL,
    price DOUBLE NOT NULL,
    PRIMARY KEY (booking_id, seat_label),
    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);
