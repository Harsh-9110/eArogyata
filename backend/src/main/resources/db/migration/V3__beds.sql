CREATE TABLE wards (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(80) NOT NULL UNIQUE,
 floor VARCHAR(20) NOT NULL
);
CREATE TABLE beds (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 ward_id BIGINT NOT NULL,
 label VARCHAR(24) NOT NULL,
 operational_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
 FOREIGN KEY (ward_id) REFERENCES wards(id),
 UNIQUE KEY uq_ward_bed (ward_id,label),
 CHECK (operational_status IN ('AVAILABLE','CLEANING','OUT_OF_SERVICE'))
);
CREATE TABLE bed_reservations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 bed_id BIGINT NOT NULL,
 patient_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
 reserved_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 admitted_at TIMESTAMP(6) NULL,
 ended_at TIMESTAMP(6) NULL,
 active_bed_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('RESERVED','ADMITTED') THEN bed_id ELSE NULL END) STORED,
 active_patient_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('RESERVED','ADMITTED') THEN patient_id ELSE NULL END) STORED,
 FOREIGN KEY (bed_id) REFERENCES beds(id),
 FOREIGN KEY (patient_id) REFERENCES users(id),
 UNIQUE KEY uq_active_bed (active_bed_id),
 UNIQUE KEY uq_active_patient_bed (active_patient_id),
 INDEX idx_reservation_patient (patient_id,id),
 CHECK (status IN ('RESERVED','ADMITTED','DISCHARGED','CANCELLED'))
);
INSERT INTO wards (name,floor) VALUES ('General Medicine','Level 1'),('Observation','Level 2'),('Critical Care','Level 3');
INSERT INTO beds (ward_id,label) VALUES (1,'G-101'),(1,'G-102'),(1,'G-103'),(1,'G-104'),(1,'G-105'),(1,'G-106'),(2,'O-201'),(2,'O-202'),(2,'O-203'),(2,'O-204'),(3,'C-301'),(3,'C-302');
