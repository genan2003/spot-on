-- Runs once on first start of the postgres container (empty volume).
-- One database per service; services never read each other's databases.
CREATE DATABASE parking_db;
CREATE DATABASE reservation_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
