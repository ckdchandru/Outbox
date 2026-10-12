CREATE TABLE transfers (
    id          UUID           PRIMARY KEY,
    amount      NUMERIC(19, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT transfers_status_check CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT transfers_amount_check CHECK (amount > 0)
);

CREATE SEQUENCE outbox_seq;

CREATE TABLE outbox_events (
    id            UUID          PRIMARY KEY,
    entity_id     UUID          NOT NULL,
    event_type    VARCHAR(100)  NOT NULL,
    payload       JSONB         NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    sequence_no   BIGINT        NOT NULL DEFAULT nextval('outbox_seq'),
    attempts      INT           NOT NULL DEFAULT 0,
    max_attempts  INT           NOT NULL,
    available_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    locked_by     VARCHAR(100),
    locked_at     TIMESTAMPTZ,
    claim_token   UUID,
    last_error    TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,

    CONSTRAINT outbox_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT outbox_attempts_check
        CHECK (attempts <= max_attempts),
    -- token only while processing
    CONSTRAINT outbox_token_check
        CHECK ((status = 'PROCESSING') = (claim_token IS NOT NULL)),
    CONSTRAINT outbox_published_check
        CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT outbox_entity_seq_unique
        UNIQUE (entity_id, sequence_no)
);

ALTER SEQUENCE outbox_seq OWNED BY outbox_events.sequence_no;

-- ordering check
CREATE INDEX idx_outbox_entity_status_seq ON outbox_events (entity_id, status, sequence_no);

-- poller
CREATE INDEX idx_outbox_status_available ON outbox_events (status, available_at);

-- reclaimer scan
CREATE INDEX idx_outbox_status_locked ON outbox_events (status, locked_at);

-- dead list
CREATE INDEX idx_outbox_dead_created ON outbox_events (created_at DESC) WHERE status = 'DEAD';
