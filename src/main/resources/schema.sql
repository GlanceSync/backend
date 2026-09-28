-- GlanceSync schema. Applied on startup; every statement is idempotent.

-- Geography types and spatial indexes for location matching. The server must have
-- PostGIS installed, and creating the extension needs a superuser the first time.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username          VARCHAR(30)  NOT NULL,
    email             VARCHAR(254) NOT NULL,
    first_name        VARCHAR(50)  NOT NULL,
    last_name         VARCHAR(50)  NOT NULL,
    password_hash     TEXT         NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at      TIMESTAMPTZ,
    -- gender: male | female. Null until the profile is completed.
    gender            VARCHAR(16),
    -- heterosexual | homosexual | bisexual
    sexual_preference VARCHAR(16)  NOT NULL DEFAULT 'bisexual',
    biography         TEXT,
    birth_date        DATE,

    profile_picture_id UUID,
    -- Longitude/latitude on the WGS 84 globe, so distances come out in metres.
    location            GEOGRAPHY(Point, 4326),
    city                VARCHAR(100),
    -- gps | manual
    location_source     VARCHAR(8),
    location_updated_at TIMESTAMPTZ,

    -- Recomputed by FameService whenever one of its inputs changes; see there for the formula.
    fame_rating       INTEGER      NOT NULL DEFAULT 0,

    -- True once gender, birth date, biography, a profile picture, a location and at least
    -- one interest are all set. Kept up to date by UserDao.refreshProfileCompleted.
    profile_completed BOOLEAN      NOT NULL DEFAULT false
);

-- Spatial index behind "everyone within N km".
CREATE INDEX IF NOT EXISTS users_location_idx ON users USING GIST (location);

-- Usernames and emails are unique case-insensitively, but stored as the user typed them.
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_key ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_key    ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS email_verifications (
    token_hash CHAR(64)     PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    new_email  VARCHAR(254),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS email_verifications_user_id_idx ON email_verifications (user_id);

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

CREATE INDEX IF NOT EXISTS messages_conversation_idx
    ON messages (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id), created_at);

CREATE INDEX IF NOT EXISTS messages_unread_idx
    ON messages (recipient_id) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- message | call_missed | event_invited | event_accepted | event_declined | event_cancelled
    -- | user_blocked | user_unblocked | like | match | unlike | profile_view
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

CREATE INDEX IF NOT EXISTS blocks_blocked_idx ON blocks (blocked_id);

CREATE TABLE IF NOT EXISTS user_pictures (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL,
    data         BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_pictures_user_idx ON user_pictures (user_id, created_at);

-- users and user_pictures point at each other, so one of the two keys has to come after
-- both tables. Postgres has no ADD CONSTRAINT IF NOT EXISTS, hence the check.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_profile_picture_id_fkey') THEN
        ALTER TABLE users ADD CONSTRAINT users_profile_picture_id_fkey
            FOREIGN KEY (profile_picture_id) REFERENCES user_pictures (id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS interests (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(30) NOT NULL UNIQUE,
    created_by UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_interests (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    interest_id UUID        NOT NULL REFERENCES interests (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, interest_id)
);

CREATE INDEX IF NOT EXISTS user_interests_interest_idx ON user_interests (interest_id);

CREATE TABLE IF NOT EXISTS likes (
    liker_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    liked_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (liker_id, liked_id),
    CONSTRAINT likes_not_self CHECK (liker_id <> liked_id)
);

CREATE INDEX IF NOT EXISTS likes_liked_idx ON likes (liked_id, created_at DESC);

CREATE TABLE IF NOT EXISTS profile_views (
    viewer_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    first_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_viewed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (viewer_id, viewed_id),
    CONSTRAINT profile_views_not_self CHECK (viewer_id <> viewed_id)
);

CREATE INDEX IF NOT EXISTS profile_views_viewed_idx ON profile_views (viewed_id, last_viewed_at DESC);

CREATE TABLE IF NOT EXISTS reports (
    reporter_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reported_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (reporter_id, reported_id),
    CONSTRAINT reports_not_self CHECK (reporter_id <> reported_id)
);

CREATE INDEX IF NOT EXISTS reports_reported_idx ON reports (reported_id);

-- "Forgot password" links. Only the SHA-256 of the token is stored.
CREATE TABLE IF NOT EXISTS password_resets (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS password_resets_user_id_idx ON password_resets (user_id);
