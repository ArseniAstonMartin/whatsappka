-- Сроки хранения (PRD). Журнал аудита по-прежнему нельзя менять. Удалять его можно только одной функцией,
-- которая проверяет срок и не трогает записи, относящиеся к открытым жалобам.

CREATE OR REPLACE FUNCTION audit_logs_forbid_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    -- Удаление разрешено только внутри retention_purge_audit: она выставляет признак на время своей транзакции.
    IF TG_OP = 'DELETE' AND current_setting('whatsappka.audit_retention', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE FUNCTION retention_purge_audit(p_cutoff timestamptz) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    removed bigint;
BEGIN
    PERFORM set_config('whatsappka.audit_retention', 'on', true);
    DELETE FROM audit_logs a
    WHERE a.occurred_at < p_cutoff
      AND NOT (a.target_type = 'report' AND a.target_id IN (
            SELECT r.id FROM reports r WHERE r.status IN ('OPEN', 'IN_REVIEW')));
    GET DIAGNOSTICS removed = ROW_COUNT;
    PERFORM set_config('whatsappka.audit_retention', 'off', true);
    RETURN removed;
END;
$$;

REVOKE ALL ON FUNCTION retention_purge_audit(timestamptz) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'whatsappka_app') THEN
        GRANT EXECUTE ON FUNCTION retention_purge_audit(timestamptz) TO whatsappka_app;
    END IF;
END $$;

-- Индексы под очистку: поиск по времени без полного просмотра.
CREATE INDEX notifications_created_at_idx ON notifications (created_at);
CREATE INDEX conversation_events_occurred_at_idx ON conversation_events (occurred_at);
CREATE INDEX idempotency_records_expires_at_idx ON idempotency_records (expires_at);
CREATE INDEX auth_sessions_absolute_expires_at_idx ON auth_sessions (absolute_expires_at);
CREATE INDEX account_tokens_expires_at_idx ON account_tokens (expires_at);
CREATE INDEX media_assets_deleted_at_idx ON media_assets (deleted_at) WHERE deleted_at IS NOT NULL;
