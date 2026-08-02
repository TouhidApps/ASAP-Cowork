CREATE TABLE chat_message_attachments (
    id TEXT PRIMARY KEY,
    message_id TEXT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_chat_message_attachments_message_id ON chat_message_attachments(message_id);
