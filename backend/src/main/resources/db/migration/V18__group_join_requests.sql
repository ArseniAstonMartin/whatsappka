-- Заявки на вступление в приватное сообщество (FR-07). Решение принимает OWNER/ADMIN сообщества;
-- публичное вступление без заявки обходится стороной (TASK-036).

CREATE TABLE group_join_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL REFERENCES groups (id),
    requester_id uuid NOT NULL REFERENCES users (id),
    status varchar(10) NOT NULL,
    decided_by uuid REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    responded_at timestamptz,
    CONSTRAINT group_join_requests_status_allowed
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

-- Одна активная заявка на пару «сообщество, заявитель».
CREATE UNIQUE INDEX group_join_requests_active_key
    ON group_join_requests (group_id, requester_id) WHERE status = 'PENDING';

-- Очередь заявок администратора читает только ожидающие по сообществу.
CREATE INDEX group_join_requests_group_idx ON group_join_requests (group_id, status);

-- Повторная заявка после отказа проверяет последнюю запись пары (cooldown 24 часа).
CREATE INDEX group_join_requests_pair_idx ON group_join_requests (group_id, requester_id, created_at DESC);
