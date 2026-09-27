-- GlanceSync schema. Applied on startup; every statement is idempotent.

CREATE TABLE IF NOT EXISTS users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username          VARCHAR(30)  NOT NULL,
    email             VARCHAR(254) NOT NULL,
    first_name        VARCHAR(50)  NOT NULL,
    last_name         VARCHAR(50)  NOT NULL,
    password_hash     TEXT         NOT NULL,
    -- Null until the link in the verification email is followed; signing in needs it.
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at      TIMESTAMPTZ
);

-- Usernames and emails are unique case-insensitively, but stored as the user typed them.
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_key ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_key    ON users (LOWER(email));

-- One-time links sent by email. Only the SHA-256 of the token is stored.
CREATE TABLE IF NOT EXISTS email_verifications (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS email_verifications_user_id_idx ON email_verifications (user_id);

-- One row per signed-in device. The refresh token itself lives in an HttpOnly cookie;
-- only its SHA-256 is stored, and each use swaps it for a new one.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens (user_id);

CREATE TABLE IF NOT EXISTS messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,
    CONSTRAINT messages_not_self CHECK (sender_id <> recipient_id)
);

-- A conversation is the unordered pair of participants, so normalising with
-- LEAST/GREATEST lets one index serve both directions of the same thread.
CREATE INDEX IF NOT EXISTS messages_conversation_idx
    ON messages (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id), created_at);

-- Partial index: unread counts are read constantly, read messages never matter here.
CREATE INDEX IF NOT EXISTS messages_unread_idx
    ON messages (recipient_id) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- message | call_missed | event_invited | event_accepted | event_declined | event_cancelled
    type       VARCHAR(40) NOT NULL,
    actor_id   UUID        REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS notifications_unread_idx
    ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invitee_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        TEXT        NOT NULL,
    location     TEXT,
    starts_at    TIMESTAMPTZ NOT NULL,
    -- pending | accepted | declined | cancelled
    status       VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT events_not_self CHECK (organizer_id <> invitee_id)
);

CREATE INDEX IF NOT EXISTS events_organizer_idx ON events (organizer_id, starts_at);
CREATE INDEX IF NOT EXISTS events_invitee_idx   ON events (invitee_id, starts_at);

CREATE TABLE IF NOT EXISTS calls (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    callee_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- audio | video
    mode             VARCHAR(8)  NOT NULL,
    -- answered | missed | declined
    status           VARCHAR(16) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    answered_at      TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_seconds INTEGER,
    CONSTRAINT calls_not_self CHECK (caller_id <> callee_id)
);

-- Same normalised pair as messages, so one conversation reads from both tables cheaply.
CREATE INDEX IF NOT EXISTS calls_pair_idx
    ON calls (LEAST(caller_id, callee_id), GREATEST(caller_id, callee_id), started_at);

CREATE TABLE IF NOT EXISTS blocks (
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- Looked up in the other direction too, to answer "has this person blocked me".
CREATE INDEX IF NOT EXISTS blocks_blocked_idx ON blocks (blocked_id);

-- Profile photos. Kept out of `users` so listing people never drags image bytes along;
-- `users.avatar_updated_at` is enough to know one exists and to version its URL.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS user_avatars (
    user_id      UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
