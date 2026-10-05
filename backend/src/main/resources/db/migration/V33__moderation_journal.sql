-- Журнал решений модерации неизменяем: записи только добавляются. Изменение и удаление отклоняются самой базой,
-- даже если приложение или оператор попытаются их выполнить.
CREATE FUNCTION moderation_actions_forbid_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'moderation_actions is append-only';
END;
$$;

CREATE TRIGGER moderation_actions_immutable
    BEFORE UPDATE OR DELETE ON moderation_actions
    FOR EACH ROW EXECUTE FUNCTION moderation_actions_forbid_change();
