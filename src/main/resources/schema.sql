-- GlanceSync schema. Applied on startup; every statement is idempotent.

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30) NOT NULL,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ
);

-- Usernames are unique case-insensitively, but stored with the casing the user chose.
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_key ON users (LOWER(username));

CREATE TABLE IF NOT EXISTS sessions (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS sessions_user_id_idx    ON sessions (user_id);
CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions (expires_at);

CREATE TABLE IF NOT EXISTS messages (
    id           BIGSERIAL PRIMARY KEY,
    sender_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
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
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- message | call_missed | event_invited | event_accepted | event_declined | event_cancelled
    type       VARCHAR(40) NOT NULL,
    actor_id   BIGINT      REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS notifications_unread_idx
    ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS events (
    id           BIGSERIAL PRIMARY KEY,
    organizer_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invitee_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
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
    id               BIGSERIAL PRIMARY KEY,
    caller_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    callee_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
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
    blocker_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- Looked up in the other direction too, to answer "has this person blocked me".
CREATE INDEX IF NOT EXISTS blocks_blocked_idx ON blocks (blocked_id);
