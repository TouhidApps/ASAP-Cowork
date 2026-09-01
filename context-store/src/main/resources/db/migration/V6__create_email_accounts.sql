CREATE TABLE email_accounts (
    id TEXT PRIMARY KEY,
    provider TEXT NOT NULL,
    email_address TEXT NOT NULL,
    display_label TEXT,
    is_default INTEGER NOT NULL DEFAULT 0,
    last_seen_message_id TEXT,
    last_seen_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX email_accounts_provider_address ON email_accounts (provider, email_address);
