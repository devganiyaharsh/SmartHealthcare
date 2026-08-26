-- SmartHealthcare Database Schema (auto-created by SQLAlchemy)
-- This file is for reference only. The app creates tables automatically.

CREATE TABLE IF NOT EXISTS user (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(120) UNIQUE NOT NULL,
    password    VARCHAR(200) NOT NULL,
    age         INTEGER,
    gender      VARCHAR(10),
    phone       VARCHAR(15),
    blood_group VARCHAR(5),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prediction (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER REFERENCES user(id),
    disease     VARCHAR(100),
    confidence  FLOAT,
    symptoms    VARCHAR(500),
    severity    VARCHAR(20),
    specialist  VARCHAR(100),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS appointment (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER REFERENCES user(id),
    doctor_name VARCHAR(100),
    department  VARCHAR(100),
    date        VARCHAR(20),
    time        VARCHAR(20),
    reason      VARCHAR(300),
    status      VARCHAR(20) DEFAULT 'Scheduled',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);
