CREATE TABLE app_user (
  id            UUID PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER','FLEET_ADMIN')),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE vehicle (
  id           UUID PRIMARY KEY,
  vin          CHAR(17)     NOT NULL UNIQUE,
  owner_id     UUID         NOT NULL REFERENCES app_user(id),
  model        VARCHAR(100) NOT NULL,
  time_zone    VARCHAR(50)  NOT NULL DEFAULT 'Europe/Dublin',
  api_key_hash VARCHAR(100) NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE device (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES app_user(id),
  public_key    TEXT NOT NULL,
  label         VARCHAR(100),
  registered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE SEQUENCE revocation_epoch_seq;

CREATE TABLE digital_key (
  id               UUID PRIMARY KEY,
  vehicle_id       UUID NOT NULL REFERENCES vehicle(id),
  holder_id        UUID NOT NULL REFERENCES app_user(id),
  device_id        UUID NOT NULL REFERENCES device(id),
  issued_by        UUID NOT NULL REFERENCES app_user(id),
  parent_key_id    UUID REFERENCES digital_key(id),
  depth            INT  NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 3),
  status           VARCHAR(20) NOT NULL
                   CHECK (status IN ('ACTIVE','SUSPENDED','EXPIRED','REVOKED')),
  not_before       TIMESTAMPTZ NOT NULL,
  not_after        TIMESTAMPTZ NOT NULL,
  policy           JSONB NOT NULL DEFAULT '[]',
  max_speed_kmh    INT CHECK (max_speed_kmh BETWEEN 10 AND 250),
  revoked_at       TIMESTAMPTZ,
  revocation_epoch BIGINT,
  version          BIGINT NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (not_after > not_before)
);
CREATE INDEX idx_key_vehicle_status ON digital_key (vehicle_id, status);
CREATE INDEX idx_key_holder         ON digital_key (holder_id);
CREATE INDEX idx_key_parent         ON digital_key (parent_key_id);
CREATE INDEX idx_key_active_expiry  ON digital_key (not_after) WHERE status = 'ACTIVE';
CREATE INDEX idx_key_rev_epoch      ON digital_key (revocation_epoch)
                                    WHERE revocation_epoch IS NOT NULL;

CREATE TABLE key_permission (
  key_id     UUID NOT NULL REFERENCES digital_key(id) ON DELETE CASCADE,
  permission VARCHAR(20) NOT NULL
             CHECK (permission IN ('UNLOCK','LOCK','OPEN_BOOT','START_ENGINE','SHARE')),
  PRIMARY KEY (key_id, permission)
);

CREATE TABLE access_nonce (
  vehicle_id UUID        NOT NULL REFERENCES vehicle(id),
  nonce      VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (vehicle_id, nonce)
);
CREATE INDEX idx_nonce_expiry ON access_nonce (expires_at);

CREATE TABLE audit_event (
  id          BIGSERIAL PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  vehicle_id  UUID,
  key_id      UUID,
  actor_id    UUID,
  event_type  VARCHAR(40) NOT NULL,
  command     VARCHAR(20),
  reason      VARCHAR(300),
  trace       JSONB,
  lat         DOUBLE PRECISION,
  lon         DOUBLE PRECISION
);
CREATE INDEX idx_audit_vehicle_time ON audit_event (vehicle_id, occurred_at DESC);
CREATE INDEX idx_audit_key_type     ON audit_event (key_id, event_type, occurred_at);

CREATE FUNCTION forbid_audit_change() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_append_only
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION forbid_audit_change();

CREATE TABLE alert (
  id           UUID PRIMARY KEY,
  vehicle_id   UUID NOT NULL REFERENCES vehicle(id),
  key_id       UUID,
  alert_type   VARCHAR(40) NOT NULL,
  details      TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  acknowledged BOOLEAN NOT NULL DEFAULT false
);
