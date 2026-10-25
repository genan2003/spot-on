-- Demo data with fixed IDs, so the demo runner and manual REST calls can refer to them.

INSERT INTO users (id, user_type, name, email, phone, preferred_channel, preferred_payment_method) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',  'Ioana (admin)', 'ioana@spot-on.example', NULL, NULL, NULL),
    ('00000000-0000-0000-0000-000000000011', 'DRIVER', 'Ana',    'ana@example.com',    '+40 700 000 011', 'EMAIL', 'CARD'),
    ('00000000-0000-0000-0000-000000000012', 'DRIVER', 'Bogdan', 'bogdan@example.com', '+40 700 000 012', 'SMS',   'WALLET');

INSERT INTO parking_location (id, admin_id, name, address, latitude, longitude, strategy_type, hourly_rate) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001',
     'Central Garage', 'Strada Memorandumului 21', 46.7701, 23.5878, 'HOURLY', 6.00);

INSERT INTO parking_spot (id, location_id, code, level, zone, type, ev_charging, accessible, status, version) VALUES
    ('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000101', 'A1', 0, 'A', 'STANDARD', FALSE, TRUE,  'AVAILABLE',      0),
    ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000101', 'A2', 0, 'A', 'STANDARD', FALSE, FALSE, 'AVAILABLE',      0),
    ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000101', 'A3', 0, 'A', 'STANDARD', FALSE, FALSE, 'AVAILABLE',      0),
    ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000101', 'A4', 0, 'A', 'LARGE',    FALSE, FALSE, 'AVAILABLE',      0),
    ('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000101', 'B1', 0, 'B', 'STANDARD', TRUE,  FALSE, 'AVAILABLE',      0),
    ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000101', 'B2', 0, 'B', 'STANDARD', TRUE,  FALSE, 'OUT_OF_SERVICE', 0);
