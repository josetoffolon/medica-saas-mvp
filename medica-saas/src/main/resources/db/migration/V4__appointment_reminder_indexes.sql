-- V4: índices para el AppointmentReminderJob (optimización)
-- Las queries de recordatorios filtran por status + reminder_flag + rango de
-- scheduled_at SIN tenant_id, así que los índices existentes (todos liderados
-- por tenant_id) no aplican → full scan cada 10 min. Estos índices alinean
-- igualdades primero y el rango al final.

CREATE INDEX idx_appt_reminder_24h
    ON appointment (status, reminder_24h_sent, scheduled_at);

CREATE INDEX idx_appt_reminder_2h
    ON appointment (status, reminder_2h_sent, scheduled_at);