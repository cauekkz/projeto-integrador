CREATE INDEX idx_users_check_email_created_at
ON users (created_at)
WHERE status = 'CHECK_EMAIL';
