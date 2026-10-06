-- Журнал аудита только дописывается: изменить или удалить запись нельзя даже из базы.
-- Сроки хранения (TASK-086) будут выполняться отдельной ограниченной функцией, а не обычным DELETE.
CREATE FUNCTION audit_logs_forbid_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_forbid_change();
