-- Additive migration. Do not execute on a live DB without backup/history review and approval.
-- Administrative removal blocks re-registration; ordinary self-withdrawal remains distinct.
ALTER TABLE member ADD COLUMN admin_withdrawn BOOLEAN NOT NULL DEFAULT FALSE;
