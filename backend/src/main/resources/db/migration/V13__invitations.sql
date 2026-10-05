-- Приглашения в групповой чат (FR-06). Приглашение принимается только получателем.
-- Одно действующее приглашение на пару «чат, получатель»; история решений сохраняется.

CREATE TABLE conversation_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    inviter_id uuid NOT NULL REFERENCES users (id),
    invitee_id uuid NOT NULL REFERENCES users (id),
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    responded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_invitations_status_allowed
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT conversation_invitations_not_self CHECK (inviter_id <> invitee_id),
    CONSTRAINT conversation_invitations_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX conversation_invitations_active_key
    ON conversation_invitations (conversation_id, invitee_id) WHERE status = 'PENDING';
CREATE INDEX conversation_invitations_invitee_idx
    ON conversation_invitations (invitee_id) WHERE status = 'PENDING';
