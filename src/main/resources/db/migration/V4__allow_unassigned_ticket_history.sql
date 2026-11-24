ALTER TABLE helpdesk.ticket_histories
    ALTER COLUMN new_value DROP NOT NULL,
    ADD CONSTRAINT chk_ticket_history_new_value
        CHECK (new_value IS NOT NULL OR field_name = 'assignedTo');
