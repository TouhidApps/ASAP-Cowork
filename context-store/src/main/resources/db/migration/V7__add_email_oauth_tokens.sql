ALTER TABLE email_accounts ADD COLUMN access_token TEXT;
ALTER TABLE email_accounts ADD COLUMN refresh_token TEXT;
ALTER TABLE email_accounts ADD COLUMN token_expires_at INTEGER;
