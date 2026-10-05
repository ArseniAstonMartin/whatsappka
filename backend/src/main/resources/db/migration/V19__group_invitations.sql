-- Приглашения в сообщество (FR-07, FR-09). Принимает только получатель, одна активная запись на пару.

CREATE TABLE group_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL REFERENCES groups (id),
    inviter_id uuid NOT NULL REFERENCES users (id),
    invitee_id uuid NOT NULL REFERENCES users (id),
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    responded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT group_invitations_status_allowed
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT group_invitations_not_self CHECK (inviter_id <> invitee_id),
    CONSTRAINT group_invitations_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX group_invitations_active_key
    ON group_invitations (group_id, invitee_id) WHERE status = 'PENDING';
CREATE INDEX group_invitations_invitee_idx
    ON group_invitations (invitee_id) WHERE status = 'PENDING';
