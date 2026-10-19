CREATE TABLE doctors (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL UNIQUE,
 specialty VARCHAR(80) NOT NULL,
 room VARCHAR(40) NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE opd_sessions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 doctor_id BIGINT NOT NULL,
 session_date DATE NOT NULL,
 average_minutes INT NOT NULL DEFAULT 12,
 next_ticket INT NOT NULL DEFAULT 1,
 state VARCHAR(12) NOT NULL DEFAULT 'OPEN',
 FOREIGN KEY (doctor_id) REFERENCES doctors(id),
 UNIQUE KEY uq_doctor_day (doctor_id, session_date),
 CHECK (state IN ('OPEN','CLOSED')),
 CHECK (average_minutes BETWEEN 1 AND 120)
);
CREATE TABLE appointment_slots (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 session_id BIGINT NOT NULL,
 starts_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (session_id) REFERENCES opd_sessions(id),
 UNIQUE KEY uq_session_slot (session_id,starts_at),
 UNIQUE KEY uq_slot_session (id,session_id)
);
CREATE TABLE queue_entries (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 slot_id BIGINT NOT NULL,
 session_id BIGINT NOT NULL,
 patient_id BIGINT NOT NULL,
 ticket_number INT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'BOOKED',
 booked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 checked_in_at TIMESTAMP(6) NULL,
 completed_at TIMESTAMP(6) NULL,
 active_slot_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('BOOKED','WAITING','IN_CONSULTATION','COMPLETED') THEN slot_id ELSE NULL END) STORED,
 active_patient_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('BOOKED','WAITING','IN_CONSULTATION') THEN patient_id ELSE NULL END) STORED,
 serving_session_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'IN_CONSULTATION' THEN session_id ELSE NULL END) STORED,
 FOREIGN KEY (slot_id,session_id) REFERENCES appointment_slots(id,session_id),
 FOREIGN KEY (session_id) REFERENCES opd_sessions(id),
 FOREIGN KEY (patient_id) REFERENCES users(id),
 UNIQUE KEY uq_slot_active (active_slot_id),
 UNIQUE KEY uq_patient_session_active (session_id,active_patient_id),
 UNIQUE KEY uq_one_consultation (serving_session_id),
 UNIQUE KEY uq_ticket (session_id,ticket_number),
 INDEX idx_queue (session_id,status,ticket_number),
 INDEX idx_patient_history (patient_id,id),
 CHECK (status IN ('BOOKED','WAITING','IN_CONSULTATION','COMPLETED','CANCELLED','NO_SHOW'))
);
